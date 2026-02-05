package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SupremoJiraFinalJava17 {

    public record TicketData(
        String host, String estado, String idColegio, String idDispositivo,
        String contactoNom, String contactoCel, String dep, String prov,
        String dist, String direccion, String fechaGen, String fechaSol,
        String nombreIE, String codModular, String codLocal, String numTicketRef,
        String medioTrans, String tipoIncidencia, String tiempoNoDisp,
        String itemSeleccionado, String solucionTexto, String rutasImagenes,
        String area, String causaRaiz, String categoriaServicio
    ) {}

    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    static String jiraUrl;
    static String encodedAuth;
    static final HttpClient client = HttpClient.newHttpClient();
    static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = Optional.ofNullable(dotenv.get("JIRA_URL")).orElse("").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL");
        String token = dotenv.get("JIRA_TOKEN");

        if (email == null || token == null) return;
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        try (var file = new FileInputStream(new File("carga_tickets.xlsx"));
             var workbook = new XSSFWorkbook(file)) {

            var sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🚀 INICIANDO CARGA MASIVA - NOC BITEL <<<");

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                TicketData data = mapRowToRecord(row);
                if (data.host().isEmpty()) break;

                System.out.printf("🔄 Fila #%d | Creando MSP para: %s...%n", row.getRowNum(), data.host().substring(0, Math.min(data.host().length(), 20)));

                // PASO 1: Crear ticket
                String issueKey = crearTicketBasico(data);

                if (issueKey != null) {
                    // PASO 2: Subir imágenes
                    List<String> attachmentIds = data.rutasImagenes().isBlank() ? 
                            Collections.emptyList() : procesarAdjuntos(issueKey, data.rutasImagenes());

                    // PASO 3: ACTUALIZACIÓN COMPLETA (Aquí está el detalle)
                    actualizarDatosCompletos(issueKey, data, attachmentIds);

                    row.createCell(20).setCellValue(issueKey);
                    System.out.println("   ✅ MSP Creado y Actualizado: " + issueKey);
                } else {
                    row.createCell(20).setCellValue("ERROR_CREACION");
                }
                Thread.sleep(500); 
            }

            try (var fileOut = new FileOutputStream("resultado_carga_final.xlsx")) {
                workbook.write(fileOut);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static String crearTicketBasico(TicketData d) throws Exception {
        var config = switch (d.itemSeleccionado()) {
            case "1" -> new String[]{"126", "customfield_10250"};
            case "2" -> new String[]{"127", "customfield_10251"};
            case "4" -> new String[]{"129", "customfield_10253"};
            default  -> new String[]{"128", "customfield_10252"};
        };

        ObjectNode payload = mapper.createObjectNode();
        payload.put("serviceDeskId", SERVICE_DESK_ID);
        payload.put("requestTypeId", config[0]);

        ObjectNode v = payload.putObject("requestFieldValues");
        v.put("summary", d.host().replace("\n", " ").trim());
        putIfVal(v, "customfield_10180", d.estado());
        putIfVal(v, "customfield_10090", d.contactoNom());
        putIfVal(v, "customfield_10091", d.contactoCel());
        v.putObject("customfield_10176").put("value", "Incidente");

        if (!d.idColegio().isBlank()) agregarActivo(v, "customfield_10170", d.idColegio());
        if (!d.idDispositivo().isBlank()) agregarActivo(v, config[1], d.idDispositivo());

        var request = HttpRequest.newBuilder()
                .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .header("X-ExperimentalApi", "opt-in")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 201 ? mapper.readTree(resp.body()).get("issueKey").asText() : null;
    }

    private static void actualizarDatosCompletos(String key, TicketData d, List<String> attIds) throws Exception {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode f = payload.putObject("fields");

        // Datos técnicos y ubicación
        putIfVal(f, "customfield_10355", d.dep());
        putIfVal(f, "customfield_10356", d.prov());
        putIfVal(f, "customfield_10357", d.dist());
        putIfVal(f, "customfield_10358", d.direccion());
        putIfVal(f, "customfield_10321", d.fechaGen());
        putIfVal(f, "customfield_10322", d.fechaSol());
        putIfVal(f, "customfield_10359", d.nombreIE());
        putIfVal(f, "customfield_10169", d.codModular());
        putIfVal(f, "customfield_10168", d.codLocal());
        putIfVal(f, "customfield_10320", d.numTicketRef());
        putIfVal(f, "customfield_10361", d.medioTrans());
        putIfVal(f, "customfield_10469", d.tipoIncidencia());
        putIfVal(f, "customfield_10178", d.tiempoNoDisp());

        // Combos de selección (Dropdowns)
        if (!d.area().isBlank()) f.putObject("customfield_10504").put("value", d.area());
        if (!d.causaRaiz().isBlank()) f.putObject("customfield_10135").put("value", d.causaRaiz());
        if (!d.categoriaServicio().isBlank()) f.putObject("customfield_10394").put("value", d.categoriaServicio());

        // Solución con imágenes (ADF)
        if (!d.solucionTexto().isBlank() || !attIds.isEmpty()) {
            f.set("customfield_10089", construirCuerpoADF(d.solucionTexto(), attIds));
        }

        var request = HttpRequest.newBuilder()
                .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + key))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() != 204) {
            System.err.println("   ⚠️ Error actualizando detalles: " + resp.body());
        }
    }

    private static TicketData mapRowToRecord(Row row) {
        return new TicketData(
            getVal(row, 0), getVal(row, 1), getVal(row, 2), getVal(row, 3),
            getVal(row, 4), getVal(row, 5), getVal(row, 6), getVal(row, 7),
            getVal(row, 8), getVal(row, 9), getVal(row, 10), getVal(row, 11),
            getVal(row, 12), getVal(row, 13), getVal(row, 14), getVal(row, 15),
            getVal(row, 16), getVal(row, 17), getVal(row, 18), getVal(row, 19),
            getVal(row, 21), getVal(row, 22), getVal(row, 23), getVal(row, 24), getVal(row, 25)
        );
    }

    // --- MÉTODOS DE APOYO ---

    private static JsonNode construirCuerpoADF(String texto, List<String> ids) {
        ObjectNode doc = mapper.createObjectNode().put("type", "doc").put("version", 1);
        ArrayNode content = doc.putArray("content");
        if (!texto.isBlank()) {
            content.addObject().put("type", "paragraph")
                   .putArray("content").addObject().put("type", "text").put("text", texto);
        }
        for (String id : ids) {
            var media = content.addObject().put("type", "mediaSingle");
            media.putObject("attrs").put("layout", "align-start").put("width", 500);
            media.putArray("content").addObject().put("type", "media")
                 .putObject("attrs").put("id", id).put("type", "file").put("collection", "");
        }
        return doc;
    }

    private static List<String> procesarAdjuntos(String key, String rutas) {
        return Arrays.stream(rutas.split(","))
                .map(String::trim).filter(p -> !p.isBlank())
                .map(path -> subirArchivo(key, path)).filter(Objects::nonNull).toList();
    }

    private static String subirArchivo(String issueKey, String ruta) {
        try {
            var path = Path.of(ruta);
            if (!Files.exists(path)) return null;
            String boundary = "---" + UUID.randomUUID();
            byte[] fileContent = Files.readAllBytes(path);
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + path.getFileName() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(head.getBytes()); os.write(fileContent); os.write(tail.getBytes());
            var request = HttpRequest.newBuilder().uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/attachments"))
                    .header("Authorization", "Basic " + encodedAuth).header("X-Atlassian-Token", "no-check")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(os.toByteArray())).build();
            var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? mapper.readTree(resp.body()).get(0).get("id").asText() : null;
        } catch (Exception e) { return null; }
    }

    private static void agregarActivo(ObjectNode v, String f, String id) {
        v.putArray(f).addObject().put("id", WORKSPACE_ID + ":" + id);
    }

    private static String getVal(Row r, int i) {
        Cell c = r.getCell(i);
        return (c == null) ? "" : new DataFormatter().formatCellValue(c).trim();
    }

    private static void putIfVal(ObjectNode f, String k, String v) {
        if (v != null && !v.isBlank()) f.put(k, v);
    }
}