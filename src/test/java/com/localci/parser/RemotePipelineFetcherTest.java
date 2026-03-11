package com.localci.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RemotePipelineFetcher: checksum verification logic.
 */
class RemotePipelineFetcherTest {

    @Test
    void validChecksumPasses() {
        // SHA256 of empty byte array
        byte[] content = new byte[0];
        String expected = "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        assertDoesNotThrow(() -> RemotePipelineFetcher.verifyChecksum(content, expected));
    }

    @Test
    void validChecksumWithoutPrefix() {
        byte[] content = new byte[0];
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        assertDoesNotThrow(() -> RemotePipelineFetcher.verifyChecksum(content, expected));
    }

    @Test
    void invalidChecksumThrows() {
        byte[] content = "hello world".getBytes();
        String expected = "sha256:0000000000000000000000000000000000000000000000000000000000000000";

        IOException ex = assertThrows(IOException.class,
                () -> RemotePipelineFetcher.verifyChecksum(content, expected));
        assertTrue(ex.getMessage().contains("Checksum mismatch"));
    }

    @Test
    void checksumIsCaseInsensitive() {
        byte[] content = new byte[0];
        String expected = "SHA256:E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";

        assertDoesNotThrow(() -> RemotePipelineFetcher.verifyChecksum(content, expected));
    }

    @Test
    void fetchBadUrlThrows() {
        assertThrows(Exception.class,
                () -> RemotePipelineFetcher.fetch("https://this-definitely-does-not-exist.invalid/pipeline.yml", null));
    }
}
