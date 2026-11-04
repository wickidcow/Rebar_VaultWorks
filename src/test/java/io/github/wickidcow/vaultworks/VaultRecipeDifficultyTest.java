package io.github.wickidcow.vaultworks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultRecipeDifficultyTest {

    @Test
    void difficultyParserIsCaseInsensitiveAndRejectsTypos() {
        assertEquals(VaultRecipeDifficulty.HARD, VaultRecipeDifficulty.parse(" hard "));
        assertEquals(VaultRecipeDifficulty.BALANCED, VaultRecipeDifficulty.parse("balanced"));
        assertEquals(VaultRecipeDifficulty.CLASSIC, VaultRecipeDifficulty.parse("classic"));
        assertThrows(IllegalArgumentException.class, () -> VaultRecipeDifficulty.parse("HADR"));
        assertThrows(IllegalArgumentException.class, () -> VaultRecipeDifficulty.parse(null));
    }

    @Test
    void pylonIngredientRequirementsAreDeclared() {
        assertFalse(VaultRecipeDifficulty.CLASSIC.requiresPylon());
        assertTrue(VaultRecipeDifficulty.CLASSIC.requiredPylonItems().isEmpty());
        for (VaultRecipeDifficulty difficulty : List.of(VaultRecipeDifficulty.BALANCED, VaultRecipeDifficulty.HARD)) {
            assertTrue(difficulty.requiresPylon());
            assertTrue(difficulty.requiredPylonItems().contains("cargo_controller"));
        }
        assertTrue(VaultRecipeDifficulty.HARD.requiredPylonItems().containsAll(
                List.of("steel_sheet", "capacitor_1_kj", "igneous_composite")));
    }

    @Test
    void hardIsTheDefaultInConfig() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(config.contains("difficulty: HARD"));
    }

    @Test
    void registeredRecipeCountIsUnchanged() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/wickidcow/vaultworks/VaultWorks.java"));
        assertEquals(14, source.split("recipe\\(new ShapedRecipe\\(", -1).length - 1);
    }
}
