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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JiraAutoCleaner {

    // --- CONFIGURACIÓN ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // ⚠️ REVISA: Columna donde está el ID del Ticket (Ej: NOC-123). 
    // Columna Z en Excel es el índice 25.
    static final int COL_TICKET_KEY = 25; 

    // MAPA DE CAMPOS SEGÚN EL ITEM
    // Item 1 -> customfield_10250
    // Item 2 -> customfield_10251
    // Item 3 -> customfield_10252
    // Item 4 -> customfield_10253 (El que tenía basura)
    
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        // 1. CARGA DE CREDENCIALES
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";
        
        if (email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ Error: Faltan credenciales en el archivo .env");
            return;
        }
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        // 2. ABRIR EL EXCEL
        try (FileInputStream file = new FileInputStream(new File("carga_Actulizar_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🧹 INICIANDO LIMPIEZA Y ACTUALIZACIÓN <<<");
            
            int actualizados = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                // Obtener ID del Ticket (Columna Z)
                String issueKey = getCellValue(row, COL_TICKET_KEY);
                
                // Si no hay ID o es muy largo (título), saltamos
                if (issueKey.isEmpty() || issueKey.length() > 20) continue;

                System.out.println("\n🔄 Procesando: " + issueKey);

                // LEER DATOS CLAVE
                String itemRaw = getCellValue(row, 19); // Columna T (Item)
                String itemNum = extraerSoloNumeros(itemRaw); 
                String idDispositivo = getCellValue(row, 3); // Columna D
                String idColegio = getCellValue(row, 2);     // Columna C

                // --- CONSTRUCCIÓN JSON ---
                ObjectNode payload = mapper.createObjectNode();
                ObjectNode fields = payload.putObject("fields");

                // =================================================================
                // 🧹 LÓGICA DE LIMPIEZA AUTOMÁTICA DE ITEMS
                // =================================================================
                
                Map<String, String> mapItems = new HashMap<>();
                mapItems.put("1", "customfield_10250");
                mapItems.put("2", "customfield_10251");
                mapItems.put("3", "customfield_10252");
                mapItems.put("4", "customfield_10253");

                // Verificamos si tenemos un número de Item válido (1, 2, 3 o 4)
                if (!itemNum.isEmpty() && mapItems.containsKey(itemNum)) {
                    
                    // Recorremos los 4 campos posibles
                    for (Map.Entry<String, String> entry : mapItems.entrySet()) {
                        String numItemIteracion = entry.getKey();     // "1", "2", "3", "4"
                        String fieldId = entry.getValue();            // ID del campo en Jira

                        if (numItemIteracion.equals(itemNum)) {
                            // SI COINCIDE CON EL EXCEL: Ponemos el dispositivo (ACTUALIZAR)
                            putAssetOrNull(fields, fieldId, idDispositivo);
                            System.out.println("   ✅ Item " + numItemIteracion + ": ASIGNANDO dispositivo " + idDispositivo);
                        } else {
                            // SI NO COINCIDE: Enviamos lista vacía (BORRAR BASURA)
                            fields.putArray(fieldId); 
                            // System.out.println("   🗑️ Item " + numItemIteracion + ": Limpiando campo...");
                        }
                    }
                    
                    // Asignar Organización Correcta según el Item
                    String realOrgId = obtenerOrgId(itemNum);
                    if (!realOrgId.isEmpty()) {
                        fields.putArray("customfield_10002").add(realOrgId);
                    }

                } else {
                    System.out.println("   ⚠️ Fila " + (row.getRowNum()+1) + ": Item desconocido o vacío (" + itemRaw + "). NO SE TOCARÁN DISPOSITIVOS.");
                }

                // =================================================================
                // RESTO DE CAMPOS (Siempre se actualizan igual)
                // =================================================================
                putOrNull(fields, "summary", getCellValue(row, 0));
                putOrNull(fields, "customfield_10320", issueKey);       
                putOrNull(fields, "customfield_10469", getCellValue(row, 17).toUpperCase());
                putOrNull(fields, "customfield_10178", getCellValue(row, 18));
                
                // Textos largos (Descripción y Solución)
                putOrNull(fields, "customfield_10180", getCellValue(row, 1)); 
                putOrNull(fields, "customfield_10089", getCellValue(row, 20)); 

                // Ubicación y Dropdowns
                String area = getCellValue(row, 22);
                putDropdownOrNull(fields, "customfield_10504", area.equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
                putDropdownOrNull(fields, "customfield_10394", getCellValue(row, 24)); // Categoría
                putDropdownOrNull(fields, "customfield_10135", getCellValue(row, 23)); // Causa Raíz

                putOrNull(fields, "customfield_10355", getCellValue(row, 6)); // Dep
                putOrNull(fields, "customfield_10356", getCellValue(row, 7)); // Prov
                putOrNull(fields, "customfield_10357", getCellValue(row, 8)); // Dist
                putOrNull(fields, "customfield_10358", getCellValue(row, 9)); // Dir
                putOrNull(fields, "customfield_10359", getCellValue(row, 12)); // IE
                putOrNull(fields, "customfield_10169", getCellValue(row, 13)); // CodMod
                putOrNull(fields, "customfield_10168", getCellValue(row, 14)); // CodLoc
                putOrNull(fields, "customfield_10361", getCellValue(row, 16)); // Medio Trans

                // Fechas
                putFechaOrNull(fields, "customfield_10321", getCellValue(row, 10));
                putFechaOrNull(fields, "customfield_10322", getCellValue(row, 11));

                // Asset Colegio (Siempre es fijo en el campo 10170)
                putAssetOrNull(fields, "customfield_10170", idColegio);


                // --- 3. ENVIAR A JIRA ---
                String jsonParaEnviar = mapper.writeValueAsString(payload);
                boolean exito = enviarUpdateV2(issueKey, jsonParaEnviar);
                
                if (exito) {
                    actualizados++;
                    row.createCell(26).setCellValue("LIMPIEZA OK");
                } else {
                    row.createCell(26).setCellValue("ERROR");
                }
                
                Thread.sleep(150); // Pausa pequeña para no saturar
            }

            // 4. GUARDAR RESULTADOS
            try (FileOutputStream fileOut = new FileOutputStream("resultado_limpieza.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n🏁 FIN. Tickets procesados: " + actualizados);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- MÉTODOS AUXILIARES ---

    private static boolean enviarUpdateV2(String issueKey, String jsonBody) {
        try {
            // CODIFICAR URL: Esto arregla el error de "Illegal character in path"
            String encodedKey = URLEncoder.encode(issueKey.trim(), StandardCharsets.UTF_8);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + encodedKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204) {
                return true;
            } else {
                System.err.println("   ❌ ERROR API (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) { 
            System.err.println("   ❌ ERROR RED: " + e.getMessage());
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
        if (val == null || val.trim().isEmpty()) {
            fields.putArray(key); // ARRAY VACÍO -> BORRA EL CAMPO EN JIRA
        } else {
            ArrayNode arr = fields.putArray(key);
            ObjectNode asset = arr.addObject();
            asset.put("workspaceId", WORKSPACE_ID);
            asset.put("id", WORKSPACE_ID + ":" + val);
        }
    }
    
    private static String obtenerOrgId(String item) {
        if ("1".equals(item)) return "2";
        if ("2".equals(item)) return "1";
        if ("3".equals(item)) return "3";
        if ("4".equals(item)) return "4";
        return "";
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