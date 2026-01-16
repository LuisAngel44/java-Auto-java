package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Jira_Create {

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String serviceDeskId = "34"; 
        String requestTypeId = "128"; // Solicitud de soporte, item 3
        
        // --- ACTIVOS ---
        String workspaceId = "01cf423f-729d-4ecc-9da9-3df244069bb5";
        String idColegio = "16988";   // JOSE DE SAN MARTIN
        String idDispositivo = "46494"; // CID 113176

        String auth = email + ":" + token;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        ObjectMapper mapper = new ObjectMapper();

        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", serviceDeskId);
            payload.put("requestTypeId", requestTypeId);
            
            ObjectNode values = payload.putObject("requestFieldValues");

            // 1. TÍTULO
            values.put("summary", "Alerta NOC: Incidente (Final V4)");

            // 2. DESCRIPCIÓN (Texto Simple - Validado)
            String descripcion = "Host: MINEDU5K2_113176\nState: DOWN\nTicket generado vía API Java.";
            values.put("customfield_10180", descripcion);

            // 3. ¡EL CAMPO QUE FALTABA! (Tipo Ticket - Obligatorio)
            // Según tu espía es el ID 10176 y el valor es "Incidente"
            values.putObject("customfield_10176").put("value", "Incidente");

            // 4. OTROS CAMPOS OBLIGATORIOS (De tus imágenes)
            // Contacto
            values.put("customfield_10090", "QUISPE GAYONA, PRESENTACION"); 
            values.put("customfield_10091", "990.707.178");      
            
            // Área (Rural)
            values.putObject("customfield_10504").put("value", "Rural"); 

            // Organización (Item 3)
            values.putArray("customfield_10002").add(3);

            // 5. ACTIVOS (Cajas Azules)
            agregarActivo(values, "customfield_10170", workspaceId, idColegio); // IE
            agregarActivo(values, "customfield_10252", workspaceId, idDispositivo); // Dispositivo

            // --- ENVÍO ---
            String jsonBody = mapper.writeValueAsString(payload);
            
            System.out.println(">>> EJECUTANDO CÓDIGO V4 (CON TIPO TICKET) <<<");
            System.out.println("Enviando...");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .header("X-ExperimentalApi", "opt-in") 
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                JsonNode responseNode = mapper.readTree(response.body());
                String key = responseNode.get("issueKey").asText();
                System.out.println("\n✅ ¡VICTORIA TOTAL! TICKET CREADO: " + key);
                System.out.println("Link: " + jiraUrl + "/browse/" + key);
                System.out.println("Este ticket debe ser IDÉNTICO al manual (con activos, tipo ticket y formulario correcto).");
            } else {
                System.out.println("\n❌ ERROR " + response.statusCode());
                System.out.println("Respuesta: " + response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Función auxiliar para Activos
    private static void agregarActivo(ObjectNode values, String fieldId, String workspaceId, String objectId) {
        String assetRef = workspaceId + ":" + objectId;
        ArrayNode assetArray = values.putArray(fieldId);
        assetArray.addObject().put("id", assetRef);
    }
}