package io.github.wickidcow.vaultworks;

import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultSearchQueryTest {

    @Test
    void matchesMultipleNameTerms() {
        VaultSearchQuery query = VaultSearchQuery.parse("ender pearl", Locale.US);
        assertTrue(query.matches("ender pearl", "minecraft", 100, 100));
        assertTrue(query.matches("large ender pearl cache", "minecraft", 100, 100));
        assertFalse(query.matches("ender eye", "minecraft", 100, 100));
    }

    @Test
    void filtersNamespace() {
        VaultSearchQuery query = VaultSearchQuery.parse("@vaultworks cell", Locale.US);
        assertTrue(query.matches("vault cell", "vaultworks", 10, 10));
        assertFalse(query.matches("vault cell", "minecraft", 10, 10));
    }

    @Test
    void filtersAvailability() {
        VaultSearchQuery online = VaultSearchQuery.parse("#online", Locale.US);
        VaultSearchQuery offline = VaultSearchQuery.parse("#offline", Locale.US);

        assertTrue(online.matches("diamond", "minecraft", 100, 20));
        assertFalse(online.matches("diamond", "minecraft", 100, 0));

        assertTrue(offline.matches("diamond", "minecraft", 100, 20));
        assertFalse(offline.matches("diamond", "minecraft", 100, 100));
    }

    @Test
    void onlineAndOfflineTogetherFindPartiallyAccessibleStock() {
        VaultSearchQuery query = VaultSearchQuery.parse("#online #offline", Locale.US);
        assertTrue(query.matches("diamond", "minecraft", 100, 20));
        assertFalse(query.matches("diamond", "minecraft", 100, 0));
        assertFalse(query.matches("diamond", "minecraft", 100, 100));
    }

    @Test
    void standaloneNamespaceMarkerDoesNotHideEverything() {
        VaultSearchQuery query = VaultSearchQuery.parse("@", Locale.US);
        assertTrue(query.matches("diamond", "minecraft", 1, 1));
    }
}
