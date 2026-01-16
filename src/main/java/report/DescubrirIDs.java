package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class DescubrirIDs {
    public static void main(String[] args) {
        // Cargar credenciales
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String encodedAuth = Base64.getEncoder().encodeToString(
                (dotenv.get("JIRA_EMAIL") + ":" + dotenv.get("JIRA_TOKEN")).getBytes());

        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // Esta URL pide la definición de TODOS los campos
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/field")) 
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            System.out.println("⏳ Consultando a Jira... espera un momento...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode fields = mapper.readTree(response.body());

                System.out.println("\n==========================================");
                System.out.println(" LISTA DE CAMPOS (Copia el ID que necesites)");
                System.out.println("==========================================\n");

                // Filtramos para mostrar solo los custom fields o los importantes
                for (JsonNode field : fields) {
                    String name = field.get("name").asText();
                    String id = field.get("id").asText();
                    
                    // Solo imprimimos si es un customfield o suena importante
                    // Puedes quitar el 'if' si quieres ver ABSOLUTAMENTE TODO
                    if (id.startsWith("customfield") || name.contains("IE") || name.contains("Modular")) {
                        System.out.printf("NOMBRE: %-40s | ID: %s%n", name, id);
                    }
                }
            } else {
                System.out.println("Error: " + response.statusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}