package report; // <--- Importante: indica la carpeta donde está el archivo

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
// import java.util.List; // No es estrictamente necesario si no usas List explícitamente

public class wordApoyo {

    // Este es el método principal que se ejecuta al darle "Run"
    public static void main(String[] args) {
        corregirNumeracionWord();
    }

    public static void corregirNumeracionWord() {
    	// --- 2. CONFIGURACIÓN PARA ARCHIVOS GRANDES ---
        // Esto evita el error de "Malicious file" por tener muchas tablas
        ZipSecureFile.setMinInflateRatio(0);
        ZipSecureFile.setMaxFileCount(50000); // Subimos el límite de 1000 a 50000
        // ---------------------------------------------
    	
        // Asegúrate de que la ruta sea correcta. Si el archivo está en la raíz del proyecto, esto funciona.
        String archivoEntrada = "C:/Users/SVTECHer06/Documents/svtech-datos/informe.docx"; 
        String archivoSalida  = "C:/Users/SVTECHer06/Documents/svtech-datos/informe1.docx";

        try {
            System.out.println("Abriendo documento pesado, por favor espera...");
            FileInputStream fis = new FileInputStream(archivoEntrada);
            XWPFDocument doc = new XWPFDocument(fis);

            int contador = 1;
            int tablasCorregidas = 0;

            // Recorremos TODAS las tablas
            for (XWPFTable table : doc.getTables()) {
                
                // FILTRO: Solo tablas que tengan "LOCAL" en la cabecera
                if (table.getRows().size() > 1 && 
                    table.getRow(0).getCell(1) != null && 
                    table.getRow(0).getCell(1).getText().toUpperCase().contains("LOCAL")) {

                    XWPFTableRow filaDatos = table.getRow(1);
                    XWPFTableCell celdaNumero = filaDatos.getCell(0);

                    if (celdaNumero != null) {
                        // Borrar contenido anterior
                        for (XWPFParagraph p : celdaNumero.getParagraphs()) {
                            for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                                p.removeRun(i);
                            }
                        }
                        
                        if (celdaNumero.getParagraphs().isEmpty()) {
                            celdaNumero.addParagraph();
                        }

                        // Poner nuevo número
                        XWPFRun run = celdaNumero.getParagraphArray(0).createRun();
                        run.setText(String.valueOf(contador));
                        // run.setBold(true); 
                        
                        contador++;
                        tablasCorregidas++;
                        
                        // Imprimimos cada 100 para que veas que avanza
                        if (tablasCorregidas % 100 == 0) {
                            System.out.println("Procesadas " + tablasCorregidas + " tablas...");
                        }
                    }
                }
            }

            System.out.println("Guardando archivo (esto puede tardar un poco)...");
            FileOutputStream out = new FileOutputStream(archivoSalida);
            doc.write(out);
            out.close();
            doc.close();

            System.out.println("¡LISTO! Se numeraron " + tablasCorregidas + " tablas.");
            System.out.println("Archivo generado: " + archivoSalida);

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}