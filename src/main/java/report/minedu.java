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

//		
	
		Controller controller=new Controller();
	    int[] columnasDeseadas = {0,1,2,3,4,5};
	    String RutaExcelFinal = "src/main/resources/";
	    String ITEM=controller.ElegirITEM();
	    String NombreExcel="Excel_"+ITEM+".xlsx";
        int n = 0;
        System.out.println(NombreExcel);
        
	    //Elegir el nombre del excel por ITEM
	    
	    
	    ///VARIABLE DE LOS ENCABEZADOS DEL EXCEL 
	    // --- C MO USAR LOS DATOS EN EL BUCLE ---
        //
        String Numero;    
        String codigo_local;
        String CID;    
        String NomLLEE;
        String Departamento;
        String Provincia;
        String Distrito;     
        String Peak_Receive;
        String Minimum_Receive;
        String Average_Receive;
        String Peak_Transmit;
        String Minimum_Transmit;
        String Average_Transmit;
        String Ancho_de_banda;
       
        
               
       //PARA REDUCIR EL MENU
        boolean menuReducido = false;
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
                CID=fila.get(1);
                NomLLEE=fila.get(2);
                Departamento=fila.get(3);
                Provincia=fila.get(4);
                Distrito=fila.get(5); 
                n = n + 1; // Ahora funcionar  sin errores
                if(codigo_local.matches("[\\d\\.]+")) { 
                String url=WebController.ElegirURL(fechaini, fechafin,ITEM, codigo_local, CID,driver);
                Thread.sleep(1000); 
                
                System.out.println(url);
   
		        driver.get(url);

       	        System.out.println("SE VISUALIZA LA FRAFICA .......");
                
               Thread.sleep(3000); 
        //RETRAER EL MENU VERTICAL------------------
               Thread.sleep(3000); 
            // 2. Solo entrará aquí en la primera iteración
               if (!menuReducido) {
                   WebController.colapsarMenuGrafana(driver);
                   menuReducido = true; // Cambiamos a true para que no vuelva a entrar
               }
               
        //----------------------------------------------
                //marcar y desmarcar 
                      // 1. Localizar los elementos (ajusta los selectores a tu p gina)
                
          //TOMAR DATOS DE LA GRAFICA PARA EL EXCEL 
                
         
                // 2. INYECTAR FECHA EN DOM (Usa el método agregarFechaFinal que corregimos antes)
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");
                String fechaActual = dtf.format(LocalDateTime.now());

                // 3. CAPTURAR DATOS NUMÉRICOS (¡NUEVO!)
                Map<String, String> datosCapturados = WebController.capturarDatosGrafica(driver);

                // 4. GUARDAR EN EXCEL (¡NUEVO!)
               
               ExcelController.escribirExcelResultados(Departamento, Provincia,Distrito ,ITEM,codigo_local, NomLLEE, CID, RutaExcelFinal+"excel/ITEM"+ITEM+"/Resultados_Grafana_"+ITEM+".xlsx", datosCapturados);
                
                
          //-------------------------------------------------------------------
           //TOMAR CAPTURA DE LA IMAGENES 
                
               WebController.TomadeCapturaGurardado(driver, codigo_local,ITEM); 
              
            	  }else {
            		  
            		  System.out.println("SE ESTA PRSEANDO EL ENCABEZADO SE PASARA LA SIGUINTE FILA");
                        
            	  } 
          }
            	
        } catch (Exception e) {
            e.printStackTrace();
        }
        
	}

	
}
