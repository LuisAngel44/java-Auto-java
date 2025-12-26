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
	    String ITEM=controller.ElegirITEM();
	    String NombreExcel="Excel_w_ITEM_"+ITEM+".xlsx";
        int n = 0;
        System.out.println(NombreExcel);
	    //Elegir el nombre del excel por ITEM
	    
	    
	    ///VARIABLE DE LOS ENCABEZADOS DEL EXCEL 
	    // --- C MO USAR LOS DATOS EN EL BUCLE ---
        //
        String Numero;
        //
        
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
       String fechaini=controller.ElegirFechaIni();
       String  fechafin=controller.ElegirFechaFinal();
            
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
                
              
				WebController.ElegirURL(fechaini, fechafin,ITEM, codigo_local, CID,driver);
		
                
               Thread.sleep(5000); 
               
                System.out.println("Reduce el menu vertical");
                //marcar y desmarcar 
                System.out.println("se empieza a ver las graficas ");
                // 1. Localizar los elementos (ajusta los selectores a tu p gina)
                
          //TOMAR DATOS DE LA GRAFICA PARA EL EXCEL 
          //-------------------------------------------------------------------
           
                
                // --- ESCENARIO 1: Solo Verde ---
                // Si la p gina carga con ambos marcados, haz clic en el amarillo para desmarcarlo

                WebElement checkAmarillo = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Subida')]"));
                checkAmarillo.click();
                Thread.sleep(1000); // Espera breve para que la gr fica reaccione
               tomarCapturaElemento(driver,"Descarga_"+"CL_"+codigo_local);
          
          
          
             // --- ESCENARIO 2: Solo Amarillo ---
             // Marcamos el amarillo y desmarcamos el verde
            Thread.sleep(5000);
         //   WebElement check1Amarillo = driver.findElement(By.xpath("//*[local-name()='text' and text()='Sunida']"));
         
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
	
}
