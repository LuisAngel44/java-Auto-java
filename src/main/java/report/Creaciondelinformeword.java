package report;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;

import controller.Controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;


public class Creaciondelinformeword {

	public static void main(String[] args) throws InterruptedException {
		// TODO Auto-generated method stub
		// --- CONFIGURACIÓN ---
		Controller controller12=new Controller();
 	   String ITEM=controller12.ElegirITEM();
		
        String rutaExcel = "src/main/resources/Excel_"+ITEM+".xlsx"; 
        String carpetaImagenes = "src/main/resources/img/ITEM"+ITEM+"/"; 
        String rutaSalidaWord = "src/main/resources/word/ITEM"+ITEM+"/Reporte_Final_Consolidado"+ITEM+".docx";
        // ---------------------
        System.out.println("REPORTE GENERADO EN: " +rutaSalidaWord +rutaExcel+carpetaImagenes);
        generarReporteMasivo(rutaExcel, carpetaImagenes, rutaSalidaWord);
    }

   public static void generarReporteMasivo(String rutaExcel, String dirImg, String rutaSalida) {
        try (FileInputStream fis = new FileInputStream(new File(rutaExcel));
             Workbook workbook = new XSSFWorkbook(fis);
             XWPFDocument document = new XWPFDocument()) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Saltamos la cabecera
            if (rowIterator.hasNext()) rowIterator.next();

            int contador = 1;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getCell(5) == null) continue;

                String nombreColegio = getValorCelda(row, 1);
                String departamento = getValorCelda(row, 2);
                String provincia = getValorCelda(row, 3);
                String distrito = getValorCelda(row, 4);
                String codigoLocal = getValorCelda(row, 5); 

                String maxIn = getValorCelda(row, 6);
                String minIn = getValorCelda(row, 7);
                String avgIn = getValorCelda(row, 8);
                
                String maxOut = getValorCelda(row, 9);
                String minOut = getValorCelda(row, 10);
                String avgOut = getValorCelda(row, 11);

                String rutaImgDescarga = buscarImagen(dirImg, "Descarga", codigoLocal);
                String rutaImgSalida = buscarImagen(dirImg, "Salida", codigoLocal);

                // Pasamos contador como String
                agregarSeccionAlDocumento(document, String.valueOf(contador), nombreColegio, departamento, provincia, distrito, codigoLocal,
                                          maxIn, minIn, avgIn, maxOut, minOut, avgOut,
                                          rutaImgDescarga, rutaImgSalida);

                document.createParagraph().setPageBreak(true);
                
