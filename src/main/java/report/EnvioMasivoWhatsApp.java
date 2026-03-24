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
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class EnvioMasivoWhatsApp {

    public static void main(String[] args) {
        // 1. CONFIGURACIÓN DE CHROME
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--remote-allow-origins=*");
        
        WebDriver driver = new ChromeDriver(options);
        DataFormatter formatter = new DataFormatter();
        
        // Espera para elementos de la interfaz (envío)
        WebDriverWait waitEnvio = new WebDriverWait(driver, Duration.ofSeconds(25));

        // LISTAS PARA REPORTE FINAL
        List<String> exitosos = new ArrayList<>();
        List<String> inexistentes = new ArrayList<>();
        List<String> fallidosConError = new ArrayList<>();

        // RUTA DEL EXCEL
        String rutaExcel = "src/main/resources/escribir.xlsx"; 

        try (FileInputStream fis = new FileInputStream(new File(rutaExcel));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            // 2. INICIO DE SESIÓN CON PAUSA MANUAL
            driver.get("https://web.whatsapp.com");
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚨 ATENCIÓN REQUERIDA - MODO MANUAL 🚨");
            System.out.println("1. Se ha abierto el navegador. Escanea el código QR con calma.");
            System.out.println("2. Espera a que carguen tus chats completamente.");
            System.out.println("👉 3. Cuando estés listo, HAZ CLIC AQUÍ Y PRESIONA 'ENTER'...");
            System.out.println("=".repeat(60));

            Scanner scanner = new Scanner(System.in);
            scanner.nextLine(); 

            System.out.println("✅ Confirmado. Iniciando el envío masivo...");

            // 3. RECORRIDO DE FILAS
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // Lectura de datos
                String codigoLocal = formatter.formatCellValue(row.getCell(0));
                String nombreIE    = formatter.formatCellValue(row.getCell(1));
                String numTelefono = formatter.formatCellValue(row.getCell(2)).replaceAll("[^0-9]", "");
                String nombreContacto = formatter.formatCellValue(row.getCell(3));

                if (numTelefono.isEmpty()) {
                    fallidosConError.add("Fila " + (i + 1) + ": Sin número de teléfono.");
                    continue;
                }

                // MENSAJE PERSONALIZADO
                String mensaje = "Hola, le saluda Luis, Residente del proyecto Bitel - MINEDU.\n\n"
                        + "Estimado(a) *" + nombreContacto + "*,\n\n"
                        + "Nuestro sistema de monitoreo indica que el equipo de comunicaciones de la IE *" + nombreIE + "* (Código Local: " + codigoLocal + ") se encuentra *desconectado o sin servicio desde hace más de 7 días*.\n\n"
                        + "Para gestionar correctamente su caso, ¿podría confirmarnos si la institución se encuentra cerrada actualmente (por vacaciones o mantenimiento) o si los equipos fueron apagados por precaución?\n\n"
                        + "Si se encuentran laborando normalmente, le agradecería verificar si el equipo principal cuenta con energía eléctrica y está encendido. Quedo atento a su amable respuesta, muchas gracias.";
                try {
                    System.out.println("\n-------------------------------------------");
                    System.out.println("🚀 Procesando: " + nombreIE + " (" + numTelefono + ")");
                    
                    String url = "https://web.whatsapp.com/send?phone=51" + numTelefono + 
                                 "&text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
                    
                    driver.get(url);

                    // 4. DETECCIÓN CORREGIDA: Usamos un solo XPath con "OR" (|) interno
                    try {
                        // Este XPath busca: El botón enviar (icono) O el botón enviar (texto) O el mensaje de error
                        String xpathCompleto = "//span[@data-icon='send'] | //button[@aria-label='Enviar'] | //div[contains(text(), 'inválido')]";
                        
                        WebElement resultado = waitEnvio.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpathCompleto)));

                        String textoElemento = resultado.getText();

                        // Verificamos qué encontramos
                        if (textoElemento.contains("inválido")) {
                            System.out.println("❌ ERROR: El número no existe en WhatsApp.");
                            inexistentes.add(nombreIE + " (" + numTelefono + ")");
                        } else {
                            // Si NO dice inválido, es el botón de enviar
                            resultado.click();
                            Thread.sleep(5000); // Esperar que salga el mensaje
                            System.out.println("✅ MENSAJE ENVIADO CON ÉXITO.");
                            exitosos.add(nombreIE);
                        }

                    } catch (Exception e) {
                        System.out.println("⚠️ No se pudo determinar el estado del envío (Posible carga lenta).");
                        fallidosConError.add(nombreIE + ": Tiempo de espera agotado.");
                    }

                } catch (Exception e) {
                    System.out.println("❌ Error crítico en fila " + (i + 1) + ": " + e.getMessage());
                    fallidosConError.add(nombreIE + ": " + e.getMessage());
                }
                
                // Pausa de seguridad entre colegios
                Thread.sleep(8000);
            }

            // 5. RESUMEN FINAL POR CONSOLA
            System.out.println("\n\n" + "=".repeat(60));
            System.out.println("         📊 REPORTE FINAL DE GESTIÓN NOC");
            System.out.println("=".repeat(60));
            System.out.println("✅ ENVÍOS EXITOSOS: " + exitosos.size());
            System.out.println("🚫 NÚMEROS QUE NO EXISTEN (Llamar directo): " + inexistentes.size());
            System.out.println("⚠️ OTROS ERRORES: " + fallidosConError.size());
            System.out.println("=".repeat(60));

            if (!inexistentes.isEmpty()) {
                System.out.println("\n❌ NÚMEROS NO EXISTENTES EN WHATSAPP:");
                inexistentes.forEach(item -> System.out.println(" - " + item));
            }

            if (!fallidosConError.isEmpty()) {
                System.out.println("\n⚠️ DETALLE DE OTROS ERRORES (Revisar conexión):");
                fallidosConError.forEach(item -> System.out.println(" - " + item));
            }
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println("🚨 ERROR AL ABRIR EL EXCEL: " + e.getMessage());
        } finally {
            System.out.println("\n[FIN] Proceso terminado. Puedes revisar el resumen arriba.");
        }
    }
}