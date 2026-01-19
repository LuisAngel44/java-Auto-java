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
        Dotenv dotenv = Dotenv.load();
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        try {
            System.out.println("🔍 1. Buscando WORKSPACE ID correcto...");
            
            // Petición para listar Workspaces y obtener el ID real
            HttpRequest reqWorkspace = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.atlassian.com/jsm/assets/v1/workspace"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respWS = client.send(reqWorkspace, HttpResponse.BodyHandlers.ofString());
            
            if (respWS.statusCode() != 200) {
                System.out.println("❌ Error obteniendo Workspace: " + respWS.statusCode());
                System.out.println(respWS.body());
                return;
            }

            JsonNode rootWS = mapper.readTree(respWS.body());
            JsonNode values = rootWS.path("values");
            
            if (values.isEmpty()) {
                System.out.println("⚠️ No tienes Workspaces de Assets asociados.");
                return;
            }

            // Tomamos el primer Workspace encontrado
            String workspaceId = values.get(0).path("workspaceId").asText();
            System.out.println("✅ WORKSPACE ID ENCONTRADO: " + workspaceId);

            // ---------------------------------------------------------
            
            System.out.println("\n🔍 2. Buscando objeto testigo (112675) usando ese ID...");
            
            // Usamos AQL genérico. Encodeamos la URL para evitar Error 400.
            String aql = "Name LIKE \"112675*\" OR label LIKE \"112675*\"";
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
                    JsonNode obj = objs.get(0);
                    System.out.println("🎉 ¡OBJETO ENCONTRADO!");
                    System.out.println("   Nombre: " + obj.path("label").asText());
                    System.out.println("   Key:    " + obj.path("objectKey").asText());
                    System.out.println("   ID:     " + obj.path("id").asText());
                    System.out.println("   --------------------------------");
                    System.out.println("   ATRIBUTOS (Aquí veremos dónde se esconde el número):");
                    
                    for (JsonNode attr : obj.path("attributes")) {
                         String attrId = attr.path("objectTypeAttributeId").asText();
                         if (attr.path("objectAttributeValues").size() > 0) {
                             String val = attr.path("objectAttributeValues").get(0).path("value").asText();
                             String display = attr.path("objectAttributeValues").get(0).path("displayValue").asText();
                             System.out.println("   👉 ID Atributo [" + attrId + "] Valor: " + val + " (" + display + ")");
                         }
                    }
                } else {
                    System.out.println("⚠️ No se encontraron objetos con ese número.");
                }
            } else {
                System.out.println("❌ Error buscando objeto: " + respObj.statusCode());
                System.out.println(respObj.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}