import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Serveur HTTP minimaliste basé sur HttpServer (inclus dans le JDK).
 * Deux routes :
 *   - POST /check  : validation d’un mot de passe via CrackLib
 *   - GET  /health : vérification de l’état du serveur
 */
public class CrackLibServer {

    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/check", new CheckHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(null); // exécuteur par défaut
        server.start();
        System.out.println("Serveur démarré sur le port " + PORT);
    }

    // Handler POST /check — utilise CrackLib pour valider un mot de passe
    static class CheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Méthode non autorisée\"}");
                return;
            }

            String password = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            CrackLibResult result = runCrackLib(password);

            String json = String.format("{\"accepted\":%b,\"detail\":\"%s\"}",
                    result.accepted(), escapeJson(result.detail()));
            sendJson(exchange, 200, json);
        }

        private CrackLibResult runCrackLib(String password) {
            try {
                Process process = new ProcessBuilder("cracklib-check").redirectErrorStream(true).start();
                try (OutputStream os = process.getOutputStream()) {
                    os.write((password + "\n").getBytes(StandardCharsets.UTF_8));
                }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                process.waitFor();

                if (output.contains(": ")) {
                    String verdict = output.substring(output.indexOf(": ") + 2).trim();
                    return new CrackLibResult("OK".equalsIgnoreCase(verdict), verdict);
                }
                return new CrackLibResult(false, "Réponse inattendue : " + output);
            } catch (Exception e) {
                return new CrackLibResult(false, "Erreur : " + e.getMessage());
            }
        }

        private String escapeJson(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private void sendJson(HttpExchange ex, int code, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    // Handler GET /health — simple ping
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"OK\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

    // Résultat CrackLib (Java 21 record)
    record CrackLibResult(boolean accepted, String detail) {}
}
