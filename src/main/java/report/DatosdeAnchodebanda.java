package report;

import java.time.Duration;

import org.openqa.selenium.By;
//Importar estas clases
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

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
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import java.time.Duration;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.FileInputStream;




public class DatosdeAnchodebanda {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
	
				// 1. Iniciar el navegador
				// --- PASO 1: Configurar las opciones (ANTES de abrir nada) ---
			    ChromeOptions options = new ChromeOptions();
			    options.setAcceptInsecureCerts(true);           // Aceptar certificados "malos"
			    options.addArguments("--ignore-certificate-errors"); 
			    options.addArguments("--allow-running-insecure-content");
			    options.addArguments("--remote-allow-origins=*"); // A veces ayuda con versiones nuevas

			    // --- PASO 2: Ahora s�, abrimos el navegador CON las opciones ---
			    // (Aseg�rate de borrar cualquier otro "new ChromeDriver()" que tengas arriba)
			    WebDriver driver = new ChromeDriver(options);
		     // 2. Ir a la web (Ejemplo con Google, c�mbialo por tu url de Grafana/Bitel)       

			    
			    try {
		            driver.manage().window().maximize();
		            
		            // --- PASO 3: Navegar ---
		            // Al entrar aqu�, ya NO deber�a salir la pantalla roja
		            driver.get("https://181.176.39.44/grafana"); 

		            // Espera para que cargue Grafana
		            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
		            
		            System.out.println("Entr� a Grafana correctamente.");

		            // AQU� CODIGO DE USER ...
		            WebElement campoUser = driver.findElement(By.name("user")); 
		            campoUser.sendKeys("ap-view"); // <--- CAMBIA "admin" POR TU USUARIO REAL
		         // 2. Ubicar campo de Contrase�a y escribir
		            WebElement campoPass = driver.findElement(By.name("password"));
		            campoPass.sendKeys("ap-view@123"); // <--- PON TU CLAVE REAL
		            WebElement botonLogin = driver.findElement(By.xpath("//button[contains(., 'Log in')]"));
		            botonLogin.click();

		            System.out.println("Login enviado. Esperando carga del Dashboard...");

		            // 4. Esperar un poco a que cargue el panel principal
		            Thread.sleep(5000); 
		            
		        //---------------------------------------------------------------------------------//
		            //METODO PARA GRABAR LOS VALORES EN UNA LISTA //
		        
		            String ruta = "src/main/resources/AnchoBanda.xlsx";
		         // Aqu� definimos qu� columnas queremos: La 0 (A), la 2 (C) y la 3 (D)
		            int[] columnasDeseadas = {0,1};
		         // Llamamos al m�todo
		            
		            List<List<String>> misDatos = leerColumnasEspecificas(ruta, columnasDeseadas);
		            misDatos.clear();
		            misDatos = leerColumnasEspecificas(ruta, columnasDeseadas);
		         // --- C�MO USAR LOS DATOS EN EL BUCLE ---
		        
		            int n = 0;
		            String codigo_local;
		            String CID;
		            for (List<String> fila : misDatos) {
		            	WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
		                codigo_local = fila.get(0);
		                CID=fila.get(1);
		            
		                // Ahora funcionar� sin errores
		                System.out.println("Procesando: codigo local:  " + codigo_local + "\n CID: "+CID);
		                	
		                // AQUI CONTINUAR�A EL C�DIGO DE LA GR�FICA...
		                //https://181.176.39.44/grafana/d/device-detail/device-detail?var-codigo_local="+codigo_local+"&orgId=6&from=2025-11-01T05:00:00.000Z&to=2025-12-01T04:59:59.000Z&timezone=browser&var-exportpdf=&var-check_user=3&var-item=$__all&var-hostgroup=minedu&var-departamento=$__all&var-provincia=$__all&var-distrito=$__all&var-centro_poblado=$__all&var-centro_educativo=$__all&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_"+CID+"_SRX300&var-max_bandwidth=100&var-interface=$__all&var-rp=six_months&var-service=$__all&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F5%26var-interface%3Dge-0%2F0%2F7&refresh=10m&viewPanel=panel-145
		                driver.get("https://181.176.39.44/grafana/d/device-detail/device-detail?var-codigo_local="+codigo_local+"&orgId=6&from=2025-11-01T05:00:00.000Z&to=2025-12-01T04:59:59.000Z&timezone=browser&var-exportpdf=&var-check_user=2&var-item=$__all&var-hostgroup=minedu&var-departamento=$__all&var-provincia=$__all&var-distrito=$__all&var-centro_poblado=$__all&var-centro_educativo=$__all&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_"+CID+"_SRX300&var-max_bandwidth=30&var-interface=$__all&var-rp=six_months&var-service=$__all&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F7&refresh=10m&viewPanel=panel-145");
		                //https://181.176.39.44/grafana/d/device-detail/device-detail?var-codigo_local=354949&orgId=6&from=2025-11-01T05:00:00.000Z&to=2025-12-01T04:59:59.000Z&timezone=browser&var-exportpdf=&var-check_user=2&var-item=$__all&var-hostgroup=minedu&var-departamento=$__all&var-provincia=$__all&var-distrito=$__all&var-centro_poblado=$__all&var-centro_educativo=$__all&var-asset_name=$__all&var-hostname_old=&var-hostname=MINEDU5K2_111883_SRX300&var-max_bandwidth=30&var-interface=$__all&var-rp=six_months&var-service=$__all&var-channel=plugin%2Ftestdata%2Frandom-2s-stream&var-pingtrace_session_id=default&var-render_interface=var-interface%3Dge-0%2F0%2F1%26var-interface%3Dge-0%2F0%2F7&refresh=10m&viewPanel=panel-145
		                Thread.sleep(5000); 
		                //
		                System.out.println("Reduce el menu vertical");
		                //marcar y desmarcar 
		                System.out.println("se empieza a ver las graficas ");
		                		            
		                
		                n++;
		                                

		                // 2. INYECTAR FECHA EN DOM (Usa el método agregarFechaFinal que corregimos antes)
		                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");
		                String fechaActual = dtf.format(LocalDateTime.now());
		               

		                // 3. CAPTURAR DATOS NUMÉRICOS (¡NUEVO!)
		                Map<String, String> datosCapturados = capturarDatosGrafica(driver);

		                // 4. GUARDAR EN EXCEL (¡NUEVO!)
		                escribirExcelResultados(codigo_local, fechaActual, datosCapturados);
                           
		                
		                
		            }
		         	
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
	}
	
	
	
