package dev.harbordesk.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Proof tests write one markdown fragment each, scripts/build-report.sh assembles them into docs/proof/report.md. */
public final class ProofReport {
    private ProofReport() {
    }

    public static void write(String name, String markdown) {
        try {
            Path dir = Path.of("target", "proof");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".md"), markdown);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
