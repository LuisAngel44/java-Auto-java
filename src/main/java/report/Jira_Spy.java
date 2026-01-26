package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

public class Jira_Spy {

    public static void main(String[] args) {
        
        // --- CONFIGURACIÓN ---
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();  
        // EL TICKET MODELO QUE QUEREMOS COPIAR (Sacado de tu imagen)
        String ticketToSpy = "MSP-14876"; 

        String auth = email + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        ObjectMapper mapper = new ObjectMapper();

        try {
            HttpClient client = HttpClient.newHttpClient();
            System.out.println("Espiando al ticket " + ticketToSpy + "...");
            
            // 1. Pedimos el ticket con la opción ?expand=names para que nos de los nombres de los campos
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + ticketToSpy + "?expand=names"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                JsonNode names = root.path("names");   // Diccionario de Nombres (ID -> Nombre legible)
                JsonNode fields = root.path("fields"); // Los datos reales
                
                System.out.println("\n=== MAPA DE CAMPOS DEL TICKET " + ticketToSpy + " ===");
                System.out.println("Copia estos IDs para tu código Java:\n");

                // Iteramos sobre todos los campos que tienen datos
                Iterator<Map.Entry<String, JsonNode>> fieldsIterator = fields.fields();
                
                while (fieldsIterator.hasNext()) {
                    Map.Entry<String, JsonNode> field = fieldsIterator.next();
                    String fieldId = field.getKey();
                    JsonNode value = field.getValue();
                    
                    // Solo nos interesan los campos custom (empiezan por customfield) o los importantes
                    if (fieldId.startsWith("customfield") || fieldId.equals("issuetype") || fieldId.equals("project")) {
                        
                        // Obtenemos el nombre legible (Ej. "Código Modular")
                        String fieldName = names.path(fieldId).asText("Nombre Desconocido");
                        
                        // Ignoramos campos vacíos (null)
                        if (!value.isNull()) {
                            System.out.println("NOMBRE: " + fieldName);
                            System.out.println("   ID:   " + fieldId);
                            
                            // Intentamos mostrar el valor de forma limpia
                            if (value.isObject() && value.has("value")) {
                                System.out.println("   VALOR: [Opción] " + value.path("value").asText());
                            } else if (value.isObject() && value.has("name")) {
                                System.out.println("   VALOR: [Objeto] " + value.path("name").asText());
                            } else if (value.isTextual()) {
                                System.out.println("   VALOR: " + value.asText());
                            } else {
                                System.out.println("   VALOR: " + value.toString()); // Imprime JSON crudo si es complejo
                            }
                            System.out.println("------------------------------------------------");
                        }
                    }
                }
                
            } else {
                System.out.println("Error " + response.statusCode());
                System.out.println(response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}