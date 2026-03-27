package EstadoJira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TransitionManagerCerradoAResuelto {

    // ========= CONFIGURACIÓN =========
    static final String EXCEL_FILE = "cambio_estados_verse.xlsx"; 
    static final int COL_TICKET = 0;
    static final int COL_ESTADO_FINAL = 1;

    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";

        if (jiraUrl.isEmpty() || email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ ERROR: Credenciales incompletas en .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        client = HttpClient.newHttpClient();
        mapper = new ObjectMapper();

        System.out.println(">>> 🚀 INICIANDO CAMBIO DE ESTADOS NOC (De Cerrado a Resuelto) <<<");

        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 

                String issueKey = fmt.formatCellValue(row.getCell(COL_TICKET)).trim();
                String targetStatus = fmt.formatCellValue(row.getCell(COL_ESTADO_FINAL)).trim();

                if (issueKey.isEmpty()) continue;

                System.out.println("\n------------------------------------------------");
                System.out.println("🔍 Procesando " + issueKey);
                procesarFlujo(issueKey, targetStatus);
                
                Thread.sleep(500); 
            }
            System.out.println("\n🏁 PROCESO TERMINADO.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void procesarFlujo(String issueKey, String target) {
        try {
            String currentStatus = getStatus(issueKey);
            System.out.println("   📌 Estado actual: [" + currentStatus + "] | Objetivo: [" + target + "]");

            // --- CAMBIO AQUI: Validamos que el actual sea Cerrado/Cerrada y el objetivo Resuelto/Resuelta ---
            boolean isCurrentCerrado = currentStatus.matches("(?i).*cerrad[oa].*");
            boolean isTargetResuelto = target.matches("(?i).*resuelt[oa].*");

            if (isCurrentCerrado && isTargetResuelto) {
                ejecutarTransicion(issueKey);
            } else {
                System.out.println("   ⏭️ Ignorado: El estado actual no es 'Cerrado' o el Excel no dice 'RESUELTA'.");
            }

        } catch (Exception e) {
            System.err.println("   ❌ Error procesando " + issueKey + ": " + e.getMessage());
        }
    }

    private static String getStatus(String issueKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "?fields=status"))
            .header("Authorization", "Basic " + encodedAuth)
            .GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());
        return root.path("fields").path("status").path("name").asText();
    }

    private static void ejecutarTransicion(String issueKey) throws Exception {
        JsonNode transitionNode = findTransition(issueKey);
        
        if (transitionNode == null) {
            System.err.println("   ❌ Jira no permite pasar a 'Resuelta' desde este estado (La transición no existe o no está permitida en tu Workflow).");
            return;
        }

        String transitionId = transitionNode.path("id").asText();
        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("transition").put("id", transitionId);
        
        // Mantenemos la lógica de resolución, ya que al pasar a "Resuelto" Jira suele pedirla.
        JsonNode resolutionField = transitionNode.path("fields").path("resolution");
        if (!resolutionField.isMissingNode()) {
            JsonNode allowedValues = resolutionField.path("allowedValues");
            if (allowedValues.isArray() && allowedValues.size() > 0) {
                String resId = allowedValues.get(0).path("id").asText();
                payload.putObject("fields").putObject("resolution").put("id", resId);
                System.out.println("   ⚙️ Resolución detectada automáticamente (ID: " + resId + ")");
            } else {
                payload.putObject("fields").putObject("resolution").put("name", "Resuelto");
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/transitions"))
            .header("Authorization", "Basic " + encodedAuth)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 204) {
            System.out.println("   ✅ Movido exitosamente a 'Resuelta'!");
        } else {
            System.err.println("   ⚠️ JIRA RECHAZÓ EL CAMBIO (Error " + response.statusCode() + ").");
            System.err.println("   Detalle: " + response.body());
        }
    }

    private static JsonNode findTransition(String issueKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/transitions?expand=transitions.fields"))
            .header("Authorization", "Basic " + encodedAuth)
            .GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());
        
        for (JsonNode t : root.path("transitions")) {
            String toStatusName = t.path("to").path("name").asText();
            String transitionName = t.path("name").asText();
            
            // Buscamos transiciones que lleven a "Resuelto/a" o se llamen "Resolver"
            if (toStatusName.matches("(?i).*resuelt[oa].*") || transitionName.matches("(?i).*(resuelt[oa]|resolver).*")) {
                return t; 
            }
        }
        return null;
    }
}
