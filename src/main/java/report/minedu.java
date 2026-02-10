package report;
import org.openqa.selenium.By;
//Importar estas clases
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import controller.Controller;
import controller.ExcelController;
import controller.WebController;

import java.time.Duration;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.io.FileHandler;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import org.openqa.selenium.chrome.ChromeOptions; // Importante
import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import java.io.File;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import org.openqa.selenium.JavascriptExecutor;


public class minedu {

    public static void main(String[] args) throws InterruptedException {

        Controller controller = new Controller();
        int[] columnasDeseadas = {0, 1, 2, 3, 4, 5};
        String RutaExcelFinal = "src/main/resources/";
        String ITEM = controller.ElegirITEM();
        String NombreExcel = "Excel_" + ITEM + ".xlsx";
        int n = 0;
        
        // ... Variables de encabezados ...
        String codigo_local, CID, NomLLEE, Departamento, Provincia, Distrito;
        boolean menuReducido = false;

        List<List<String>> misDatos = ExcelController.leerColumnasEspecificas(RutaExcelFinal + NombreExcel, columnasDeseadas);
        String fechaini = controller.ElegirFechaIni();
        String fechafin = controller.ElegirFechaFinal();

        // Inicializamos el driver FUERA del bucle y del try principal
        WebController webController = new WebController();
        WebDriver driver = webController.GraficaAnchoBancha();

        // -------------------------FOR PRINCIPAL---------------------------------------
        for (List<String> fila : misDatos) {
            
            // EL TRY DEBE EMPEZAR AQUÍ PARA QUE EL BUCLE SEA RESILIENTE
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

                codigo_local = fila.get(0);
                CID = fila.get(1);
                NomLLEE = fila.get(2);
                Departamento = fila.get(3);
                Provincia = fila.get(4);
                Distrito = fila.get(5);
                
                n++; // Contador

                if (codigo_local.matches("[\\d\\.]+")) {
                    System.out.println("--- Procesando Item " + n + ": " + codigo_local + " ---");
                    
                    String url = WebController.ElegirURL(fechaini, fechafin, ITEM, codigo_local, CID, driver);
                    
                    driver.get(url);
                    Thread.sleep(3000); // Espera carga inicial

                    // Retraer menú solo la primera vez
                    if (!menuReducido) {
                        WebController.colapsarMenuGrafana(driver);
                        menuReducido = true;
                    }

                    // 1. Capturar datos numéricos (Try/Catch interno en WebController, no detendrá el flujo)
                    Map<String, String> datosCapturados = WebController.capturarDatosGrafica(driver);

                    // 2. Guardar Excel
                    ExcelController.escribirExcelResultados(Departamento, Provincia, Distrito, ITEM, codigo_local, NomLLEE, CID, RutaExcelFinal + "excel/ITEM" + ITEM + "/Resultados_Grafana_" + ITEM + ".xlsx", datosCapturados);

                    // 3. TOMAR CAPTURA (Aquí usamos el nuevo método blindado)
                    WebController.TomadeCapturaGurardado(driver, codigo_local, ITEM);

                } else {
                    System.out.println("Fila de encabezado o vacía detectada, saltando...");
                }

            } catch (Exception e) {
                // AQUÍ CAPTURAMOS CUALQUIER ERROR DE ESTA ITERACIÓN ESPECÍFICA
                System.out.println("❌ ERROR PROCESANDO EL CÓDIGO LOCAL: " + fila.get(0));
                e.printStackTrace();
                System.out.println("🔄 Continuando con el siguiente elemento...");
                
                // Opcional: Tomar una captura de pantalla del error si quieres ver qué pasó
                // WebController.tomarCapturaElemento(driver, "ERROR_CRITICO_" + fila.get(0), ITEM);
            }
        } // FIN DEL FOR

        // Cerrar driver al final de todo
        System.out.println("Proceso finalizado.");
        // driver.quit(); 
    }
}