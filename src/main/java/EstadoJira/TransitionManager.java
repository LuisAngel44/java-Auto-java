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

public class TransitionManager {

    // ========= CONFIGURACIÓN =========
    static final String EXCEL_FILE = "cambio_estados.xlsx"; 
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

        System.out.println(">>> 🚀 INICIANDO CAMBIO DE ESTADOS (NOC) <<<");

        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                String issueKey = fmt.formatCellValue(row.getCell(COL_TICKET)).trim();
                String targetStatus = fmt.formatCellValue(row.getCell(COL_ESTADO_FINAL)).trim();

                if (issueKey.isEmpty()) continue;

                System.out.println("\n🔍 Procesando " + issueKey);
                procesarFlujo(issueKey, targetStatus);
                
                // Pequeño delay para no saturar la API de Jira
                Thread.sleep(300);
            }

            System.out.println("\n🏁 PROCESO TERMINADO.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void procesarFlujo(String issueKey, String target) {
        try {
            // 1. Obtener estado actual
            String currentStatus = getStatus(issueKey);
            System.out.println("   Estado actual: [" + currentStatus + "] | Objetivo: [" + target + "]");

            // Estandarizamos las validaciones usando Regex (Ignora mayúsculas y terminaciones a/o)
            boolean isCurrentCerrado = currentStatus.matches("(?i).*cerrad[oa].*");
            boolean isCurrentResuelto = currentStatus.matches("(?i).*resuelt[oa].*");
            boolean isCurrentEsperando = currentStatus.matches("(?i).*esperando.*");

            boolean isTargetCerrado = target.matches("(?i).*cerrad[oa].*");
            boolean isTargetResuelto = target.matches("(?i).*resuelt[oa].*");

            // 2. Si ya está en la etapa objetivo, lo omitimos
            if ((isCurrentCerrado && isTargetCerrado) || (isCurrentResuelto && isTargetResuelto)) {
                System.out.println("   ⏭️ El ticket ya está en el estado solicitado. Se deja como se encontró.");
                return;
            }

            // 3. Lógica de transición según el estado actual
            if (isCurrentEsperando) {
                if (isTargetResuelto) {
                    if (ejecutarTransicion(issueKey, "Resuelto")) {
                        System.out.println("   ✅ Movido exitosamente a Resuelto");
                    }
                } else if (isTargetCerrado) {
                    if (ejecutarTransicion(issueKey, "Resuelto")) {
                        System.out.println("   ✅ Movido a Resuelto (Paso 1/2)");
                        if (ejecutarTransicion(issueKey, "Cerrado")) {
                            System.out.println("   ✅ Movido a Cerrado (Paso 2/2)");
                        }
                    }
                }
            } else if (isCurrentResuelto) {
                if (isTargetCerrado) {
                    if (ejecutarTransicion(issueKey, "Cerrado")) {
                        System.out.println("   ✅ Movido exitosamente a Cerrado");
                    }
                }
            } else {
                System.out.println("   ⚠️ El estado inicial no permite el salto automático a tu objetivo o no está contemplado.");
            }

        } catch (Exception e) {
            System.err.println("   ❌ Error en " + issueKey + ": " + e.getMessage());
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

    private static boolean ejecutarTransicion(String issueKey, String targetState) throws Exception {
        String transitionId = findTransitionId(issueKey, targetState);
        
        if (transitionId == null) {
            System.err.println("   ❌ Jira no habilitó el botón para pasar a '" + targetState + "' desde el estado actual.");
            return false;
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("transition").put("id", transitionId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/transitions"))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 204;
    }

    private static String findTransitionId(String issueKey, String targetState) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/transitions"))
                .header("Authorization", "Basic " + encodedAuth)
                .GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());
        
        for (JsonNode t : root.path("transitions")) {
            String transitionName = t.path("name").asText().toLowerCase();
            
            // Busca coincidencias amplias (Ej: "Resuelta", "Resuelto", "Resolver")
            if (targetState.equals("Resuelto") && transitionName.matches(".*(resuelt[oa]|resolver).*")) {
                return t.path("id").asText();
            }
            // Busca coincidencias amplias (Ej: "Cerrada", "Cerrado", "Cerrar")
            if (targetState.equals("Cerrado") && transitionName.matches(".*(cerrad[oa]|cerrar).*")) {
                return t.path("id").asText();
            }
        }
        return null;
    }
}