package org.pragmatica.jbct.slice.model;

import org.pragmatica.lang.Option;

import java.util.List;

/**
 * Aspect configuration extracted from @Aspect annotation on a slice method.
 *
 * @param kinds        List of AspectKind values as strings (e.g., "CACHE", "LOG")
 * @param keyExtractor Key extractor info for CACHE aspect (if present)
 */
public record AspectModel(List<String> kinds, Option<KeyExtractorInfo> keyExtractor) {
    public AspectModel {
        kinds = List.copyOf(kinds);
    }

    /**
     * Create an empty aspect model (no aspects).
     */
    public static AspectModel none() {
        return new AspectModel(List.of(), Option.none());
    }

    /**
     * Check if CACHE aspect is present.
     */
    public boolean hasCache() {
        return kinds.contains("CACHE");
    }

    /**
     * Check if any aspects are present.
     */
    public boolean hasAspects() {
        return !kinds.isEmpty();
    }
}
