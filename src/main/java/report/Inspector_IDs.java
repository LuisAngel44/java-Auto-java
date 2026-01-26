package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Inspector_IDs {

    private static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String email = dotenv.get("JIRA_EMAIL");
        String token = dotenv.get("JIRA_TOKEN");
        String encodedAuth = Base64.getEncoder().encodeToString((email.trim() + ":" + token.trim()).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        System.out.println("🕵️‍♂️ ESCANEANDO IDs DEL 1 AL 20...\n");
        System.out.printf("%-5s | %-30s | %-10s%n", "ID", "NOMBRE DEL TIPO", "CANTIDAD");
        System.out.println("-------------------------------------------------------");

        // Escaneamos del ID 1 al 20 (Puedes aumentar el número si crees que hay más)
        for (int id = 1; id <= 20; id++) {
            try {
                // Pedimos solo 1 resultado para que sea ultra rápido
                String url = "https://api.atlassian.com/jsm/assets/workspace/" + WORKSPACE_ID + 
                             "/v1/object/aql?page=1&resultPerPage=1";

                ObjectNode payload = mapper.createObjectNode();
                payload.put("qlQuery", "objectTypeId = " + id); 

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode values = root.path("values");
                    
                    if (values.size() > 0) {
                        // Aquí sacamos el nombre real
                        String nombreTipo = values.get(0).path("objectType").path("name").asText();
                        // Y la cantidad total de objetos que tiene (aproximado)
                        int total = root.path("totalFilterCount").asInt(); // A veces Jira devuelve esto
                        
                        System.out.printf("%-5d | %-30s | %-10d%n", id, nombreTipo, total);
                    } else {
                        // Si devuelve 200 pero vacío, el ID existe pero no tiene datos, o no es un ID válido de tipo
                        System.out.printf("%-5d | %-30s | %-10s%n", id, "[Vacío / Sin Datos]", "0");
                    }
                }
            } catch (Exception e) {
                System.out.println("Error en ID " + id);
            }
        }
        System.out.println("\n✅ Escaneo finalizado.");
    }
}