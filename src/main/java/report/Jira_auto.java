package report;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import io.github.cdimascio.dotenv.Dotenv; // Importante agregar esta librería
public class Jira_auto {

	public static void main(String[] args) {
		// 1. Cargamos el archivo .env que está en la raíz
        Dotenv dotenv = Dotenv.load();
		// 2. Reemplazamos las cadenas de texto por las variables del archivo
        String jiraUrl = dotenv.get("JIRA_URL").trim();
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
     // AGREGA ESTO PARA VERIFICAR:
        System.out.println("--- DEPURACIÓN ---");
        System.out.println("URL Base: '" + jiraUrl + "'"); // Fíjate si tiene comillas o espacios
        System.out.println("URL Final: '" + jiraUrl + "/rest/api/3/search'");
        System.out.println("Email: '" + email + "'");
        System.out.println("------------------");
        
        System.out.print("sss"+token);
        String auth = email + ":" + token;
		        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
		        ObjectMapper mapper = new ObjectMapper();

		        try {
		            ObjectNode payload = mapper.createObjectNode();
		            payload.put("jql", "project = 'MSP' ORDER BY created DESC");
		            payload.put("maxResults", 50);
		            
		            ArrayNode fields = payload.putArray("fields");
		            fields.add("summary").add("status").add("created").add("assignee");
		            fields.add("customfield_10469"); // Tipo de Incidencia
		            fields.add("customfield_10355"); // Departamento / Sede
		            fields.add("customfield_10504"); // Área

		            String jsonBody = mapper.writeValueAsString(payload);

		            HttpClient client = HttpClient.newHttpClient();
		            HttpRequest request = HttpRequest.newBuilder()
		            		.uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
		                    .header("Authorization", "Basic " + encodedAuth)
		                    .header("Content-Type", "application/json")
		                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
		                    .build();

		            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		            if (response.statusCode() == 200) {
		                JsonNode root = mapper.readTree(response.body());
		                JsonNode issues = root.path("issues");

		                System.out.println("\n=== REPORTE DETALLADO DE ALERTAS MSP (ÚLTIMAS 15) ===");
		                // Ajustamos el formato para una tabla mucho más ancha
		                String format = "%-12s | %-18s | %-15s | %-15s | %-50s\n";
		                System.out.printf(format, "TICKET", "ESTADO", "SEDE/DEP", "ÁREA", "RESUMEN");
		                System.out.println("-".repeat(125));

		                for (JsonNode issue : issues) {
		                    String key = issue.path("key").asText("N/A");
		                    JsonNode f = issue.path("fields");
		                    
		                    String status = f.path("status").path("name").asText("N/A");
		                    
		                    // Extraer Sede/Departamento (customfield_10355)
		                    String sede = obtenerTexto(f.path("customfield_10355"));
		                    
		                    // Extraer Área (customfield_10504)
		                    String area = obtenerTexto(f.path("customfield_10504"));
		                    
		                    String summary = f.path("summary").asText("Sin título");
		                    if (summary.length() > 47) summary = summary.substring(0, 47) + "...";

		                    System.out.printf(format, key, status, sede, area, summary);
		                }
		                System.out.println("-".repeat(125));
		                
		            } else {
		                System.out.println("Error " + response.statusCode() + ": " + response.body());
		            }

		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    }

		    // Método auxiliar para extraer texto de campos que pueden ser objetos o strings
		    private static String obtenerTexto(JsonNode node) {
		        if (node.isMissingNode() || node.isNull()) return "N/A";
		        if (node.has("value")) return node.get("value").asText();
		        return node.asText("N/A");
		    }
		}