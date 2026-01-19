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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Jira_Create_From_Excel {
	//CREAMIS VARIASBLES 
	
	
	static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    /*el programa lee excel y crea ticke atalizando postreiormente 
     * 
     * */
    
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String auth = email + ":" + token;
        encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        /*la identificacion del lugar de trabajo*/
        String workspaceId = "01cf423f-729d-4ecc-9da9-3df244069bb5";

        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("carga_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> INICIANDO CARGA MASIVA (FULL CAMPOS) <<<");
            int rowCount = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 
                rowCount++;
                
                // --- LECTURA DE COLUMNAS BÁSICAS (A - L) ---
                String host = getCellValue(row, 0);
                String estado = getCellValue(row, 1);
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

                // --- NUEVAS COLUMNAS (M - S) ---
                String nombreIE = getCellValue(row, 12);   // Col M
                String codModular = getCellValue(row, 13); // Col N
                String codLocal = getCellValue(row, 14);   // Col O
                String numTicketRef = getCellValue(row, 15); // Col P
                String medioTrans = getCellValue(row, 16); // Col Q
                String tipoIncidencia = getCellValue(row, 17); // Col R
                String tiempoNoDisp = getCellValue(row, 18); // Col S

                if (host.isEmpty()) break;

                System.out.println("\nProcesando Fila #" + row.getRowNum() + ": " + host);

                // PASO 1: CREAR TICKET (Básico)
                String issueKey = crearTicketBasico(host, estado, contactoNom, contactoCel, workspaceId, idColegio, idDispositivo);

                // PASO 2: ACTUALIZAR TODOS LOS CAMPOS (Core API)
                if (issueKey != null) {
                    actualizarCamposCompletos(issueKey, dep, prov, dist, direccion, fechaGen, fechaSol,
                            nombreIE, codModular, codLocal,issueKey , medioTrans, tipoIncidencia, tiempoNoDisp);
                    
                    // Guardar resultado en Excel (Col T = índice 19)
                    row.createCell(19).setCellValue(issueKey);
                }

                Thread.sleep(500); 
            }
            
            // Guardar Excel final
            try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream("resultado_carga_full.xlsx")) {
                workbook.write(fileOut);
                System.out.println("✅ Reporte 'resultado_carga_full.xlsx' generado.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- PASO 1: CREAR TICKET (Igual que antes) ---
    private static String crearTicketBasico(String host, String estado, String nombre, String cel, String wsId, String colId, String dispId) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", "34");
            payload.put("requestTypeId", "128");
            ObjectNode values = payload.putObject("requestFieldValues");
            
            values.put("summary", "Alerta NOC: Incidente - " + host);
            values.put("customfield_10180", "Host: " + host + "\nState: " + estado);//variable alarma gira
            values.put("customfield_10090", nombre);
            values.put("customfield_10091", cel);
            values.putObject("customfield_10176").put("value", "Incidente");
            values.putObject("customfield_10504").put("value", "Rural");
            values.putObject("customfield_10394").put("value", "Servicio de acceso a Internet");
            values.putArray("customfield_10002").add(3);

            if (!colId.isEmpty()) agregarActivo(values, "customfield_10170", wsId, colId);
            if (!dispId.isEmpty()) agregarActivo(values, "customfield_10252", wsId, dispId);

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
                System.out.print("   ✅ [Paso 1] Creado: " + key);
                return key;
            } else {
                System.out.println("   ❌ Error Creación: " + response.statusCode());
                return null;
            }
        } catch (Exception e) { return null; }
    }

    // --- PASO 2: ACTUALIZAR (¡AQUÍ ESTÁ LA MAGIA!) ---
    // --- PASO 2: ACTUALIZAR (CORREGIDO PARA TEXTO SIMPLE) ---
    private static void actualizarCamposCompletos(String issueKey, String dep, String prov, String dist, String dir, 
                                                  String fGen, String fSol, String nomIE, String codMod, String codLoc,
                                                  String numTicket, String medio, String tipoInc, String tNoDisp) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");

            // --- UBICACIÓN ---
            fields.put("customfield_10355", dep);
            fields.put("customfield_10356", prov);
            fields.put("customfield_10357", dist);
            fields.put("customfield_10358", dir);

            // --- FECHAS ---
            fields.put("customfield_10321", fGen);
            fields.put("customfield_10322", fSol);

            // --- CAMPOS DROPDOWN (OBJETOS) ---
            // Estos SÍ requieren estructura de objeto (según pruebas anteriores exitosas)
            fields.putObject("customfield_10471").put("value", "Cliente");
            fields.putObject("customfield_10135").put("value", "Gabinete apagado");
            
            // --- CAMPOS CORREGIDOS A TEXTO SIMPLE ---
            // El error "debe ser una cadena de texto" nos obliga a enviarlos así:
            if (!medio.isEmpty()) fields.put("customfield_10361", medio);
            if (!tipoInc.isEmpty()) fields.put("customfield_10469", tipoInc);

            // --- OTROS CAMPOS DE TEXTO ---
            fields.put("customfield_10359", nomIE);      // Nombre IE
            fields.put("customfield_10169", codMod);     // Cod Modular
            fields.put("customfield_10168", codLoc);     // Cod Local
            fields.put("customfield_10320", numTicket);  // Num Ticket Ref
            
            // Tiempos
            fields.put("customfield_10178", tNoDisp); 

            // --- SOLUCIÓN ---
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
                System.out.println(" -> ✅ [Paso 2] Datos FULL inyectados correctamente.");
            } else {
                System.out.println("\n   ⚠️ Error Actualización: " + response.statusCode());
                System.out.println("   " + response.body());
            }

        } catch (Exception e) {
            System.out.println("Error update: " + e.getMessage());
        }
    }

    // --- UTILS ---
    private static String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        String assetRef = wsId + ":" + objId;
        ArrayNode assetArray = values.putArray(fieldId);
        assetArray.addObject().put("id", assetRef);
    }
}