package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class EnvioMasivoWhatsApp {

    public static void main(String[] args) {
        // 1. CONFIGURACIÓN DEL DRIVER
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--remote-allow-origins=*"); // Ayuda a evitar ciertos errores de conexión en versiones nuevas

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        DataFormatter formatter = new DataFormatter(); // Para leer celdas como texto

        // RUTA DE TU EXCEL (Asegúrate que la ruta sea correcta)
        String rutaExcel = "src/main/resources/escribir.xlsx"; 

        try (FileInputStream fis = new FileInputStream(new File(rutaExcel));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            driver.get("https://web.whatsapp.com");
            System.out.println("⏳ Por favor, escanea el código QR en el navegador...");
            
            // Espera a que cargue la lista de chats (indicador de que ya iniciaste sesión)
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@id='pane-side']")));
            System.out.println("✅ Sesión iniciada. Comenzando envío...");

            // Recorrer filas (asumiendo que la fila 0 es el encabezado, empezamos en 1)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // Lectura de columnas
                String codigoLocal = formatter.formatCellValue(row.getCell(0));
                String nombreIE    = formatter.formatCellValue(row.getCell(1));
                String numTelefono = formatter.formatCellValue(row.getCell(2)).replaceAll("[^0-9]", "");
                String nombrecontacto = formatter.formatCellValue(row.getCell(3));

                // Validación básica
                if (numTelefono.isEmpty()) {
                    System.out.println("⚠️ Fila " + (i+1) + " saltada: Sin número de teléfono.");
                    continue;
                }

                // --- MENSAJE PERSONALIZADO INTEGRADO ---
                String mensaje = "Hola, le saluda Luis, Residente del proyecto Bitel - MINEDU.\n\n"
                        + "Estimado(a) " + nombrecontacto + ",\n"
                        + "Nuestro sistema de monitoreo ha detectado una caída de tráfico en la IE " + nombreIE 
                        + " (Código Local: " + codigoLocal + ").\n\n"
                        + "¿Podría confirmarnos si hay corte de energía eléctrica en la zona, o si los equipos se encuentran apagados? Quedo atento, gracias.";
                // ---------------------------------------

                try {
                    // Codificamos el mensaje para URL
                    String url = "https://web.whatsapp.com/send?phone=51" + numTelefono + 
                                 "&text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
                    
                    driver.get(url);
                    
                    // Esperar a que el botón de enviar aparezca
                    // Nota: WhatsApp cambia el XPath a veces. Este busca el botón por el ícono o el atributo label.
                    WebElement btnEnviar = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//span[@data-icon='send'] | //button[@aria-label='Enviar'] | //span[@data-icon='send-alt']")
                    ));

                    btnEnviar.click();
                    
                    // Pausa de seguridad para asegurar que el mensaje salga antes de cambiar de página
                    Thread.sleep(6000); 
                    System.out.println("✅ Enviado a: " + nombreIE + " (" + nombrecontacto + ")");

                } catch (Exception e) {
                    System.out.println("❌ Error enviando a " + nombreIE + ": " + e.getMessage());
                    // Opcional: Tomar captura de pantalla aquí si falla
                }
            }

        } catch (Exception e) {
            System.err.println("Error crítico abriendo el Excel o el Driver: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("--- Proceso finalizado ---");
            // driver.quit(); // Descomenta si quieres que el navegador se cierre solo al acabar
        }
    }
}