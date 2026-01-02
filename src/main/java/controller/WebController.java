package controller;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
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
      campoUser.sendKeys("minedu-i301"); // <--- CAMBIA "admin" POR TU USUARIO REAL
   // 2. Ubicar campo de Contrase a y escribir
      WebElement campoPass = driver.findElement(By.name("password"));
      campoPass.sendKeys("m7n1du@i01"); // <--- PON TU CLAVE REAL
      WebElement botonLogin = driver.findElement(By.xpath("//button[contains(., 'Log in')]"));
      botonLogin.click();

      System.out.println("Login enviado. Esperando carga del Dashboard...");

      return driver;
  }
  
  
	
	public String completarConCeros(String texto) {
	    if (texto.length() >= 6) {
	        return texto;
	    }
	    return "0".repeat(6 - texto.length()) + texto;
	}
  
  public static String ElegirURL(String fechaINI, String fechafin,String item,String codigo_local,String CID,WebDriver drive) {
	  if(codigo_local.length()<6) {
			 codigo_local="0".repeat(6 - codigo_local.length()) + codigo_local;
		 }

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
			                "&from=" + fromGrafana + 
			                "&to=" + toGrafana + 
			                "&timezone=browser" +
			                "&var-check_user=2" +
			                "&var-hostgroup=minedu" +
			                "&var-hostname=MINEDU5K2_" + CID + "_SRX300" +
			                "&var-max_bandwidth=30" +
			                "&refresh=10m" +
			                "&viewPanel=panel-155"; // <--- ESTO es lo que hace que solo se vea la gráfica return url;
			        
			        return url;
	  
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
  
  
  
  public static Map<String, String> capturarDatosGrafica(WebDriver driver) {
	    Map<String, String> datos = new HashMap<>();
	    
	    // Inicializamos con valores vacíos por si falla algo
	    datos.put("Descarga_Max", "0");
	    datos.put("Descarga_Min", "0");
	    datos.put("Descarga_Avg", "0");
	    datos.put("Subida_Max", "0");
	    datos.put("Subida_Min", "0");
	    datos.put("Subida_Avg", "0");

	    try {
	        // 1. Obtener el texto completo de la línea "Descarga"
	        WebElement elemDescarga = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Descarga')]"));
	        String textoDescarga = elemDescarga.getText().replace("\u00A0", " "); // Limpiar espacios raros

	        // 2. Obtener el texto completo de la línea "Subida"
	        WebElement elemSubida = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Subida')]"));
	        String textoSubida = elemSubida.getText().replace("\u00A0", " ");

	        // 3. Extraer números usando Regex (Patrón: "Max: 10.50 Mbps")
	        datos.put("Descarga_Max", extraerValor(textoDescarga, "Max:"));
	        datos.put("Descarga_Min", extraerValor(textoDescarga, "Min:"));
	        datos.put("Descarga_Avg", extraerValor(textoDescarga, "Average:"));

	        datos.put("Subida_Max", extraerValor(textoSubida, "Max:"));
	        datos.put("Subida_Min", extraerValor(textoSubida, "Min:"));
	        datos.put("Subida_Avg", extraerValor(textoSubida, "Average:"));
	        
	        System.out.println("Datos capturados: " + datos);

	    } catch (Exception e) {
	        System.out.println("Error capturando datos numéricos: " + e.getMessage());
	    }
	    return datos;
	}

	// Helper para parsear el texto (Busca la etiqueta y devuelve el numero siguiente)
	public static String extraerValor(String textoCompleto, String etiqueta) {
	    try {
	        // Ejemplo de regex: Busca "Max:" seguido de espacios y captura los digitos y puntos
	        String patron = etiqueta + "\\s*([\\d\\.]+)\\s*Mbps";
	        Pattern r = Pattern.compile(patron);
	        Matcher m = r.matcher(textoCompleto);
	        
	        if (m.find()) {
	            return m.group(1); // Devuelve solo el número (ej: 80.56)
	        }
	    } catch (Exception e) {
	        // Ignorar
	    }
	    return "0.00";
	}
  
  
  public static void TomadeCapturaGurardado(WebDriver driver,String codigo_local,String ITEM) throws InterruptedException {
      



  try {
	// --- ESCENARIO 1: Solo Verde ---
      // Si la p gina carga con ambos marcados, haz clic en el amarillo para desmarcarlo
	  Thread.sleep(2000);
      WebElement checkAmarillo = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Subida')]"));
      checkAmarillo.click();
      Thread.sleep(2000); // Espera breve para que la gr fica reaccione
     tomarCapturaElemento(driver,"Descarga_"+"CL_"+codigo_local,ITEM);
     // --- ESCENARIO 2: Solo Amarillo ---

	  
	Thread.sleep(2000);
	 WebElement checkVerde = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Descarga')]"));
	  checkVerde.click();
	  Thread.sleep(2000);
	  WebElement checkAmarillo1 = driver.findElement(By.xpath("//*[local-name()='text' and contains(., 'Subida')]"));
	  checkAmarillo1.click();
	  
	   tomarCapturaElemento(driver, "Salida_"+"CL_"+codigo_local,ITEM); 
	    
	
	
	
	
} catch (InterruptedException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
	  tomarCapturaElemento(driver, "Salida_"+"CL_"+codigo_local,ITEM); 
	  try {
		Thread.sleep(2000);
	} catch (InterruptedException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
	  tomarCapturaElemento(driver,"Descarga_"+"CL_"+codigo_local,ITEM);
}
//   WebElement check1Amarillo = driver.findElement(By.xpath("//*[local-name()='text' and text()='Sunida']"));

 
  }


  public static void tomarCapturaElemento(WebDriver driver, String NombreImagen,String ITEM) throws InterruptedException {
	    try {
	        // Ahora el m todo ya reconoce al driver
	    	Thread.sleep(2000);

	        WebElement elementoGrafica = driver.findElement(By.className("css-1xh1fv2-page-panes"));
	    	Thread.sleep(2000);

	        File screenshot = elementoGrafica.getScreenshotAs(OutputType.FILE);
	        
	        FileUtils.copyFile(screenshot, new File("src/main/resources/img/ITEM"+ITEM+"/" + NombreImagen + ".png"));

	        System.out.println("CAPTURA GUARDADA: " + NombreImagen);

	    } catch (IOException e) {
	        System.out.println("Error: " + e.getMessage());
	    }
	}

  public static void colapsarMenuGrafana(WebDriver driver) {
	    try {
	        // Espera un segundo a que cargue el botón
	        WebElement botonMenu = driver.findElement(By.id("mega-menu-header-toggle"));
	        
	        // Verificamos si el atributo 'aria-label' dice "Cerrar menú"
	        // Si ya está cerrado, el label suele cambiar a "Abrir menú"
	        if (botonMenu.getAttribute("aria-label").contains("Cerrar")) {
	            botonMenu.click();
	            System.out.println("Menú lateral colapsado para mayor visibilidad.");
	        }
	    } catch (Exception e) {
	        System.out.println("El menú ya estaba reducido o no se encontró el botón.");
	    }
	}
 
  
}
