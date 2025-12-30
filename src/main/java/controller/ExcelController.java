package controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class ExcelController {
	
	
	
	
	
	public static List<List<String>> leerColumnasEspecificas(String rutaArchivo, int[] indicesColumnas) {
	    List<List<String>> tablaDatos = new ArrayList<>(); // Nuestra "mini tabla"
	    
	    try (Workbook workbook = WorkbookFactory.create(new File(rutaArchivo))) {
	        Sheet sheet = workbook.getSheetAt(0);
	        DataFormatter formatter = new DataFormatter();

	        // Recorremos cada fila del Excel
	        for (Row row : sheet) {
	            // Opcional: Saltar encabezados (descomenta si la fila 0 son t tulos)
	            // if (row.getRowNum() == 0) continue;

	            List<String> datosFila = new ArrayList<>();
	            boolean filaTieneDatos = false;

	            // Recorremos SOLO las columnas que t  pediste (el array indicesColumnas)
	            for (int colIndex : indicesColumnas) {
	                Cell cell = row.getCell(colIndex);
	                String valor = formatter.formatCellValue(cell); // Convierte todo a texto
	                
	                datosFila.add(valor);
	                
	                // Verificamos si al menos una celda de esta fila tiene algo escrito
	                if (!valor.trim().isEmpty()) {
	                    filaTieneDatos = true;
	                }
	            }

	            // Solo agregamos la fila si no est  totalmente vac a
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
	

	
	/**
	 * Registra los resultados de las métricas de red en un archivo Excel.
	 * Si el archivo no existe, lo crea con encabezados; si existe, añade una nueva fila.
	 * * @param codigoLocal   ID del colegio/local (extraído del Excel o URL).
	 * @param archivoSalida Ruta del archivo .xlsx donde se guardarán los resultados.
	 * @param datos         Mapa que contiene los valores de Max, Min y Avg de descarga/subida.
	 */
	
	

	public static void escribirExcelResultados(String departamento,String provincia,String distrito ,String ITEM,String codigoLocal,String NOMBRELEE ,String CID, String archivoSalida, Map<String, String> datos) {
	    
	    Workbook workbook;
	    Sheet sheet;
	    File file = new File(archivoSalida);

	    try {
	    	// --- SECCIÓN 1: INICIALIZACIÓN O APERTURA DEL ARCHIVO ---
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
	            header.createCell(0).setCellValue("ITEM");
	            header.createCell(1).setCellValue("Nombre de LLEE");
	            header.createCell(2).setCellValue("Departamento");
	            header.createCell(3).setCellValue("Provincia");
	            header.createCell(4).setCellValue("Distrito");
	            header.createCell(5).setCellValue("Codigo Local");          	    
	            header.createCell(6).setCellValue("Descarga Max (Mbps)");
	            header.createCell(7).setCellValue("Descarga Min (Mbps)");
	            header.createCell(8).setCellValue("Descarga Avg (Mbps)");
	            header.createCell(9).setCellValue("Subida Max (Mbps)");
	            header.createCell(10).setCellValue("Subida Min (Mbps)");
	            header.createCell(11).setCellValue("Subida Avg (Mbps)");
	            //header.createCell(12).setCellValue("CID");
	        }

	     // --- SECCIÓN 2: DETERMINAR LA SIGUIENTE FILA LIBRE ---
	        int rowCount = sheet.getLastRowNum();
	        Row row = sheet.createRow(rowCount + 1);

	     // --- SECCIÓN 3: INSERCIÓN DE DATOS ---
	        row.createCell(0).setCellValue(ITEM);
	        row.createCell(1).setCellValue(NOMBRELEE);
	        row.createCell(2).setCellValue(departamento);
	        row.createCell(3).setCellValue(provincia);
	        row.createCell(4).setCellValue(distrito);
	        row.createCell(5).setCellValue(codigoLocal);      
	        row.createCell(6).setCellValue(datos.get("Descarga_Max"));
	        row.createCell(7).setCellValue(datos.get("Descarga_Min"));
	        row.createCell(8).setCellValue(datos.get("Descarga_Avg"));
	        row.createCell(9).setCellValue(datos.get("Subida_Max"));
	        row.createCell(10).setCellValue(datos.get("Subida_Min"));
	        row.createCell(11).setCellValue(datos.get("Subida_Avg"));
	     //   row.createCell(1).setCellValue(CID);

	     // --- SECCIÓN 3: INSERCIÓN DE DATOS ---
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
