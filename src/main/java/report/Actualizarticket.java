package report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder; // Importante para arreglar el error de espacios
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Actualizarticket {

    // --- CONFIGURACIÓN ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // ⚠️ IMPORTANTE: COLUMNA DONDE ESTÁ EL ID TIPO "SD-123" o "NOC-500"
    // 25 = Columna Z. Si tus IDs están en la A, cambia a 0.
    static final int COL_TICKET_KEY = 25; 

    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";
        
        if (email.isEmpty() || token.isEmpty()) return;
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("carga_Actulizar_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🛠️ INICIANDO (VERSIÓN CORREGIDA URL) <<<");
            
            int actualizados = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 

                // 1. OBTENER ID DEL TICKET
                String issueKey = getCellValue(row, COL_TICKET_KEY);
                
                // ⚠️ VALIDACIÓN CRÍTICA:
                // Si la celda Z está vacía, NO INTENTAMOS ADIVINAR. Saltamos.
                if (issueKey.isEmpty()) {
                    System.out.println("⚠️ Fila " + (row.getRowNum() + 1) + ": SALTADA (Falta el ID del ticket en la columna Z)");
                    row.createCell(26).setCellValue("FALTA ID");
                    continue; 
                }

                // Validación visual: Un ID de Jira suele ser CORTO (ej: "SD-123")
                // Si es muy largo (>20 caracteres), probablemente es basura o un título.
                if (issueKey.length() > 20) {
                     System.out.println("⚠️ Fila " + (row.getRowNum() + 1) + ": ERROR - El ID '" + issueKey.substring(0, 15) + "...' parece un texto, no un ID.");
                     row.createCell(26).setCellValue("ID INVALIDO");
                     continue;
                }

                System.out.println("\n🔄 Procesando Ticket ID: " + issueKey);

                // --- LECTURA DE DATOS ---
                String resumen = getCellValue(row, 0);
                String descAlarma = getCellValue(row, 1);     
                String idColegio = getCellValue(row, 2);      
                String idDispositivo = getCellValue(row, 3);  
                String contactoNom = getCellValue(row, 4);
                String contactoCel = getCellValue(row, 5);
                String dep = getCellValue(row, 6);
                String prov = getCellValue(row, 7);
                String dist = getCellValue(row, 8);
                String direccion = getCellValue(row, 9);
                String fechaGen = getCellValue(row, 10);
                String fechaSol = getCellValue(row, 11);
                String nombreIE = getCellValue(row, 12);
                String codModular = getCellValue(row, 13);
                String codLocal = getCellValue(row, 14);
                String medioTrans = getCellValue(row, 16);
                String tipoIncidencia = getCellValue(row, 17);
                String tiempoNoDisp = getCellValue(row, 18);
                String itemRaw = getCellValue(row, 19); 
                String itemNum = extraerSoloNumeros(itemRaw); 
                String textoSolucion = getCellValue(row, 20); 
                String areaExcel = getCellValue(row, 22);
                String causaRaiz = getCellValue(row, 23);      
                String catServicio = getCellValue(row, 24);    

                String[] config = obtenerConfiguracionItem(itemNum); 
                String assetFieldId = config[1];
                String realOrgId = config[2];

                // --- CONSTRUCCIÓN JSON ---
                ObjectNode payload = mapper.createObjectNode();
                ObjectNode fields = payload.putObject("fields");

                putOrNull(fields, "summary", resumen);
                putOrNull(fields, "customfield_10090", contactoNom); 
                putOrNull(fields, "customfield_10091", contactoCel); 
                putOrNull(fields, "customfield_10320", issueKey); // Referencia Ticket ID       
                putOrNull(fields, "customfield_10469", tipoIncidencia.toUpperCase());
                putOrNull(fields, "customfield_10178", tiempoNoDisp);
                
                // Campos Largos
                putOrNull(fields, "customfield_10180", descAlarma);
                putOrNull(fields, "customfield_10089", textoSolucion);

                // Dropdowns
                putDropdownOrNull(fields, "customfield_10394", catServicio);
                putDropdownOrNull(fields, "customfield_10135", causaRaiz);
                putDropdownOrNull(fields, "customfield_10504", areaExcel.equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
                
                // Ubicación
                putOrNull(fields, "customfield_10355", dep);
                putOrNull(fields, "customfield_10356", prov);
                putOrNull(fields, "customfield_10357", dist);
                putOrNull(fields, "customfield_10358", direccion);
                putOrNull(fields, "customfield_10359", nombreIE);
                putOrNull(fields, "customfield_10169", codModular);
                putOrNull(fields, "customfield_10168", codLocal);
                putOrNull(fields, "customfield_10361", medioTrans);

                // Fechas
                putFechaOrNull(fields, "customfield_10321", fechaGen);
                putFechaOrNull(fields, "customfield_10322", fechaSol);

                // Assets
                putAssetOrNull(fields, "customfield_10170", idColegio);
                putAssetOrNull(fields, assetFieldId, idDispositivo);

                // Organización
                if (realOrgId.isEmpty()) {
                    fields.putArray("customfield_10002"); 
                } else {
                    fields.putArray("customfield_10002").add(realOrgId);
                }

                // --- ENVÍO ---
                String jsonParaEnviar = mapper.writeValueAsString(payload);
                boolean exito = enviarUpdateV2(issueKey, jsonParaEnviar);
                
                if (exito) {
                    actualizados++;
                    row.createCell(26).setCellValue("ACTUALIZADO OK");
                } else {
                    row.createCell(26).setCellValue("ERROR API");
                }
                
                Thread.sleep(200); 
            }

            try (FileOutputStream fileOut = new FileOutputStream("resultado_final.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n🏁 FIN. Tickets actualizados correctamente: " + actualizados);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- MÉTODOS AUXILIARES ---

    private static boolean enviarUpdateV2(String issueKey, String jsonBody) {
        try {
            // CORRECCIÓN: Codificar la URL para evitar errores de espacios
            String encodedKey = URLEncoder.encode(issueKey.trim(), StandardCharsets.UTF_8);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + encodedKey)) // Usamos la key codificada
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204) {
                System.out.println("   ✅ OK");
                return true;
            } else {
                System.err.println("   ❌ ERROR (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("   ❌ Error de Red/URL: " + e.getMessage());
            return false;
        }
    }

    private static void putOrNull(ObjectNode fields, String key, String val) {
        if (val == null || val.trim().isEmpty()) fields.putNull(key);
        else fields.put(key, val.trim());
    }

    private static void putDropdownOrNull(ObjectNode fields, String key, String val) {
        if (val == null || val.trim().isEmpty()) fields.putNull(key);
        else fields.putObject(key).put("value", val.trim());
    }

    private static void putFechaOrNull(ObjectNode fields, String key, String val) {
        if (val == null || val.trim().isEmpty() || val.contains("1900")) fields.putNull(key);
        else fields.put(key, formatearFecha(val));
    }

    private static void putAssetOrNull(ObjectNode fields, String key, String val) {
        if (val == null || val.trim().isEmpty()) fields.putArray(key);
        else {
            ArrayNode arr = fields.putArray(key);
            ObjectNode asset = arr.addObject();
            asset.put("workspaceId", WORKSPACE_ID);
            asset.put("id", WORKSPACE_ID + ":" + val);
        }
    }

    private static String[] obtenerConfiguracionItem(String numeroItem) {
        switch (numeroItem) {
            case "1": return new String[]{"126", "customfield_10250", "2"};
            case "2": return new String[]{"127", "customfield_10251", "1"};
            case "3": return new String[]{"128", "customfield_10252", "3"};
            case "4": return new String[]{"129", "customfield_10253", "4"};
            default:  return new String[]{"129", "customfield_10253", ""};
        }
    }
    
    private static String extraerSoloNumeros(String input) {
        if (input == null) return "";
        Matcher m = Pattern.compile("\\d+").matcher(input);
        return m.find() ? m.group() : "";
    }

    private static String formatearFecha(String f) {
        if (f.contains("T")) return f;
        return f.replace(" ", "T") + ".000-0500";
    }

    private static String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return new DataFormatter().formatCellValue(cell).trim();
    }
}