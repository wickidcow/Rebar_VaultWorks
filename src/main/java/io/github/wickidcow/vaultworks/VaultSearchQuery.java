package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record VaultSearchQuery(
        List<String> nameTerms,
        List<String> namespaces,
        boolean requireOnline,
        boolean requireOffline
) {

    static VaultSearchQuery parse(String raw, Locale locale) {
        String search = raw == null ? "" : raw.trim().toLowerCase(locale);
        if (search.isBlank()) {
            return new VaultSearchQuery(List.of(), List.of(), false, false);
        }

        List<String> names = new ArrayList<>();
        List<String> namespaces = new ArrayList<>();
        boolean online = false;
        boolean offline = false;

        for (String piece : search.split("\\s+")) {
            if (piece.isBlank()) {
                continue;
            }

            if (piece.equals("#online")) {
                online = true;
            } else if (piece.equals("#offline")) {
                offline = true;
            } else if (piece.startsWith("@") && piece.length() > 1) {
                namespaces.add(piece.substring(1));
            } else if (!piece.equals("@")) {
                names.add(piece);
            }
        }

        return new VaultSearchQuery(
                List.copyOf(names),
                List.copyOf(namespaces),
                online,
                offline
        );
    }

    boolean matches(String displayName, String namespace, long totalStored, long accessibleStored) {
        String safeName = displayName == null ? "" : displayName;
        String safeNamespace = namespace == null ? "" : namespace;

        for (String term : nameTerms) {
            if (!safeName.contains(term)) {
                return false;
            }
        }

        for (String wantedNamespace : namespaces) {
            if (!safeNamespace.contains(wantedNamespace)) {
                return false;
            }
        }

        if (requireOnline && accessibleStored <= 0L) {
            return false;
        }

        if (requireOffline && totalStored <= accessibleStored) {
            return false;
        }

        return true;
    }
}
