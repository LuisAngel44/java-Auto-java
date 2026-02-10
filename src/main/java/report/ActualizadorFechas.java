package report;

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
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

public class ActualizadorFechas {

    // 🚨 CONFIGURACIÓN JIRA
    static final String FIELD_FECHA_GEN = "customfield_10321"; 
    static final String FIELD_FECHA_SOL = "customfield_10322"; 

    static HttpClient client;
    static ObjectMapper mapper;
    static String jiraUrl;
    static String encodedAuth;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL");
        String token = dotenv.get("JIRA_TOKEN");

        if (email == null || token == null || jiraUrl.isEmpty()) {
            System.err.println("❌ ERROR: Faltan credenciales en el archivo .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("cambiar_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(); 

            System.out.println(">>> 🚀 INICIANDO CARGA (V. FINAL - FIX FECHAS DD/MM) <<<");
            
            int ok = 0;
            int error = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String issueKey = getVal(row, 0, formatter);
                String fGen     = getVal(row, 1, formatter);
                String fSol     = getVal(row, 2, formatter);

                if (issueKey.isEmpty()) continue; 
                
                System.out.print("🔄 Ticket " + issueKey + " [Gen: " + fGen + "] ... ");

                if (enviarActualizacion(issueKey, fGen, fSol)) {
                    System.out.println("✅ OK");
                    ok++;
                } else {
                    error++;
                }
                
                Thread.sleep(150);
            }
            
            System.out.println("\n------------------------------------------------");
            System.out.println("🏁 FIN DEL PROCESO | Correctos: " + ok + " | Fallidos: " + error);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean enviarActualizacion(String key, String fGen, String fSol) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            boolean dataToSend = false;

            String g = formatearParaJira(fGen);
            String s = formatearParaJira(fSol);

            if (g != null) { fields.put(FIELD_FECHA_GEN, g); dataToSend = true; }
            if (s != null) { fields.put(FIELD_FECHA_SOL, s); dataToSend = true; }

            if (!dataToSend) return false;

            String jsonBody = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + key))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                return true; 
            } else {
                System.out.println("\n❌ Error Jira: " + response.body());
                return false;
            }

        } catch (Exception e) {
            System.out.println("\n❌ Error: " + e.getMessage());
            return false;
        }
    }

    private static String formatearParaJira(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        
        try {
            String limpio = raw.trim().replaceAll("\\s+", " "); 
            String fechaPart = limpio;
            String horaPart = "00:00:00";

            if (limpio.contains(" ")) {
                String[] partes = limpio.split(" ");
                fechaPart = partes[0];
                if (partes.length > 1) horaPart = partes[1];
            }

            // --- LÓGICA ESTRICTA DD/MM/YYYY ---
            String[] dmy = fechaPart.split("[/\\-]");
            if (dmy.length == 3) {
                String dia, mes, anio;

                if (dmy[0].length() == 4) { // Ya viene como YYYY-MM-DD
                    anio = dmy[0]; mes = dmy[1]; dia = dmy[2];
                } else { // Viene como DD-MM-YYYY (Forzamos este orden)
                    dia = dmy[0]; mes = dmy[1]; anio = dmy[2];
                }

                // Normalización de dígitos
                dia = (dia.length() == 1) ? "0" + dia : dia;
                mes = (mes.length() == 1) ? "0" + mes : mes;
                anio = (anio.length() == 2) ? "20" + anio : anio;

                fechaPart = anio + "-" + mes + "-" + dia;
            }

            // Normalización de Hora HH:mm:ss
            String[] hms = horaPart.split(":");
            String hh = (hms.length > 0) ? hms[0] : "00";
            String mm = (hms.length > 1) ? hms[1] : "00";
            String ss = (hms.length > 2) ? hms[2] : "00";

            hh = (hh.length() == 1) ? "0" + hh : hh;
            mm = (mm.length() == 1) ? "0" + mm : mm;
            ss = (ss.length() == 1) ? "0" + ss : ss;

            return fechaPart + "T" + hh + ":" + mm + ":" + ss + ".000-0500";

        } catch (Exception e) {
            return null;
        }
    }

    private static String getVal(Row row, int index, DataFormatter fmt) {
        Cell c = row.getCell(index);
        if (c == null) return "";

        // Si Excel reconoce la celda como FECHA, la extraemos manualmente
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            Date date = c.getDateCellValue();
            // Esto garantiza que el String que reciba el programa SIEMPRE sea día/mes/año
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(date);
        }
        
        return fmt.formatCellValue(c).trim();
    }
}