                System.out.println("Procesado Item " + contador + ": " + nombreColegio);
                contador++;
            }

            try (FileOutputStream out = new FileOutputStream(rutaSalida)) {
                document.write(out);
            }
            System.out.println("REPORTE GENERADO EN: " + rutaSalida);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- MÉTODOS DE GENERACIÓN WORD (VERSIÓN COMPACTA) ---

    private static void agregarSeccionAlDocumento(XWPFDocument doc, String nItem, String nombre, String dep, String prov, String dist, String cod,
                                                  String maxIn, String minIn, String avgIn,
                                                  String maxOut, String minOut, String avgOut,
                                                  String imgIn, String imgOut) throws Exception {

        XWPFParagraph pTit = doc.createParagraph();
        pTit.setSpacingAfter(0); 
        XWPFRun rTit = pTit.createRun();
        rTit.setText(" "); 
        rTit.setFontSize(6);

        XWPFTable table = doc.createTable(3, 10); 
        table.setWidth("100%");
        table.setCellMargins(20, 20, 20, 20); 

        // CABECERAS
        XWPFTableRow header = table.getRow(0);
        header.getCell(0).setText("N°");
        header.getCell(1).setText("LOCAL EDUCATIVO");
        header.getCell(2).setText("DEPARTAMENTO");
        header.getCell(3).setText("PROVINCIA");
        header.getCell(4).setText("DISTRITO");
        header.getCell(5).setText("CODIGO LOCAL");
        header.getCell(6).setText("Categoría");
        header.getCell(7).setText("Máximo");
        header.getCell(8).setText("Mínimo");
        header.getCell(9).setText("Promedio");
        estilarCabecera(header);

        // FILA IN
        XWPFTableRow rowIn = table.getRow(1);
        rowIn.getCell(0).setText(nItem);
        rowIn.getCell(1).setText(nombre);
        rowIn.getCell(2).setText(dep);
        rowIn.getCell(3).setText(prov);
        rowIn.getCell(4).setText(dist);
        rowIn.getCell(5).setText(cod);
        rowIn.getCell(6).setText("Traffic IN");
        rowIn.getCell(7).setText(maxIn);
        rowIn.getCell(8).setText(minIn);
        rowIn.getCell(9).setText(avgIn);

        // FILA OUT (MERGE)
        XWPFTableRow rowOut = table.getRow(2);
        for (int i = 0; i <= 5; i++) {
            rowIn.getCell(i).getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.RESTART);
            rowIn.getCell(i).getCTTc().addNewTcPr().addNewVAlign().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc.CENTER);
            rowOut.getCell(i).getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.CONTINUE);
        }
        rowOut.getCell(6).setText("Traffic OUT");
        rowOut.getCell(7).setText(maxOut);
        rowOut.getCell(8).setText(minOut);
        rowOut.getCell(9).setText(avgOut);

        // IMÁGENES COMPACTAS (SIN SALTOS)
        insertarImagenConTitulo(doc, "Tráfico IN (Entrada)", imgIn, "Nota: Gráfico de descarga");
        insertarImagenConTitulo(doc, "Tráfico OUT (Salida)", imgOut, "Nota: Gráfico de subida");
    }

    private static void insertarImagenConTitulo(XWPFDocument doc, String titulo, String rutaImg, String nota) {
        
        // TÍTULO PEQUEÑO
        XWPFParagraph pTit = doc.createParagraph();
        pTit.setAlignment(ParagraphAlignment.CENTER);
        pTit.setSpacingBefore(100); 
        pTit.setSpacingAfter(0);    
        
        XWPFRun rTit = pTit.createRun();
        rTit.setText(titulo);
        rTit.setBold(true);
        rTit.setColor("2E74B5");
        rTit.setFontSize(10); 

        // IMAGEN PEQUEÑA (170 de alto)
        XWPFParagraph pImg = doc.createParagraph();
        pImg.setAlignment(ParagraphAlignment.CENTER);
        pImg.setSpacingBefore(0);
        pImg.setSpacingAfter(0);
        
        if (rutaImg != null && new File(rutaImg).exists()) {
            try (InputStream is = new FileInputStream(rutaImg)) {
                XWPFRun run = pImg.createRun();
                run.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG, "img", Units.toEMU(450), Units.toEMU(170));
            } catch (Exception e) { }
        } else {
            XWPFRun run = pImg.createRun();
            run.setText("[SIN IMG]");
            run.setFontSize(8);
        }

        // NOTA PEQUEÑA
        XWPFParagraph pNota = doc.createParagraph();
        pNota.setAlignment(ParagraphAlignment.CENTER);
        pNota.setSpacingBefore(0); 
        pNota.setSpacingAfter(0);  
        
        XWPFRun rNota = pNota.createRun();
        rNota.setText(nota);
        rNota.setFontSize(8);
        rNota.setBold(true);
    }

    // --- UTILITARIOS ---

    private static String buscarImagen(String carpeta, String tipo, String codigo) {
        File folder = new File(carpeta);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.contains(tipo) && name.contains(codigo)) {
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static void estilarCabecera(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            cell.setColor("D9E2F3"); 
            cell.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = cell.getParagraphs().get(0).createRun();
            r.setBold(true);
            r.setFontSize(9);
        }
    }

    private static String getValorCelda(Row row, int indice) {
        Cell cell = row.getCell(indice);
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                double val = cell.getNumericCellValue();
                if (val == (long) val) return String.format("%d", (long) val);
                return String.valueOf(val);
            case FORMULA:
                try {
                    if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                         double fVal = cell.getNumericCellValue();
                         if (fVal == (long) fVal) return String.format("%d", (long) fVal);
                         return String.valueOf(fVal);
                    } else return cell.getStringCellValue();
                } catch(Exception e) { return ""; }
            case ERROR: return "-"; 
            default: return "";
        }
    }
}