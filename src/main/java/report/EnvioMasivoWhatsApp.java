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
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class EnvioMasivoWhatsApp {

    public static void main(String[] args) {
        // 1. CONFIGURACIÓN DEL DRIVER (SELENIUM 4.11+ LO HACE AUTOMÁTICO)
        // Ya no necesitas System.setProperty si tienes Chrome instalado en su ruta por defecto.
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        DataFormatter formatter = new DataFormatter(); // Para leer celdas como texto tal cual se ven

        // RUTA DE TU EXCEL
        String rutaExcel = "src/main/resources/escribir.xlsx"; 

        try (FileInputStream fis = new FileInputStream(new File(rutaExcel));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            driver.get("https://web.whatsapp.com");
            System.out.println("Por favor, escanea el código QR en el navegador...");
            
            // Espera a que cargue la interfaz principal de WhatsApp
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@id='pane-side']")));

            // Recorrer filas (asumiendo que la fila 0 es el encabezado)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Thread.sleep(3000);
                // Lectura de columnas (A=0, B=1, C=2, etc.)
                String codigoLocal = formatter.formatCellValue(row.getCell(0));
                String nombreIE    = formatter.formatCellValue(row.getCell(1));
                String numTelefono = formatter.formatCellValue(row.getCell(2)).replaceAll("[^0-9]", "");
                String nombrecontacto    = formatter.formatCellValue(row.getCell(3));

                if (numTelefono.isEmpty()) continue;

                // MENSAJE PERSONALIZADO
                String mensaje = "Hola, le saluda Luis, residente de MINEDU. "
                				+"Estimad@ "+ nombrecontacto
                               + " Nuestro sistema detectó que los equipos de Bitel en la IE " + nombreIE 
                               + " (Código Local: " + codigoLocal + ") se encuentran APAGADOS. "
                               + "¿Podrían confirmarnos si es por falta de energía eléctrica o algún inconveniente?";

                try {
                    String url = "https://web.whatsapp.com/send?phone=51" + numTelefono + 
                                 "&text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8.toString());
                    
                    driver.get(url);
                    System.out.println(url);

                    // Esperar a que el botón de enviar aparezca y sea clicable
                    WebElement btnEnviar = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//span[@data-icon='send'] | //button[@aria-label='Enviar']")
                    ));

                    btnEnviar.click();
                    
                    // Pausa técnica para evitar bloqueos y asegurar el envío
                    Thread.sleep(5000); 
                    System.out.println("✅ Enviado a: " + nombreIE + " [" + numTelefono + "]");

                } catch (Exception e) {
                    System.out.println("❌ Error con la IE " + nombreIE + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error crítico: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("--- Proceso finalizado ---");
        }
    }
}