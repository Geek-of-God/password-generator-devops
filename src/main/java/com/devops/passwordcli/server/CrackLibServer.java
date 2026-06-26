package com.devops.passwordcli.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Serveur HTTP embarqué dans le conteneur Docker, entièrement écrit en Java.
 *
 * Pourquoi HttpServer de la JDK plutôt que Spring Boot ou Jetty ?
 * Ce projet doit rester 100% Java sans framework externe. HttpServer est inclus
 * dans le JDK depuis Java 6 et suffit largement pour une API à deux routes.
 * Spring Boot ajouterait ~50 Mo de dépendances pour le même résultat fonctionnel.
 *
 * Ce serveur joue le rôle de "glue" entre le monde Java (appelé depuis le client)
 * et l'outil natif CrackLib (exécuté via ProcessBuilder). C'est le pattern Adapter.
 */
public class CrackLibServer {

    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/check",  new CheckHandler());
        server.createContext("/health", new HealthHandler());
        // setExecutor(null) = utiliser le thread interne du serveur.
        // Acceptable pour un usage mono-utilisateur en local/CI.
        // En production, on passerait un Executors.newFixedThreadPool(N).
        server.setExecutor(null);
        server.start();
        System.out.println("[CrackLibServer] Démarré sur le port " + PORT);
    }

    // =========================================================================
    // Handler POST /check — validation via CrackLib
    // =========================================================================

    static class CheckHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String body     = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String password = extractField(body, "password");

            if (password == null || password.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"Champ password manquant\"}");
                return;
            }

            CrackLibResult result = runCrackLib(password);
            String score          = computeScore(password, result.accepted());

            String json = String.format(
                "{\"score\":\"%s\",\"accepted\":%b,\"detail\":\"%s\"}",
                score, result.accepted(), escapeJson(result.detail())
            );
            sendJson(exchange, 200, json);
        }

        /**
         * Délègue la vérification à l'exécutable natif cracklib-check via ProcessBuilder.
         *
         * On préfère ProcessBuilder à Runtime.exec() car il offre un contrôle fin
         * sur les flux (stdin/stdout/stderr séparés) et évite les problèmes de
         * splitting d'arguments avec les espaces sous Windows.
         *
         * cracklib-check attend le mot de passe sur stdin et répond sur stdout :
         *   "motdepasse: OK"              → accepté par les règles CrackLib
         *   "motdepasse: it is too short" → rejeté, avec la raison après ": "
         */
        private CrackLibResult runCrackLib(String password) {
            try {
                ProcessBuilder pb = new ProcessBuilder("cracklib-check");
                // On fusionne stderr dans stdout pour capturer les erreurs de CrackLib
                // (ex: dictionnaire manquant) sans ouvrir un second flux de lecture.
                pb.redirectErrorStream(true);

                Process process = pb.start();
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write((password + "\n").getBytes(StandardCharsets.UTF_8));
                }
                // readAllBytes() bloque jusqu'à la fin du processus, ce qui est
                // correct ici car cracklib-check est quasi-instantané.
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                process.waitFor();

                if (output.contains(": ")) {
                    String verdict = output.substring(output.indexOf(": ") + 2).trim();
                    boolean accepted = "OK".equalsIgnoreCase(verdict);
                    return new CrackLibResult(accepted, accepted ? "" : verdict);
                }
                return new CrackLibResult(false, "Réponse inattendue : " + output);

            } catch (Exception e) {
                return new CrackLibResult(false, "Erreur ProcessBuilder : " + e.getMessage());
            }
        }

        /**
         * Croise le verdict CrackLib (qualitatif) avec la longueur (quantitatif)
         * pour produire un score à cinq niveaux.
         *
         * CrackLib seul ne distingue pas "Moyen" de "Très fort" : il dit juste
         * "OK" ou "non". La longueur affine le niveau une fois le mot de passe accepté,
         * car elle est le meilleur indicateur d'entropie brute pour un humain.
         */
        private String computeScore(String password, boolean accepted) {
            int length = password.length();
            if (!accepted) return length < 8 ? "Très faible" : "Faible";
            if (length >= 14) return "Très fort";
            if (length >= 10) return "Fort";
            return "Moyen";
        }

        private String extractField(String json, String field) {
            int start = json.indexOf("\"" + field + "\"");
            if (start == -1) return null;
            int colon  = json.indexOf(':', start);
            int quote1 = json.indexOf('"', colon + 1);
            int quote2 = json.indexOf('"', quote1 + 1);
            if (quote1 == -1 || quote2 == -1) return null;
            return json.substring(quote1 + 1, quote2);
        }

        private String escapeJson(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private void sendJson(HttpExchange exchange, int code, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // =========================================================================
    // Handler GET /health — utilisé par le HEALTHCHECK Docker
    // =========================================================================

    static class HealthHandler implements HttpHandler {
        /**
         * Route de santé pour que Docker sache quand le serveur est prêt.
         * Sans ce healthcheck, docker-compose up --wait ne saurait pas attendre
         * que le serveur soit opérationnel avant que le client Java n'envoie des requêtes.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Record Java 16+ : structure de données immuable pour transporter
     * le résultat de CrackLib. Remplace un POJO classique avec constructeur,
     * getters et equals/hashCode — le compilateur les génère automatiquement.
     */
    record CrackLibResult(boolean accepted, String detail) {}
}