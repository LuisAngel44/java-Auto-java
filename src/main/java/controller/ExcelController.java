package controller;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

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
	public static void cargar() {

        

        // 2. INYECTAR FECHA EN DOM (Usa el método agregarFechaFinal que corregimos antes)
       // DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        //String fechaActual = dtf.format(LocalDateTime.now());
       

        // 3. CAPTURAR DATOS NUMÉRICOS (¡NUEVO!)
        //Map<String, String> datosCapturados = capturarDatosGrafica(driver);

        // 4. GUARDAR EN EXCEL (¡NUEVO!)
        //escribirExcelResultados(codigo_local, fechaActual, datosCapturados);
	}

}
