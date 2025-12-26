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

//		
	
		Controller controller=new Controller();
	    int[] columnasDeseadas = {0,1,2,3,4,5,6,7,8,9,10};
	    String RutaExcelFinal = "src/main/resources/";
	    String NombreExcel="Excel_w_ITEM_"+controller.ElegirITEM()+"xlsx";
        int n = 0;
        System.out.println(NombreExcel);
	    //Elegir el nombre del excel por ITEM
	    
	    
	    ///VARIABLE DE LOS ENCABEZADOS DEL EXCEL 
	    // --- C MO USAR LOS DATOS EN EL BUCLE ---
        //
        String Numero;
        //
        String ITEM;
        String codigo_local;
        String CID;
    
	    
        String NomLLEE;
        String Regio;
        String Provincia;
        String Distrito;
     
        String Peak_Receive;
        String Minimum_Receive;
        String Average_Receive;
        String Peak_Transmit;
        String Minimum_Transmit;
        String Average_Transmit;
        String Ancho_de_banda;
       
        
               
        
	  //leer excel los pendientes   
        List<List<String>> misDatos = ExcelController.leerColumnasEspecificas(RutaExcelFinal+NombreExcel, columnasDeseadas);
     //escoger el link para las graficas 
        String Linkgraficas=controller.ElegirFecha();
     
        try {
        	WebController  webController=new WebController();
        	WebDriver driver = webController.GraficaAnchoBancha();           
           // -------------------------FOR---------------------------------------
            for (List<String> fila : misDatos) {
               
            	WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
                codigo_local = fila.get(0);
                Peak_Receive = fila.get(1);        
                Minimum_Receive=fila.get(2);
                Average_Receive=fila.get(3);
                Peak_Transmit=fila.get(4);
                Minimum_Transmit=fila.get(5);
                Average_Transmit=fila.get(6);
                Ancho_de_banda=fila.get(7);
                CID=fila.get(8);
                int intentosOutbound = 0;
                n = n + 1; // Ahora funcionar  sin errores
                System.out.println("Procesando: codigo local:  " + codigo_local + " Peak_Receive: " + Peak_Receive+" Minimum Receive: "+Minimum_Receive+" Average Receive: "+Average_Receive+" Peak_Transmit: "+Peak_Transmit+" CID: "+CID);
                	
                // AQUI CONTINUAR A EL C DIGO DE LA GR FICA...
                //https://181.176.39.44/grafana/d/device-detail/device-detail?var-codigo_local="+codigo_local+"&orgId=6&from=2025-11-01T05:00:00.000Z&to=2025-12-01T04:59:59.000Z&timezone=browser&var-exportpdf=&var-check_user=3&var-item=$__all&var-hostgroup=minedu&var-departamento=$__all&var-provincia=$__all&var-distrito=$__all&var-centro_poblado=$__all&var-centro_educativo=$__all&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_"+CID+"_SRX300&var-max_bandwidth=100&var-interface=$__all&var-rp=six_months&var-service=$__all&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F5%26var-interface%3Dge-0%2F0%2F7&refresh=10m&viewPanel=panel-145
                driver.get("https://181.176.39.44/grafana/d/device-detail/device-detail?var-codigo_local="+codigo_local+"&orgId=6&from=2025-11-01T05:00:00.000Z&to=2025-12-01T04:59:59.000Z&timezone=browser&var-exportpdf=&var-check_user=2&var-item=$__all&var-hostgroup=minedu&var-departamento=$__all&var-provincia=$__all&var-distrito=$__all&var-centro_poblado=$__all&var-centro_educativo=$__all&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_"+CID+"_SRX300&var-max_bandwidth=30&var-interface=$__all&var-rp=six_months&var-service=$__all&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F7&refresh=10m&viewPanel=panel-145");
                //https://181.176.39.44/grafana/d/device-detail/device-detail?var-codigo_local=354949&orgId=6&from=2025-11-01T05:00:00.000Z&to=2025-12-01T04:59:59.000Z&timezone=browser&var-exportpdf=&var-check_user=2&var-item=$__all&var-hostgroup=minedu&var-departamento=$__all&var-provincia=$__all&var-distrito=$__all&var-centro_poblado=$__all&var-centro_educativo=$__all&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_111883_SRX300&var-max_bandwidth=30&var-interface=$__all&var-rp=six_months&var-service=$__all&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F7&refresh=10m&viewPanel=panel-145
                Thread.sleep(5000); 
                //
                System.out.println("Reduce el menu vertical");
                //marcar y desmarcar 
                System.out.println("se empieza a ver las graficas ");
                // 1. Localizar los elementos (ajusta los selectores a tu p gina)
                
                
             // 2. Espera OBLIGATORIA para que se dibuje el SVG.
             // Usa un Thread.sleep largo para probar, luego optimiza con WebDriverWait.
             try { Thread.sleep(5000); } catch (InterruptedException e) {}
             DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");
             String fechaActual = dtf.format(LocalDateTime.now());
             String fechaFinanl = "11/30 23:59";
                
          // Llamamos al m todo est tico que acabamos de crear
          // Esto pondr  la fecha al final, con el mismo color gris/blanco del tema
          agregarFechaFinal(driver, fechaFinanl);
          
          System.out.println("se modifico el fecha ");
          Thread.sleep(5000); 
          System.out.println("se modifico el fecha f ");
          
          
          
              WebElement checkAmarillo = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Subida')]"));
                
                
             // --- ESCENARIO 1: Solo Verde ---
             // Si la p gina carga con ambos marcados, haz clic en el amarillo para desmarcarlo
             checkAmarillo.click();
             Thread.sleep(1000); // Espera breve para que la gr fica reaccione
            tomarCapturaElemento(driver,"Descarga_"+"CL_"+codigo_local);
           
             // --- ESCENARIO 2: Solo Amarillo ---
             // Marcamos el amarillo y desmarcamos el verde
            Thread.sleep(5000);
         //   WebElement check1Amarillo = driver.findElement(By.xpath("//*[local-name()='text' and text()='Sunida']"));
            while (intentosOutbound < 3) {
                try {
            
            //check1Amarillo.click();
            Thread.sleep(1000);
            System.out.println("Clic exitoso en Outbound");
            break; // Si llega aqu , el clic funcion  y sale del bu
                } catch (StaleElementReferenceException e) {
                    intentosOutbound++;
                    System.out.println("El elemento se refresc . Reintentando clic... (" + intentosOutbound + "/3)");
                    // Peque a pausa para esperar que la gr fica se estabilice
                    try { Thread.sleep(500); } catch (InterruptedException ie) { }
                }
            }
            WebElement checkVerde = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Descarga')]"));
            checkVerde.click();
            WebElement checkAmarillo1 = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Subida')]"));
            checkAmarillo1.click();
            
             Thread.sleep(1000);
             tomarCapturaElemento(driver, "Salida_"+"CL_"+codigo_local);
                
              
            }
            

            
            	
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        
         	

	}
	
	
	//-------------------------------------------//
   //metodo para tomar caputura 
