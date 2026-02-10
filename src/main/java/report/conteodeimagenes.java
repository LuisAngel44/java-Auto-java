package report;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import controller.WebController;
import controller.Controller;
import java.io.File;
import java.io.FileInputStream;

import java.util.HashSet;
import java.util.Set;

public class conteodeimagenes {

    public static void main(String[] args) throws InterruptedException {
    	Controller controller12=new Controller();
    	   String ITEM=controller12.ElegirITEM();
        // --- CONFIGURACIÓN ---
        String rutaCarpeta = "src/main/resources/img/ITEM"+ITEM+"/"; // Pon tu ruta aquí
        String rutaExcel = "src/main/resources/Excel_"+ITEM+".xlsx";  // Pon tu ruta aquí
        int columnaID = 0; // Si los IDs están en la Columna A pon 0, si es B pon 1
        // ---------------------

        // 1. Escaneamos la carpeta y guardamos los NÚMEROS encontrados
        // Usamos dos Sets separados para validar que existan los pares
        Set<Integer> salidasEncontradas = new HashSet<>();
        Set<Integer> descargasEncontradas = new HashSet<>();

        File folder = new File(rutaCarpeta);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    String nombre = file.getName(); // Ej: Salida_CL_04239.png
                    
                    // Extraemos solo los dígitos del nombre usando Expresión Regular
                    // "\\D+" significa "cualquier cosa que NO sea un dígito"
                    String soloNumeros = nombre.replaceAll("\\D+", ""); 
                    
                    if (!soloNumeros.isEmpty()) {
                        int numeroId = Integer.parseInt(soloNumeros); // Convertimos a Entero (quita los ceros extra)

                        if (nombre.toLowerCase().contains("salida")) {
                            salidasEncontradas.add(numeroId);
                        } else if (nombre.toLowerCase().contains("descarga")) {
                            descargasEncontradas.add(numeroId);
                        }
                    }
                }
            }
        }
        
        System.out.println("Archivos escaneados. Salidas: " + salidasEncontradas.size() + " | Descargas: " + descargasEncontradas.size());

        // 2. Leemos el Excel y verificamos qué falta
        try (FileInputStream fis = new FileInputStream(new File(rutaExcel));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            System.out.println("\n--- REPORTE DE FALTANTES ---");
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltamos cabecera si quieres

                Cell cell = row.getCell(columnaID);
                if (cell != null) {
                    // Obtenemos el ID del Excel (sea texto o número)
                    int idExcel = -1;
                    
                    try {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            idExcel = (int) cell.getNumericCellValue();
                        } else if (cell.getCellType() == CellType.STRING) {
                            // Limpiamos por si el Excel tiene texto basura
                            String texto = cell.getStringCellValue().replaceAll("\\D+", "");
                            if(!texto.isEmpty()) idExcel = Integer.parseInt(texto);
                        }
                    } catch (Exception e) {
                        System.out.println("Error leyendo fila " + row.getRowNum());
                    }

                    // 3. LA VERIFICACIÓN DE PARES
                    if (idExcel != -1) {
                        boolean faltaSalida = !salidasEncontradas.contains(idExcel);
                        boolean faltaDescarga = !descargasEncontradas.contains(idExcel);

                        if (faltaSalida || faltaDescarga) {
                            System.out.print("ID Excel: " + idExcel + " -> ");
                            if (faltaSalida) System.out.print("[FALTA IMAGEN SALIDA] ");
                            if (faltaDescarga) System.out.print("[FALTA IMAGEN DESCARGA]");
                            System.out.println(); // Salto de línea
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}