package com.localci.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fetches a remote YAML pipeline definition over HTTP/HTTPS.
 * <p>
 * Supports optional SHA256 checksum verification for integrity.
 * <p>
 * Usage:
 *
 * <pre>
 *   pipeline_ref: "<a href="https://example.com/ci/base-pipeline.yml">...</a>"
 *   checksum: "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
 * </pre>
 */
public class RemotePipelineFetcher {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Downloads a remote YAML file and optionally verifies its checksum.
     *
     * @param url              the URL to fetch from
     * @param expectedChecksum optional SHA256 checksum (format: "sha256:hex..." or
     *                         just hex)
     * @return path to the downloaded temporary file
     * @throws IOException if download fails or checksum doesn't match
     */
    public static String fetch(String url, String expectedChecksum) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " fetching: " + url);
            }

            byte[] content = response.body();

            // Verify checksum if provided
            if (expectedChecksum != null && !expectedChecksum.isBlank()) {
                verifyChecksum(content, expectedChecksum);
            }

            // Write to temp file
            Path tempFile = Files.createTempFile("localci-remote-", ".yml");
            Files.write(tempFile, content);
            tempFile.toFile().deleteOnExit();

            return tempFile.toAbsolutePath().toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + url, e);
        }
    }

    /**
     * Verifies the SHA256 checksum of the downloaded content.
     */
    static void verifyChecksum(byte[] content, String expected) throws IOException {
        String hex = expected.strip();
        if (hex.toLowerCase().startsWith("sha256:")) {
            hex = hex.substring(7);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            String actual = HexFormat.of().formatHex(hash);

            if (!actual.equalsIgnoreCase(hex)) {
                throw new IOException("Checksum mismatch!\n  Expected: " + hex
                        + "\n  Actual:   " + actual);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }
}
