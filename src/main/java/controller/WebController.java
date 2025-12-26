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
  
  public static String ElegirURL(String item, String Mes_Año,String codigo_local,String CID) {
	  String[] date = Mes_Año.split(",");
	  if(item.equals(3)||item.equals(1)) {
		  
		  
		    String fechaInicioPeru = "13/12/"+date+" 00:00";
		      String fechaFinPeru    = "13/12/"+date+" 12:59"; // Puse 2026 como pediste, ajusta si es error.
		  
	  }else if(){
			  }else {System.exit(0);}
		// Si es otro año, solo cambia el String.
  

      // 1. Convertir tus fechas simples a formato URL de Grafana
      String fromGrafana = convertirFecha(fechaInicioPeru);
      String toGrafana   = convertirFecha(fechaFinPeru);

      // Variables de tu código anterior
      codigo_local = "TU_CODIGO";
      CID = "TU_CID";
	  
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
  
  public void TomadeCapturaGurardado(,WebDriver driver,String URL) {
	  fechaMesAño.split(ITEM);

	  
	// 1. Definir el formato exacto que pide Grafana
	  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.000'Z'");

	  // 2. Obtener la hora actual en UTC (Recomendado para servidores)
	  ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("UTC"));

	  // EJEMPLO 1: Rango de las últimas 24 horas exactas
	  String fechaFin = ahora.format(formatter);
	  String fechaInicio = ahora.minus(24, ChronoUnit.HOURS).format(formatter);

	  // EJEMPLO 2: Si quieres el "Mes actual" (Desde el día 1 a las 00:00 hasta ahora)
	  // String fechaFin = ahora.format(formatter);
	  // String fechaInicio = ahora.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).format(formatter);

	  // 3. Imprimir para verificar (Opcional)
	  System.out.println("Desde: " + fechaInicio);
	  System.out.println("Hasta: " + fechaFin); 
                
             
             
    
      	
     } catch (Exception e) {
         e.printStackTrace();
     }
  
}
