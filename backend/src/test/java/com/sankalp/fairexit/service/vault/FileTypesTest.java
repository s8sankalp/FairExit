package com.sankalp.fairexit.service.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileTypesTest {

    @Test
    void knownFilesAreRecognisedByTheirFirstBytes() {
        assertThat(FileTypes.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).contains(FileTypes.JPEG);
        assertThat(FileTypes.sniff(TestFiles.png())).contains(FileTypes.PNG);
        assertThat(FileTypes.sniff("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII))).contains(FileTypes.PDF);
    }

    @Test
    void anythingElseIsUnknownWhateverItIsCalled() {
        assertThat(FileTypes.sniff("<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(FileTypes.sniff(new byte[] {'M', 'Z'})).isEmpty(); // an .exe renamed to .jpg
        assertThat(FileTypes.sniff(new byte[0])).isEmpty();
    }
}