	public static List<List<String>> leerColumnasEspecificas(String rutaArchivo, int[] indicesColumnas) {
	    List<List<String>> tablaDatos = new ArrayList<>(); // Nuestra "mini tabla"
	    
	    try (Workbook workbook = WorkbookFactory.create(new File(rutaArchivo))) {
	        Sheet sheet = workbook.getSheetAt(0);
	        DataFormatter formatter = new DataFormatter();

	        // Recorremos cada fila del Excel
	        for (Row row : sheet) {
	            // Opcional: Saltar encabezados (descomenta si la fila 0 son t�tulos)
	            // if (row.getRowNum() == 0) continue;

	            List<String> datosFila = new ArrayList<>();
	            boolean filaTieneDatos = false;

	            // Recorremos SOLO las columnas que t� pediste (el array indicesColumnas)
	            for (int colIndex : indicesColumnas) {
	                Cell cell = row.getCell(colIndex);
	                String valor = formatter.formatCellValue(cell); // Convierte todo a texto
	                
	                datosFila.add(valor);
	                
	                // Verificamos si al menos una celda de esta fila tiene algo escrito
	                if (!valor.trim().isEmpty()) {
	                    filaTieneDatos = true;
	                }
	            }

	            // Solo agregamos la fila si no est� totalmente vac�a
	            if (filaTieneDatos) {
	                tablaDatos.add(datosFila);
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return tablaDatos;
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
	    return "0";
	}
	


	public static void escribirExcelResultados(String codigoLocal, String fecha, Map<String, String> datos) {
	    String archivoSalida = "src/main/resources/Resultados_Grafana.xlsx";
	    Workbook workbook;
	    Sheet sheet;
	    File file = new File(archivoSalida);

	    try {
	        // 1. Si el archivo existe, lo abrimos. Si no, lo creamos.
	        if (file.exists()) {
	            FileInputStream fis = new FileInputStream(file);
	            workbook = new XSSFWorkbook(fis);
	            sheet = workbook.getSheetAt(0);
	            fis.close();
	        } else {
	            workbook = new XSSFWorkbook();
	            sheet = workbook.createSheet("Datos");
	            // Crear encabezados si es nuevo
	            Row header = sheet.createRow(0);
	            header.createCell(0).setCellValue("Codigo Local");
	            header.createCell(1).setCellValue("Fecha Hora");
	            header.createCell(2).setCellValue("Descarga Max");
	            header.createCell(3).setCellValue("Descarga Min");
	            header.createCell(4).setCellValue("Descarga Avg");
	            header.createCell(5).setCellValue("Subida Max");
	            header.createCell(6).setCellValue("Subida Min");
	            header.createCell(7).setCellValue("Subida Avg");
	        }

	        // 2. Crear nueva fila al final
	        int rowCount = sheet.getLastRowNum();
	        Row row = sheet.createRow(rowCount + 1);

	        // 3. Escribir datos
	        row.createCell(0).setCellValue(codigoLocal);
	        row.createCell(1).setCellValue(fecha);
	        row.createCell(2).setCellValue(datos.get("Descarga_Max"));
	        row.createCell(3).setCellValue(datos.get("Descarga_Min"));
	        row.createCell(4).setCellValue(datos.get("Descarga_Avg"));
	        row.createCell(5).setCellValue(datos.get("Subida_Max"));
	        row.createCell(6).setCellValue(datos.get("Subida_Min"));
	        row.createCell(7).setCellValue(datos.get("Subida_Avg"));

	        // 4. Guardar cambios
	        FileOutputStream fos = new FileOutputStream(archivoSalida);
	        workbook.write(fos);
	        fos.close();
	        workbook.close();
	        
	        System.out.println("Excel actualizado para local: " + codigoLocal);

	    } catch (IOException e) {
	        System.out.println("Error escribiendo Excel: " + e.getMessage());
	    }
	}
	
	

}