// 1. Agregamos "WebDriver driver" a los par ntesis
public static void tomarCapturaElemento(WebDriver driver, String NombreImagen) {
    try {
        // Ahora el m todo ya reconoce al driver
        WebElement elementoGrafica = driver.findElement(By.className("css-1xh1fv2-page-panes"));

        File screenshot = elementoGrafica.getScreenshotAs(OutputType.FILE);
        
        FileUtils.copyFile(screenshot, new File("src/main/resources/img/" + NombreImagen + ".png"));

        System.out.println("CAPTURA GUARDADA: " + NombreImagen);

    } catch (IOException e) {
        System.out.println("Error: " + e.getMessage());
    }
}	

	
	//metodo para lee excel y gargaso en una lista de listas 
		
	
	// Agrega este m todo dentro de tu clase 'minedu', puede ser al final junto a los otros static
	public static void agregarFechaFinal(WebDriver driver, String textoFecha) {
	    JavascriptExecutor js = (JavascriptExecutor) driver;
	    String script = 
	    		"try {\n" +
	    		        "    // --- TUS VARIABLES DE AJUSTE --- \n" +
	    		        "    var ajusteX = 35;  // Cuanto mas grande, mas a la DERECHA\n" +
	    		        "    var ajusteY = 84;  // Cuanto mas grande, mas ABAJO\n" +
	    		        "    // -------------------------------\n" +
	    		        "\n" +
	    		        "    var svg = document.querySelector('div[data-testid=\"data-testid chart\"] svg');\n" +
	    		        "    if (!svg) return 'Error: SVG no encontrado';\n" +
	    		        "\n" +
	    		        "    // 1. Encontrar coordenadas base del grid\n" +
	    		        "    var gridLine = svg.querySelector('path[stroke=\"#484753\"]'); \n" +
	    		        "    var finalX = 1218; // Base X\n" +
	    		        "    if (gridLine) {\n" +
	    		        "        var d = gridLine.getAttribute('d');\n" +
	    		        "        var matchX = /L\\s*([\\d\\.]+)/.exec(d);\n" +
	    		        "        if (matchX) finalX = parseFloat(matchX[1]);\n" +
	    		        "    }\n" +
	    		        "\n" +
	    		        "    // 2. Encontrar coordenadas base de la altura\n" +
	    		        "    var texts = Array.from(svg.querySelectorAll('text'));\n" +
	    		        "    var label = texts.find(t => t.textContent.includes('/') || t.textContent.includes(':'));\n" +
	    		        "    var finalY = 432.75; // Base Y\n" +
	    		        "    if (label) {\n" +
	    		        "        var transform = label.getAttribute('transform');\n" +
	    		        "        var matchY = /translate\\([\\d\\.]+,\\s*([\\d\\.]+)\\)/.exec(transform);\n" +
	    		        "        if (matchY) finalY = parseFloat(matchY[1]);\n" +
	    		        "    }\n" +
	    		        "\n" +
	    		        "    // 3. Aplicar TUS ajustes manuales\n" +
	    		        "    var posicionFinalX = finalX + ajusteX;\n" +
	    		        "    var posicionFinalY = finalY + ajusteY;\n" +
	    		        "\n" +
	    		        "    // 4. Crear el elemento\n" +
	    		        "    var ns = 'http://www.w3.org/2000/svg';\n" +
	    		        "    var newText = document.createElementNS(ns, 'text');\n" +
	    		        "    newText.textContent = arguments[0];\n" +
	    		        "    \n" +
	    		        "    // Estilos\n" +
	    		        "    newText.setAttribute('fill', '#B9B8CE');\n" +
	    		        "    newText.style.fontSize = '12px';\n" +
	    		        "    newText.style.fontFamily = '\"Microsoft YaHei\", sans-serif';\n" +
	    		        "    newText.setAttribute('text-anchor', 'end');\n" +
	    		        "    newText.setAttribute('dominant-baseline', 'central');\n" +
	    		        "\n" +
	    		        "    // 5. Ubicar\n" +
	    		        "    newText.setAttribute('transform', 'translate(' + posicionFinalX + ', ' + posicionFinalY + ')');\n" +
	    		        "    newText.setAttribute('id', 'fecha-selenium-ajustada');\n" +
	    		        "\n" +
	    		        "    // 6. Insertar\n" +
	    		        "    var old = svg.querySelector('#fecha-selenium-ajustada');\n" +
	    		        "    if (old) old.remove();\n" +
	    		        "    svg.appendChild(newText);\n" +
	    		        "\n" +
	    		        "    return 'Fecha movida: Derecha +' + ajusteX + 'px, Abajo +' + ajusteY + 'px';\n" +
	    		        "} catch (e) {\n" +
	    		        "    return 'Error: ' + e.message;\n" +
	    		        "}";
	    try {
	        js.executeScript(script, textoFecha);
	        System.out.println("DOM modificado: Fecha agregada al final -> " + textoFecha);
	    } catch (Exception e) {
	        System.out.println("Error inyectando fecha: " + e.getMessage());
	    }
	}
	
}
