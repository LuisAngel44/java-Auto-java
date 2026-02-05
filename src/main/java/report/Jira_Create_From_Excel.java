package report;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Jira_Create_From_Excel {

    // --- ⚙️ CONFIGURACIÓN GLOBAL ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // Variables estáticas
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        // 1. Cargar Entorno
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        
        if (email == null || token == null) {
            System.err.println("❌ ERROR: Faltan credenciales en .env");
            return;
        }
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        // 2. Procesar Excel
        try (FileInputStream file = new FileInputStream(new File("carga_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🚀 INICIANDO CARGA MASIVA OPTIMIZADA <<<");
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                // Leer datos básicos
                String host = getCellValue(row, 0);
                if (host.isEmpty()) break; // Fin del archivo

                String estado = getCellValue(row, 1);
                String idColegio = getCellValue(row, 2);
                String idDispositivo = getCellValue(row, 3);
                String contactoNom = getCellValue(row, 4);
                String contactoCel = getCellValue(row, 5);
                
                // Datos para Update (Paso 2)
                String dep = getCellValue(row, 6);
                String prov = getCellValue(row, 7);
                String dist = getCellValue(row, 8);
                String direccion = getCellValue(row, 9);
                String fechaGen = getCellValue(row, 10);
                String fechaSol = getCellValue(row, 11);
                String nombreIE = getCellValue(row, 12);
                String codModular = getCellValue(row, 13);
                String codLocal = getCellValue(row, 14);
                String numTicketRef = getCellValue(row, 15);
                String medioTrans = getCellValue(row, 16);
                String tipoIncidencia = getCellValue(row, 17);
                String tiempoNoDisp = getCellValue(row, 18);

                // --- ⚡ DEFINIR QUÉ ITEM ES (IMPORTANTE) ---
                // Opción A: Leerlo de una columna nueva del Excel (ej: columna 20)
                 String itemSeleccionado = getCellValue(row, 19); 
                
                // Opción B: Lógica manual (Por defecto "3" si no hay lógica)
               // String itemSeleccionado = "3"; 
                // Ejemplo: Si el host empieza con 'R', es Item 1 (puedes descomentar y adaptar)
                // if (host.startsWith("R")) itemSeleccionado = "1";

                System.out.println("\n------------------------------------------------");
                System.out.println("🔄 Procesando Fila #" + row.getRowNum() + " | Host: " + host + " | Item: " + itemSeleccionado);

                // PASO 1: Crear Ticket (Con lógica dinámica de Item)
                String issueKey = crearTicketBasico(fechaGen, host, estado, contactoNom, contactoCel, idColegio, idDispositivo, itemSeleccionado);

                // PASO 2: Actualizar Ticket
                if (issueKey != null) {
                    actualizarCamposCompletos(issueKey, dep, prov, dist, direccion, fechaGen, fechaSol,
                            nombreIE, codModular, codLocal, numTicketRef, medioTrans, tipoIncidencia, tiempoNoDisp);

                    // Guardar éxito en Excel (Columna T / Índice 20)
                    row.createCell(20).setCellValue(issueKey);
                } else {
                    row.createCell(20).setCellValue("ERROR");
                }
                
                // Pausa pequeña para evitar rate-limit
                Thread.sleep(200); 
            }

            // Guardar archivo final
            try (FileOutputStream fileOut = new FileOutputStream("resultado_carga_full.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n✅ REPORTE GENERADO: resultado_carga_full.xlsx");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 🧠 LÓGICA CORE: OBTENER CONFIGURACIÓN SEGÚN EL ITEM
    // =========================================================================
    private static String[] obtenerConfiguracionItem(String numeroItem) {
        String requestTypeId;
        String campoId;

        switch (numeroItem) {
            case "1":
                // ⚠️ IMPORTANTE: Pon aquí el ID real del Request Type para "Item 1"
                requestTypeId = "126"; // <-- ¡CAMBIAR SI ES NECESARIO!
                campoId = "customfield_10250";
                break;
            case "2":
                requestTypeId = "127"; // <-- ¡CAMBIAR SI ES NECESARIO!
                campoId = "customfield_10251";
                break;
            case "3":
                requestTypeId = "128"; // Confirmado que este funciona para Item 3
                campoId = "customfield_10252";
                break;
            case "4":
                requestTypeId = "129"; // <-- ¡CAMBIAR SI ES NECESARIO!
                campoId = "customfield_10253";
                break;
            default:
                System.out.println("⚠️ Item desconocido (" + numeroItem + "). Usando defecto (Item 3).");
                requestTypeId = "1";
                campoId = "customfield_10252";
                break;
        }
        return new String[]{requestTypeId, campoId};
    }

    // =========================================================================
    // 📨 PASO 1: CREAR TICKET (SERVICE DESK API)
    // =========================================================================
    private static String crearTicketBasico(String fGen, String host, String estado, String nombre, String cel, String colId, String dispId, String itemNum) {
        try {
            // 1. Obtener configuración dinámica
            String[] config = obtenerConfiguracionItem(itemNum);
            String reqTypeId = config[0];
            String campoActivoDestino = config[1];

            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", SERVICE_DESK_ID);
            payload.put("requestTypeId", reqTypeId); // <--- ID Dinámico
            
            ObjectNode values = payload.putObject("requestFieldValues");

            values.put("summary", host);
            values.put("customfield_10180",  estado );
            values.put("customfield_10090", nombre);
            values.put("customfield_10091", cel);
            
            // Valores Dropdown (Deben coincidir exactamente con Jira)
            values.putObject("customfield_10176").put("value", "Incidente");
            values.putObject("customfield_10504").put("value", "Rural");
            values.putObject("customfield_10394").put("value", "Servicio de acceso a Internet");
            values.putArray("customfield_10002").add(3); // Urgencia

            // Agregar Activos (Assets)
            if (!colId.isEmpty()) {
                agregarActivo(values, "customfield_10170", WORKSPACE_ID, colId);
            }
            if (!dispId.isEmpty()) {
                // Aquí usamos el campo dinámico (10250, 10252, etc.)
                agregarActivo(values, campoActivoDestino, WORKSPACE_ID, dispId);
            }

            // Enviar Petición
            String jsonBody = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .header("X-ExperimentalApi", "opt-in")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                JsonNode node = mapper.readTree(response.body());
                String key = node.get("issueKey").asText();
                System.out.println("   ✅ Ticket Creado: " + key + " (Tipo: " + reqTypeId + ", Campo: " + campoActivoDestino + ")");
                return key;
            } else {
                System.err.println("   ❌ Error Creando Ticket (" + response.statusCode() + ")");
                System.err.println("   💀 DETALLE TÉCNICO: " + response.body());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =========================================================================
    // 📝 PASO 2: ACTUALIZAR CAMPOS (CORE API)
    // =========================================================================
    private static void actualizarCamposCompletos(String issueKey, String dep, String prov, String dist, String dir, 
                                                  String fGen, String fSol, String nomIE, String codMod, String codLoc,
                                                  String numTicket, String medio, String tipoInc, String tNoDisp) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");

            // Helpers para evitar null pointers y código repetido
            putIfNotEmpty(fields, "customfield_10355", dep);
            putIfNotEmpty(fields, "customfield_10356", prov);
            putIfNotEmpty(fields, "customfield_10357", dist);
            putIfNotEmpty(fields, "customfield_10358", dir);
            putIfNotEmpty(fields, "customfield_10321", fGen);
            putIfNotEmpty(fields, "customfield_10322", fSol);
            putIfNotEmpty(fields, "customfield_10359", nomIE);
            putIfNotEmpty(fields, "customfield_10169", codMod);
            putIfNotEmpty(fields, "customfield_10168", codLoc);
            putIfNotEmpty(fields, "customfield_10320", numTicket);
            putIfNotEmpty(fields, "customfield_10361", medio);
            putIfNotEmpty(fields, "customfield_10469", tipoInc);
            putIfNotEmpty(fields, "customfield_10178", tNoDisp);

            // Objetos fijos
            fields.putObject("customfield_10471").put("value", "Cliente");
            fields.putObject("customfield_10135").put("value", "Gabinete apagado");
            fields.put("customfield_10089", "Solución automática:\nServicio restablecido tras validación.");

            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                System.out.println("   ✨ Datos adicionales actualizados correctamente.");
            } else {
                System.err.println("   ⚠️ Error Actualizando (" + response.statusCode() + "): " + response.body());
            }

        } catch (Exception e) {
            System.err.println("Error en update: " + e.getMessage());
        }
    }

    // =========================================================================
    // 🛠️ UTILITARIOS
    // =========================================================================
    
    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        // Formato requerido por Jira Cloud Assets: "WorkspaceId:ObjectId"
        String assetRef = wsId + ":" + objId;
        ArrayNode assetArray = values.putArray(fieldId);
        assetArray.addObject().put("id", assetRef);
    }

    private static String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
    
    // Método helper para limpiar el código de actualización
    private static void putIfNotEmpty(ObjectNode fields, String key, String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(key, value);
        }
    }
}