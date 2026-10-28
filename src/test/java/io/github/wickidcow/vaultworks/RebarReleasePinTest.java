package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Keeps the compiled Rebar API and pinned live-server smoke release aligned. */
class RebarReleasePinTest {
    @Test void gradleApiAndCiServerUseTheSameReleasedRebarVersion() throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("gradle.properties"))) {
            properties.load(input);
        }
        String version = properties.getProperty("rebar.version");
        assertNotNull(version);
        assertTrue(version.matches("0\\.44\\.[0-9]+-26\\.2"), "Unexpected Rebar version: " + version);

        String workflow = Files.readString(Path.of(".github/workflows/build.yml"));
        assertTrue(workflow.contains("https://github.com/pylonmc/rebar/releases/download/"
                        + version + "/rebar-" + version + ".jar"),
                "CI smoke server must use the exact Rebar version from gradle.properties");
        assertTrue(workflow.contains("sha256sum -c -"),
                "CI must verify the checksum of the downloaded real-server JAR");
    }
}
