package com.sankalp.fairexit.service;

import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.UserRepository;
import com.sankalp.fairexit.service.llm.PiiMasker;
import com.sankalp.fairexit.service.vault.FileTypes;
import com.sankalp.fairexit.service.vault.PdfText;
import com.sankalp.fairexit.service.vault.VaultService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides how the LLM should see a stored file (spec decision 18):
 * a PDF with a text layer is read with OpenPDF and sent as masked text;
 * a photo, or a scanned PDF with no text layer, is sent as the file itself.
 *
 * Privacy note: text can be masked, an image cannot. A photo of a bill may show a name
 * or phone number, and it reaches the LLM as it is. This is said in the README.
 */
@Component
public class DocumentReader {

    /** Exactly one of the two is set. */
    public record Content(List<String> maskedPages, byte[] fileBytes, String mimeType, String sha256) {

        public boolean isText() {
            return maskedPages != null;
        }
    }

    private final VaultService vault;
    private final UserRepository users;
    private final PiiMasker masker;

    public DocumentReader(VaultService vault, UserRepository users, PiiMasker masker) {
        this.vault = vault;
        this.users = users;
        this.masker = masker;
    }

    public Content read(Document document, UUID userId) {
        byte[] bytes = vault.contentOf(document);
        if (FileTypes.PDF.equals(document.getContentType())) {
            List<String> pages = PdfText.pages(bytes);
            if (PdfText.hasTextLayer(pages)) {
                List<String> names = users.findById(userId).map(User::getName).map(List::of).orElse(List.of());
                return new Content(pages.stream().map(p -> masker.mask(p, names)).toList(), null, null, null);
            }
        }
        return new Content(null, bytes, document.getContentType(), document.getSha256());
    }
}
