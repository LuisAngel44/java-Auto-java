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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Actuliza_status {

    // --- CONFIGURACIÓN GENERAL ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // --- CONFIGURACIÓN DE COLUMNAS EXCEL ---
    // 25 = Columna Z (Donde está el ID del ticket, ej: MSP-123)
    static final int COL_TICKET_KEY = 25; 
    
    // ⚠️ COLUMNA NUEVA: Donde escribes el estado deseado (ej: "RESUELTA" o "EN ESPERA")
    // 26 = Columna AA. Asegúrate de que en tu Excel tengas el estado escrito en esta columna.
    static final int COL_ESTADO_DESTINO = 26; 

    // --- CONFIGURACIÓN DE TRANSICIONES (IDs DE JIRA) ---
 // --- ⚠️ IDs DE TRANSICIÓN (ACTUALIZADOS CON TU JIRA) ---
    // Extraídos del JSON del ticket MSP-39832
    static final String ID_TRANSICION_RESOLVER = "761";   // "Resolver esta incidencia" -> Resuelta
    static final String ID_TRANSICION_EN_ESPERA = "871";  // "Pendiente" -> Pendiente
    static final String ID_TRANSICION_CANCELAR = "901";   // "Cancelar solicitud" -> Cancelado

    // Variables globales
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        // Carga de credenciales
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";
        
        if (email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ Error: Faltan credenciales en .env");
            return;
        }
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("carga_Actulizar_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🛠️ INICIANDO PROCESO DE ACTUALIZACIÓN + RESOLUCIÓN <<<");
            
            int procesados = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                // 1. OBTENER ID DEL TICKET
                String issueKey = getCellValue(row, COL_TICKET_KEY);
                String estadoDeseado = getCellValue(row, COL_ESTADO_DESTINO); // Leemos el estado deseado

                if (issueKey.isEmpty()) {
                    row.createCell(28).setCellValue("FALTA ID");
                    continue; 
                }

                if (issueKey.length() > 20) {
                    row.createCell(28).setCellValue("ID INVALIDO");
                    continue;
                }

                System.out.println("\n🔄 Procesando Ticket: " + issueKey);

                // --- PASO 1: ACTUALIZACIÓN DE CAMPOS ---
                // Lectura de datos del Excel
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

                // Construcción JSON para Update
                ObjectNode payload = mapper.createObjectNode();
                ObjectNode fields = payload.putObject("fields");

                putOrNull(fields, "summary", resumen);
                putOrNull(fields, "customfield_10090", contactoNom); 
                putOrNull(fields, "customfield_10091", contactoCel); 
                putOrNull(fields, "customfield_10320", issueKey);      
                putOrNull(fields, "customfield_10469", tipoIncidencia.toUpperCase());
                putOrNull(fields, "customfield_10178", tiempoNoDisp);
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

                // Enviar Update
                String jsonParaEnviar = mapper.writeValueAsString(payload);
                boolean updateExito = enviarUpdateV2(issueKey, jsonParaEnviar);
                
                // --- PASO 2: TRANSICIÓN DE ESTADO (RESOLVER) ---
                boolean transicionExito = true; // Asumimos éxito si no hay nada que hacer
                
                // Solo intentamos mover el estado si el update anterior funcionó y hay un estado escrito
                if (updateExito && !estadoDeseado.isEmpty()) {
                    String idTransicion = obtenerIdTransicion(estadoDeseado);
                    
                    if (idTransicion != null) {
                        System.out.println("   ➡️ Intentando mover a estado: " + estadoDeseado + " (ID: " + idTransicion + ")");
                        
                        // LÓGICA ESPECIAL PARA RESOLVER:
                        // Si vamos a "RESUELTA", enviamos la resolución "Listo" obligatoria.
                        String resolucion = null;
                        if (idTransicion.equals(ID_TRANSICION_RESOLVER)) { 
                            resolucion = "Listo"; 
                        }

                        transicionExito = enviarTransicion(issueKey, idTransicion, resolucion);
                    } else {
                        System.out.println("   ⚠️ Estado desconocido en Excel: " + estadoDeseado + " (Se omitió transición)");
                    }
                }

                // Resultado final en el Excel
                if (updateExito && transicionExito) {
                    procesados++;
                    row.createCell(28).setCellValue("TODO OK");
                } else {
                    row.createCell(28).setCellValue("ERROR");
                }
                
                Thread.sleep(200); // Pequeña pausa para no saturar Jira
            }

            // Guardar archivo final
            try (FileOutputStream fileOut = new FileOutputStream("resultado_final_completo.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n🏁 FIN DEL PROCESO. Tickets procesados correctamente: " + procesados);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- MÉTODOS DE CONEXIÓN JIRA ---

    private static boolean enviarUpdateV2(String issueKey, String jsonBody) {
        try {
            String encodedKey = URLEncoder.encode(issueKey.trim(), StandardCharsets.UTF_8);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + encodedKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204) {
                System.out.println("   ✅ Datos actualizados.");
                return true;
            } else {
                System.err.println("   ❌ Error Update (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("   ❌ Error de Red Update: " + e.getMessage());
            return false;
        }
    }

    // MÉTODO PARA CAMBIAR ESTADO Y PONER "LISTO"
    private static boolean enviarTransicion(String issueKey, String transitionId, String resolutionName) {
        try {
            String encodedKey = URLEncoder.encode(issueKey.trim(), StandardCharsets.UTF_8);
            String url = jiraUrl + "/rest/api/2/issue/" + encodedKey + "/transitions";

            ObjectNode root = mapper.createObjectNode();
            root.putObject("transition").put("id", transitionId);
            
            // Si nos pasaron un nombre de resolución (ej: "Listo"), lo agregamos
            if (resolutionName != null && !resolutionName.isEmpty()) {
                ObjectNode fields = root.putObject("fields");
                fields.putObject("resolution").put("name", resolutionName);
            }

            String jsonBody = mapper.writeValueAsString(root);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                System.out.println("   ✅ Estado cambiado con éxito.");
                return true;
            } else {
                System.err.println("   ❌ Error Transición (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("   ❌ Error de Red Transición: " + e.getMessage());
            return false;
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private static String obtenerIdTransicion(String nombreEstadoExcel) {
        if (nombreEstadoExcel == null) return null;
        String estado = nombreEstadoExcel.trim().toUpperCase();
        
        // Mapeo simple: Nombre en Excel -> ID de Jira
        if (estado.contains("RESUELTA") || estado.contains("RESOLVER")) return ID_TRANSICION_RESOLVER;
        if (estado.contains("ESPERA")) return ID_TRANSICION_EN_ESPERA;
        if (estado.contains("CANCELAR")) return ID_TRANSICION_CANCELAR;
        
        return null;
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