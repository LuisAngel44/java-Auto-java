package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Jira_GetTypes {

    public static void main(String[] args) {
        
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim(); 
        String auth = email + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        ObjectMapper mapper = new ObjectMapper();

        try {
            HttpClient client = HttpClient.newHttpClient();
            // Consultamos la información del proyecto MSP para ver sus tipos de issue
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/project/MSP"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                JsonNode issueTypes = root.path("issueTypes");

                System.out.println("\n=== TIPOS DE TICKET DISPONIBLES EN 'MSP' ===");
                System.out.println("(Copia uno de estos nombres EXACTAMENTE en tu código de crear)\n");
                
                if (issueTypes.isArray()) {
                    for (JsonNode type : issueTypes) {
                        String name = type.path("name").asText();
                        String id = type.path("id").asText();
                        boolean isSubtask = type.path("subtask").asBoolean();
                        
                        // Filtramos para que no te salgan sub-tareas que a veces confunden
                        if (!isSubtask) {
                            System.out.println("NOMBRE: " + name + "  (ID: " + id + ")");
                        }
                    }
                }
                System.out.println("============================================");
                
            } else {
                System.out.println("Error " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}