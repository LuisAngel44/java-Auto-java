package controller;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class WebController {
	
 public WebController() throws InterruptedException {
	
 }
 
 
  public WebDriver  GraficaAnchoBancha() {
	  // PASO 1
		//options para acar paginas sin certificado ssl 
	  ChromeOptions options = new ChromeOptions();
	    options.setAcceptInsecureCerts(true);           // Aceptar certificados "malos"
	    options.addArguments("--ignore-certificate-errors"); 
	    options.addArguments("--allow-running-insecure-content");
	    options.addArguments("--remote-allow-origins=*"); // A veces ayuda con versiones nuevas

	    // --- PASO 2: Ahora s , abrimos el navegador CON las opciones ---
	    
	    // (Aseg rate de borrar cualquier otro "new ChromeDriver()" que tengas arriba)
	    WebDriver driver = new ChromeDriver(options);
      driver.manage().window().maximize();
      
      // --- PASO 3: Navegar ---
      // Al entrar aqu , ya NO deber a salir la pantalla roja
      driver.get("https://181.176.39.44/grafana"); 

      // Espera para que cargue Grafana
      driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
      
      System.out.println("Entr  a Grafana correctamente.");

      // AQU  CODIGO DE USER ...
      WebElement campoUser = driver.findElement(By.name("user")); 
      campoUser.sendKeys("ap-view"); // <--- CAMBIA "admin" POR TU USUARIO REAL
   // 2. Ubicar campo de Contrase a y escribir
      WebElement campoPass = driver.findElement(By.name("password"));
      campoPass.sendKeys("ap-view@123"); // <--- PON TU CLAVE REAL
      WebElement botonLogin = driver.findElement(By.xpath("//button[contains(., 'Log in')]"));
      botonLogin.click();

      System.out.println("Login enviado. Esperando carga del Dashboard...");

      return driver;
  }
  
  public static void ElegirURL(String fechaINI, String fechafin,String item,String codigo_local,String CID,WebDriver drive) {
	//   "13/12/2025 00:00"; si es item 2 o item 4 
	     //  "12/01/2026 23:59";	  
			  
		  String fechaInicioPeru = fechaINI+" 00:00";
		  String fechaFinPeru    = fechafin + " 23:59"; // Puse 2026 como pediste, ajusta si es error.
		  
		// 1. Convertir tus fechas simples a formato URL de Grafana
	        String fromGrafana = convertirFecha(fechaInicioPeru);
	        String toGrafana   = convertirFecha(fechaFinPeru);
	        
	     // Variables de tu código anterior
	             
	        String url = "https://181.176.39.44/grafana/d/device-detail/device-detail?" +
                    "var-codigo_local=" + codigo_local +
                    "&orgId=6" +
                    "&from=" + fromGrafana +  // <--- FECHA INICIO CONVERTIDA
                    "&to=" + toGrafana +      // <--- FECHA FIN CONVERTIDA
                    "&timezone=browser" +
                    "&var-exportpdf=&var-check_user=2&var-item=$__all" +
                    "&var-hostgroup=minedu&var-departamento=$__all" +
                    "&var-provincia=$__all&var-distrito=$__all" +
                    "&var-centro_poblado=$__all&var-centro_educativo=$__all" +
                    "&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_" + CID + "_SRX300" +
                    "&var-max_bandwidth=30&var-interface=$__all&var-rp=six_months&var-service=$__all" +
                    "&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default" +
                    "&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F7" +
                    "&refresh=10m&viewPanel=panel-145";
	        
	        drive.get(url);
            
		  
	  
  }
  public static String convertirFecha(String fechaTexto) {
      // 1. Definimos cómo viene tu fecha: "dd/MM/yyyy HH:mm"
      DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
      
      // 2. Definimos cómo la quiere Grafana: "yyyy-MM-dd'T'HH:mm:ss.000'Z'"
      DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.000'Z'");

      // 3. Le decimos a Java: "Esta fecha es de Lima/Perú"
      LocalDateTime ldt = LocalDateTime.parse(fechaTexto, inputFormat);
      ZonedDateTime fechaPeru = ldt.atZone(ZoneId.of("America/Lima"));

      // 4. La convertimos a UTC (Hora Zero) para Grafana
      ZonedDateTime fechaUTC = fechaPeru.withZoneSameInstant(ZoneId.of("UTC"));

      return fechaUTC.format(outputFormat);
  }
  
  
  public void TomadeCapturaGurardado(WebDriver driver,String URL) {
	


             
             
    
  }
  
}
