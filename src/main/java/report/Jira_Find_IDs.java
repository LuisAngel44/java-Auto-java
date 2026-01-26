package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Jira_Find_IDs {

    public static void main(String[] args) {
        // --- TUS DATOS ---
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String auth = email + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        ObjectMapper mapper = new ObjectMapper();

        try {
            HttpClient client = HttpClient.newHttpClient();
            // Este endpoint trae TODOS los campos que existen en tu Jira
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/field"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode fields = mapper.readTree(response.body());
                
                System.out.println("=== BUSCANDO TUS CAMPOS ===");
                
                for (JsonNode field : fields) {
                    String name = field.path("name").asText();
                    String id = field.path("id").asText();
                    
                    // Buscamos palabras clave de tu imagen
                    if (name.contains("solución") || name.contains("Root") || name.contains("incidente")) {
                        System.out.println("NOMBRE: " + name + "  --->  ID: " + id);
                    }
                }
                System.out.println("===========================");
                
            } else {
                System.out.println("Error: " + response.statusCode());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}