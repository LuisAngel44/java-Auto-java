package report;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class DiagnosticoMSP {

    public static void main(String[] args) {
        String jiraUrl = "";
        String email = "";
        String token = "";

        String auth = email + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        // Agregamos ?fields=key,summary,status para forzar a Jira a enviarlos
        String searchUrl = jiraUrl + "/rest/api/3/search/jql?fields=summary,status,assignee";
        String jsonPayload = "{\"jql\": \"project = 'MSP' ORDER BY created DESC\", \"maxResults\": 5}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                JsonNode issues = root.path("issues");

                // --- BLOQUE DE DIAGNÓSTICO ---
                if (!issues.isEmpty()) {
                    System.out.println("Estructura real del primer ticket:");
                    // Esto imprimirá el JSON real para que veamos por qué no captura los valores
                    System.out.println(issues.get(0).toPrettyString());
                }
                // -----------------------------

                System.out.println("\n=== LISTADO REPARADO ===");
                for (JsonNode issue : issues) {
                    String key = issue.path("key").asText("No Key");
                    String summary = issue.path("fields").path("summary").asText("No Summary");
                    String status = issue.path("fields").path("status").path("name").asText("No Status");

                    System.out.println(key + " | " + status + " | " + summary);
                }
            } else {
                System.out.println("Error " + response.statusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}