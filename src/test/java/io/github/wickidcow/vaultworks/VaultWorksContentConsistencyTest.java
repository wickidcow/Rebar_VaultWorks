package io.github.wickidcow.vaultworks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultWorksContentConsistencyTest {

    private static final Path MAIN = Path.of(
            "src/main/java/io/github/wickidcow/vaultworks/VaultWorks.java"
    );
    private static final Path LANG = Path.of("src/main/resources/lang/en.yml");

    private static final Pattern ITEM_KEY = Pattern.compile(
            "NamespacedKey\\s+[a-zA-Z0-9_]+Key\\s*=\\s*new NamespacedKey\\(this, \"([a-z0-9_]+)\"\\)"
    );
    private static final Pattern RECIPE = Pattern.compile(
            "recipe\\(new ShapedRecipe\\("
    );
    private static final Pattern GUIDE_ITEM = Pattern.compile(
            "page\\.addItem\\("
    );

    @Test
    void catalogIsUniqueAndNormalized() {
        VaultWorksContentCatalog.validate();
        assertEquals(14, VaultWorksContentCatalog.ALL_IDS.size());
    }

    @Test
    void registeredKeysMatchCanonicalCatalog() throws Exception {
        Set<String> registered = findAll(Files.readString(MAIN), ITEM_KEY);
        assertEquals(VaultWorksContentCatalog.ALL_ID_SET, registered);
    }

    @Test
    void recipeAndGuideCountsCoverCatalog() throws Exception {
        String source = Files.readString(MAIN);
        assertEquals(
                VaultWorksContentCatalog.ALL_IDS.size(),
                count(source, RECIPE),
                "Every player-facing VaultWorks item should have one survival recipe"
        );
        assertEquals(
                VaultWorksContentCatalog.ALL_IDS.size(),
                count(source, GUIDE_ITEM),
                "Every player-facing VaultWorks item should appear in the guide"
        );
    }

    @Test
    void englishMetadataMatchesCanonicalCatalog() throws Exception {
        String yaml = Files.readString(LANG);
        int start = yaml.indexOf("\nitem:\n");
        assertTrue(start >= 0, "lang/en.yml is missing item section");

        String itemSection = yaml.substring(start + "\nitem:\n".length());
        Pattern entry = Pattern.compile("(?m)^  ([a-z0-9_]+):$");
        Matcher matcher = entry.matcher(itemSection);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            assertTrue(ids.add(matcher.group(1)), "Duplicate English item id: " + matcher.group(1));
        }

        assertEquals(VaultWorksContentCatalog.ALL_ID_SET, ids);
    }

    private static Set<String> findAll(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            assertTrue(values.add(matcher.group(1)), "Duplicate id: " + matcher.group(1));
        }
        return values;
    }

    private static int count(String source, Pattern pattern) {
        int count = 0;
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
