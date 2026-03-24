package programas;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GeneradorReporteWord {

    // ÍNDICES DE COLUMNAS EN TU EXCEL (Ajusta si es necesario)
    static final int COL_COD_LOCAL = 0;
    static final int COL_TICKET = 1;
    static final int COL_MOTIVO = 2;
    static final int COL_RUTAS_IMG = 3;

    // CLASE AUXILIAR PARA ALMACENAR Y ORDENAR LOS DATOS
    static class RegistroTicket {
        String codLocal;
        String ticket;
        String motivo;
        String rutasImagenes;

        public RegistroTicket(String codLocal, String ticket, String motivo, String rutasImagenes) {
            this.codLocal = codLocal;
            this.ticket = ticket;
            this.motivo = motivo;
            this.rutasImagenes = rutasImagenes;
        }
    }

    public static void main(String[] args) {
        String excelEntrada = "datos_informe_tickets.xlsx"; // <-- Cambia esto por la ruta de tu Excel
        String wordSalida = "Reporte_Tickets_Ordenado.docx";

        List<RegistroTicket> listaTickets = new ArrayList<>();

        System.out.println(">>> 🚀 INICIANDO LECTURA DEL EXCEL <<<");

        // 1. LEER EL EXCEL
        try (FileInputStream fis = new FileInputStream(new File(excelEntrada));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                String codLocal = formatter.formatCellValue(row.getCell(COL_COD_LOCAL)).trim();
                String ticket = formatter.formatCellValue(row.getCell(COL_TICKET)).trim();
                String motivo = formatter.formatCellValue(row.getCell(COL_MOTIVO)).trim();
                String rutasImg = formatter.formatCellValue(row.getCell(COL_RUTAS_IMG)).trim();

                if (!ticket.isEmpty()) {
                    listaTickets.add(new RegistroTicket(codLocal, ticket, motivo, rutasImg));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error leyendo el Excel: " + e.getMessage());
            return;
        }

        // 2. ORDENAR LA LISTA POR TICKET
        System.out.println("   🔄 Ordenando " + listaTickets.size() + " tickets...");
        listaTickets.sort(Comparator.comparing(t -> t.ticket));

        // 3. GENERAR EL WORD
        System.out.println(">>> 📝 GENERANDO DOCUMENTO WORD <<<");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(new File(wordSalida))) {

            for (RegistroTicket reg : listaTickets) {
                // Título del Código Local
                XWPFParagraph pCodLocal = doc.createParagraph();
                XWPFRun rCodLocal = pCodLocal.createRun();
                rCodLocal.setBold(true);
                rCodLocal.setText("CODIGO LOCAL: " + reg.codLocal);

                // Título del Ticket
                XWPFParagraph pTicket = doc.createParagraph();
                XWPFRun rTicket = pTicket.createRun();
                rTicket.setBold(true);
                rTicket.setText("TICKET: " + reg.ticket);

                // Motivo
                XWPFParagraph pMotivo = doc.createParagraph();
                XWPFRun rMotivo = pMotivo.createRun();
                rMotivo.setBold(true);
                rMotivo.setText("MOTIVO: ");
                XWPFRun rMotivoTexto = pMotivo.createRun();
                rMotivoTexto.setText(reg.motivo);

                // Procesar Imágenes (soporta múltiples separadas por ";" o ",")
                if (!reg.rutasImagenes.isEmpty()) {
                    String[] rutas = reg.rutasImagenes.replace(";", ",").split(",");
                    
                    XWPFParagraph pImagen = doc.createParagraph();
                    pImagen.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun rImagen = pImagen.createRun();

                    for (String ruta : rutas) {
                        File imgFile = new File(ruta.trim());
                        if (imgFile.exists()) {
                            insertarImagenEnWord(rImagen, imgFile);
                        } else {
                            System.err.println("   ⚠️ Imagen no encontrada: " + imgFile.getAbsolutePath());
                        }
                    }
                }

                // Añadir un salto de línea y una línea separadora entre tickets
                XWPFParagraph pSeparador = doc.createParagraph();
                XWPFRun rSeparador = pSeparador.createRun();
                rSeparador.setText("--------------------------------------------------");
                rSeparador.addBreak();
            }

            doc.write(fos);
            System.out.println("✅ PROCESO FINALIZADO. Archivo creado: " + wordSalida);

        } catch (Exception e) {
            System.err.println("❌ Error generando el Word: " + e.getMessage());
        }
    }

    // MÉTODO AUXILIAR PARA REDIMENSIONAR E INSERTAR LA IMAGEN
    private static void insertarImagenEnWord(XWPFRun run, File imgFile) {
        try {
            BufferedImage bimg = ImageIO.read(imgFile);
            int width = bimg.getWidth();
            int height = bimg.getHeight();

            // Escalar la imagen para que no desborde la página de Word (max ~450px de ancho)
            double scale = 1.0;
            if (width > 450) {
                scale = 450.0 / width;
            }
            int wordWidth = (int) (width * scale);
            int wordHeight = (int) (height * scale);

            int tipoImg = getPictureType(imgFile.getName());

            try (FileInputStream is = new FileInputStream(imgFile)) {
                run.addBreak(); // Salto de línea antes de la imagen
                run.addPicture(is, tipoImg, imgFile.getName(), Units.toEMU(wordWidth), Units.toEMU(wordHeight));
                run.addBreak(); // Salto de línea después de la imagen
            }
        } catch (Exception e) {
            System.err.println("   ❌ Error insertando imagen " + imgFile.getName() + ": " + e.getMessage());
        }
    }

    // MÉTODO AUXILIAR PARA DETERMINAR EL TIPO DE IMAGEN PARA POI
    private static int getPictureType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) return Document.PICTURE_TYPE_PNG;
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return Document.PICTURE_TYPE_JPEG;
        if (lowerName.endsWith(".gif")) return Document.PICTURE_TYPE_GIF;
        if (lowerName.endsWith(".bmp")) return Document.PICTURE_TYPE_BMP;
        return Document.PICTURE_TYPE_JPEG; // Por defecto
    }
}