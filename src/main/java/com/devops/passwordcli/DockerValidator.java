package com.devops.passwordcli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Client HTTP qui délègue l'évaluation du mot de passe au conteneur Docker.
 *
 * Pourquoi externaliser la validation dans Docker plutôt que d'appeler CrackLib
 * directement depuis Java ?
 *
 *  1. CrackLib est une bibliothèque C native : la lier depuis Java nécessiterait
 *     JNI, ce qui brise la portabilité (recompilation par OS/architecture).
 *
 *  2. Docker garantit un environnement identique en dev, CI et prod — principe
 *     fondamental du DevOps "infrastructure as code".
 *
 *  3. Le conteneur peut être remplacé par un autre outil (Zxcvbn, un LLM...)
 *     sans modifier une seule ligne de ce client, tant que le contrat HTTP/JSON
 *     est respecté. C'est le principe ouvert/fermé (SOLID).
 */
public class DockerValidator {

    // On lit l'URL depuis l'environnement pour permettre la surcharge en CI/CD
    // sans recompilation : CRACKLIB_URL=http://ci-server:5000/check mvn test
    private final String apiUrl;

    // 3 secondes : assez pour une requête locale, assez court pour ne pas
    // bloquer l'utilisateur si le conteneur est absent ou planté.
    private static final int TIMEOUT_MS = 3000;

    public DockerValidator() {
        this.apiUrl = System.getenv().getOrDefault("CRACKLIB_URL", "http://localhost:5000/check");
    }

    /**
     * Envoie le mot de passe au conteneur et retourne le verdict sous forme de texte.
     *
     * Dégradation gracieuse : en cas d'erreur réseau, on retourne un message
     * explicatif plutôt que de propager une exception. Le scoring interne Java
     * reste affiché — la validation Docker est un enrichissement, pas un prérequis.
     */
    public String validate(String password) {
        try {
            var url  = URI.create(apiUrl).toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            // Sérialisation JSON manuelle : évite d'introduire une dépendance
            // (Jackson, Gson) pour un unique champ. L'échappement est crucial :
            // un mot de passe contenant " ou \ briserait le JSON sans cette étape.
            String body = "{\"password\":\"" + escapeJson(password) + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                return "Erreur HTTP " + conn.getResponseCode() + " du conteneur";
            }

            var sb = new StringBuilder();
            try (var br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            return parseScore(sb.toString());

        } catch (java.net.ConnectException e) {
            // ConnectException est séparée des autres exceptions car c'est le cas
            // le plus fréquent (conteneur non démarré) et mérite un message d'action.
            return "[Docker non disponible — lancez : docker-compose up -d]";
        } catch (Exception e) {
            return "[Erreur : " + e.getMessage() + "]";
        }
    }

    /**
     * Extrait le champ "score" et optionnellement "detail" du JSON retourné.
     *
     * On parse manuellement plutôt qu'avec une lib pour rester sans dépendance.
     * Format attendu : {"score":"Fort","accepted":true,"detail":"it is too short"}
     *
     * Si le format change côté serveur, cette méthode est le seul endroit à adapter.
     */
    private String parseScore(String json) {
        String score = extractField(json, "score");
        if (score == null) return "Réponse inattendue : " + json;

        String detail = extractField(json, "detail");
        // On n'affiche le détail que s'il est non vide (CrackLib ne renvoie
        // de détail que pour les mots de passe rejetés).
        if (detail != null && !detail.isBlank()) {
            return score + " (" + detail + ")";
        }
        return score;
    }

    /** Extrait la valeur d'un champ JSON de type string. Retourne null si absent. */
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
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}