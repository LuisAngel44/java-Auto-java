package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Jira_Asset_Inspector {

    public static void main(String[] args) {
        // Cargar configuración
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        String email = dotenv.get("JIRA_EMAIL");
        String token = dotenv.get("JIRA_TOKEN");

        if (email == null || token == null) {
            System.out.println("❌ Error: Falta configurar JIRA_EMAIL o JIRA_TOKEN en el archivo .env");
            return;
        }

        // Codificar autenticación Basic Auth
        String encodedAuth = Base64.getEncoder().encodeToString((email.trim() + ":" + token.trim()).getBytes());

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        try {
            System.out.println("🔍 1. Buscando WORKSPACE ID correcto...");

            // 1. Obtener el Workspace ID de Assets
            HttpRequest reqWorkspace = HttpRequest.newBuilder()
            		.uri(URI.create("https://api.atlassian.com/jsm/assets/v1/workspace"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respWS = client.send(reqWorkspace, HttpResponse.BodyHandlers.ofString());

            if (respWS.statusCode() != 200) {
                System.out.println("❌ Error obteniendo Workspace: " + respWS.statusCode());
                System.out.println("Respuesta: " + respWS.body());
                return;
            }

            JsonNode rootWS = mapper.readTree(respWS.body());
            JsonNode values = rootWS.path("values");

            if (values.isEmpty()) {
                System.out.println("⚠️ No tienes Workspaces de Assets asociados a esta cuenta.");
                return;
            }

            String workspaceId = values.get(0).path("workspaceId").asText();
            System.out.println("✅ WORKSPACE ID ENCONTRADO: " + workspaceId);

            // ---------------------------------------------------------

            System.out.println("\n🔍 2. Buscando objeto testigo (112675)...");

            // AQL: Busca objetos donde el Nombre o la Etiqueta contengan ese número
            String aql = "Name LIKE \"112675*\" OR label LIKE \"112675*\"";
            
            // Construir URL encodeada correctamente
            String url = "https://api.atlassian.com/jsm/assets/workspace/" + workspaceId + 
                         "/v1/object/aql?ql=" + URLEncoder.encode(aql, StandardCharsets.UTF_8);

            HttpRequest reqObj = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respObj = client.send(reqObj, HttpResponse.BodyHandlers.ofString());

            if (respObj.statusCode() == 200) {
                JsonNode rootObj = mapper.readTree(respObj.body());
                JsonNode objs = rootObj.path("values");

                if (objs.size() > 0) {
                    JsonNode obj = objs.get(0); // Tomamos el primer resultado
                    System.out.println("🎉 ¡OBJETO ENCONTRADO!");
                    System.out.println("   Nombre/Label: " + obj.path("label").asText());
                    System.out.println("   Key:          " + obj.path("objectKey").asText());
                    System.out.println("   ID Interno:   " + obj.path("id").asText());
                    System.out.println("   --------------------------------");
                    System.out.println("   ATRIBUTOS DEL OBJETO:");

                    for (JsonNode attr : obj.path("attributes")) {
                        String attrId = attr.path("objectTypeAttributeId").asText();
                        // Verificamos si tiene valores
                        if (attr.path("objectAttributeValues").size() > 0) {
                            JsonNode firstVal = attr.path("objectAttributeValues").get(0);
                            String val = firstVal.path("value").asText();
                            String display = firstVal.path("displayValue").asText();
                            
                            System.out.println("   👉 ID Atributo [" + attrId + "] | Valor: " + val + " | Display: " + display);
                        }
                    }
                } else {
                    System.out.println("⚠️ La búsqueda AQL funcionó, pero no devolvió resultados para '112675'.");
                    System.out.println("   Prueba cambiar el AQL a: objectType = 'TuTipoDeObjeto'");
                }
            } else {
                System.out.println("❌ Error buscando objeto: " + respObj.statusCode());
                System.out.println("Cuerpo: " + respObj.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}