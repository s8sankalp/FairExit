package com.sankalp.fairexit.service.vault;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

/** Small real files for tests. */
public final class TestFiles {

    private TestFiles() {
    }

    /** A real 1x1 pixel PNG. */
    public static byte[] png() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    /** A PDF with a text layer, one string per page, made with OpenPDF. */
    public static byte[] pdf(List<String> pages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                document.newPage();
            }
            document.add(new Paragraph(pages.get(i)));
        }
        document.close();
        return out.toByteArray();
    }
}
