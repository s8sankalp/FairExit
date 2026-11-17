package com.sankalp.fairexit.service.pack;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sankalp.fairexit.dto.AgreementResponse;
import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.dto.DocumentResponse;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.service.audit.Rupees;
import com.sankalp.fairexit.service.vault.ChainVerifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Draws the evidence pack with OpenPDF: tenancy summary, the clauses used, the audit
 * table, every vault document with its hashes, the photos, and how to verify them.
 *
 * Amounts are written "Rs. 1,45,300": the PDF's built in fonts have no ₹ sign.
 */
public final class EvidencePackPdf {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font MONO = FontFactory.getFont(FontFactory.COURIER, 6.5f);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss 'IST'", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));
    static final int MAX_PHOTOS = 20;

    private EvidencePackPdf() {
    }

    public static byte[] build(PackContent c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 36, 36, 42, 42);
        try {
            PdfWriter.getInstance(pdf, out);
            pdf.addTitle("FairExit evidence pack");
            pdf.open();

            pdf.add(new Paragraph("FairExit evidence pack", TITLE));
            pdf.add(new Paragraph("Created " + TIME.format(c.createdAt()) + ". This is information, not legal advice.",
                    BODY));

            tenancy(pdf, c);
            integrity(pdf, c);
            audit(pdf, c.audit());
            clauses(pdf, c.agreement());
            documents(pdf, c.documents());
            photos(pdf, c);
            howToVerify(pdf);

            pdf.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not build the evidence pack", e);
        }
    }

    private static void tenancy(Document pdf, PackContent c) throws DocumentException {
        Tenancy t = c.tenancy();
        heading(pdf, "1. Tenancy");
        PdfPTable table = table(2, 1f, 3f);
        row(table, "Tenant", nullToDash(c.tenantName()));
        row(table, "Home", t.getFlatSize().label() + " in " + t.getCity() + ", " + t.getState().displayName());
        row(table, "Rent", rs(t.getRent()) + " a month");
        row(table, "Deposit", rs(t.getDeposit()));
        row(table, "Dates", t.getStartDate() + " to " + (t.getEndDate() == null ? "today" : t.getEndDate()));
        row(table, "Notice period", t.getNoticeDays() == null ? "-" : t.getNoticeDays() + " days");
        pdf.add(table);
    }

    private static void integrity(Document pdf, PackContent c) throws DocumentException {
        heading(pdf, "2. Integrity check");
        if (c.verify().valid()) {
            pdf.add(new Paragraph("Verified when this pack was made: all " + c.verify().documentsChecked()
                    + " documents match their stored SHA-256 hashes and the hash chain is unbroken.", BOLD));
        } else {
            pdf.add(new Paragraph("WARNING: the check found " + c.verify().breaks().size()
                    + " problem(s) when this pack was made:", BOLD));
            for (ChainVerifier.Break b : c.verify().breaks()) {
                pdf.add(new Paragraph("  " + b.problem() + (b.chainSeq() == null ? "" : " at record " + b.chainSeq())
                        + ": " + b.detail(), BODY));
            }
        }
        pdf.add(new Paragraph("Chain head (hash of the last record): ", BODY));
        pdf.add(new Paragraph(nullToDash(c.verify().chainHead()), MONO));
    }

    private static void audit(Document pdf, AuditResponse a) throws DocumentException {
        heading(pdf, "3. Deduction audit");
        pdf.add(new Paragraph("Rules version " + a.rulesVersion() + ", coverage " + a.rulesCoverage()
                + ", tenancy length " + a.tenancyMonths() + " months.", BODY));
        PdfPTable table = table(4, 2.2f, 1.1f, 1.5f, 4.2f);
        header(table, "Charge", "Amount", "Verdict", "Reason");
        for (AuditResponse.LineVerdict v : a.verdicts()) {
            cell(table, v.item(), BODY);
            cell(table, rs(v.amount()), BODY);
            cell(table, v.verdict().name().replace('_', ' '), BOLD);
            cell(table, plain(v.reason()) + "  [" + String.join(", ", v.ruleIds()) + "]", BODY);
        }
        pdf.add(table);

        PdfPTable totals = table(2, 3f, 1.4f);
        row(totals, "Total claimed", rs(a.totals().claimed()));
        row(totals, "Fair", rs(a.totals().fair()));
        row(totals, "Questionable or not in agreement", rs(a.totals().questionable()));
        row(totals, "Needs more information", rs(a.totals().needsInfo()));
        row(totals, "Suggested refund", rs(a.totals().suggestedRefund()));
        totals.setWidthPercentage(60);
        totals.setHorizontalAlignment(Element.ALIGN_LEFT);
        pdf.add(totals);
    }

    private static void clauses(Document pdf, AgreementResponse agreement) throws DocumentException {
        heading(pdf, "4. Agreement clauses used");
        if (agreement == null || agreement.clauses().isEmpty()) {
            pdf.add(new Paragraph("No agreement clauses were on file for this audit.", BODY));
            return;
        }
        for (AgreementResponse.ClauseView clause : agreement.clauses()) {
            pdf.add(new Paragraph(clause.type() + (clause.page() == null ? "" : " (page " + clause.page() + ")")
                    + ", checked against " + clause.checkedAgainst() + ":", BOLD));
            pdf.add(new Paragraph("\"" + clause.quote() + "\"", BODY));
        }
    }

    private static void documents(Document pdf, List<DocumentResponse> documents) throws DocumentException {
        heading(pdf, "5. Documents in the vault");
        if (documents.isEmpty()) {
            pdf.add(new Paragraph("No documents uploaded.", BODY));
            return;
        }
        // A wide last column, so a 64 character hash fits on one line and can be copied whole
        PdfPTable table = table(4, 0.4f, 2.0f, 1.8f, 5.4f);
        header(table, "#", "Document", "Uploaded", "SHA-256 of the file / record hash");
        for (DocumentResponse d : documents) {
            cell(table, String.valueOf(d.chainSeq()), BODY);
            cell(table, d.type() + " " + d.phase() + "\n" + d.fileName()
                    + (d.room() == null ? "" : "\n" + d.room() + (d.item() == null ? "" : ", " + d.item())), BODY);
            cell(table, TIME.format(d.uploadedAt()), BODY);
            cell(table, "file SHA-256:\n" + d.sha256() + "\nrecord hash:\n" + d.recordHash(), MONO);
        }
        pdf.add(table);
    }

    private static void photos(Document pdf, PackContent c) throws DocumentException, IOException {
        List<DocumentResponse> photos = c.documents().stream()
                .filter(d -> d.type() == DocumentType.PHOTO && c.photoBytes().containsKey(d.id()))
                .limit(MAX_PHOTOS)
                .toList();
        if (photos.isEmpty()) {
            return;
        }
        heading(pdf, "6. Photos");
        for (DocumentResponse d : photos) {
            Image image = Image.getInstance(c.photoBytes().get(d.id()));
            image.scaleToFit(260, 200);
            pdf.add(new Paragraph("Record " + d.chainSeq() + ": " + d.phase()
                    + (d.room() == null ? "" : ", " + d.room()) + ", uploaded " + TIME.format(d.uploadedAt()), BOLD));
            pdf.add(image);
        }
    }

    private static void howToVerify(Document pdf) throws DocumentException {
        heading(pdf, "How to check this evidence yourself");
        pdf.add(new Paragraph("1. Ask the tenant for the original file and compute its SHA-256 "
                + "(for example: sha256sum photo.jpg). It must equal the \"file\" hash above.", BODY));
        pdf.add(new Paragraph("2. Each record hash covers the record before it, so no file can be changed, "
                + "removed or swapped without breaking every later hash. The chain head above seals the whole list.",
                BODY));
        pdf.add(new Paragraph("3. FairExit re-checks all of this on demand at GET /api/tenancies/{id}/verify.", BODY));
    }

    // ---- small helpers ----

    private static void heading(Document pdf, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, HEADING);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        pdf.add(p);
    }

    private static PdfPTable table(int columns, float... widths) throws DocumentException {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingBefore(4);
        return table;
    }

    private static void header(PdfPTable table, String... titles) {
        for (String t : titles) {
            cell(table, t, BOLD);
        }
    }

    private static void row(PdfPTable table, String label, String value) {
        cell(table, label, BOLD);
        cell(table, value, BODY);
    }

    private static void cell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(new Chunk(text, font)));
        cell.setPadding(4);
        table.addCell(cell);
    }

    static String rs(BigDecimal amount) {
        return plain(Rupees.format(amount));
    }

    /** Swaps the ₹ sign, which the built in fonts cannot draw, for "Rs. ". */
    static String plain(String text) {
        return text == null ? "" : text.replace("₹", "Rs. ");
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
