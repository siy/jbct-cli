package org.pragmatica.jbct.config;

import org.pragmatica.lang.Result;
import org.pragmatica.lang.utils.Causes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Simple TOML parser supporting INI-like subset:
 * - Sections: [section] and [section.subsection]
 * - Properties: key = value
 * - Strings: "quoted" or unquoted
 * - Booleans: true/false
 * - Integers: 123
 * - Arrays: ["a", "b", "c"]
 * - Comments: # comment
 */
public final class TomlParser {

    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\[([a-zA-Z0-9_.]+)]$");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^([a-zA-Z0-9_-]+)\\s*=\\s*(.+)$");

    private TomlParser() {}

    /**
     * Parse TOML content into a nested map structure.
     * Returns Map where keys are section paths (e.g., "lint.rules") and values are property maps.
     */
    public static Result<Map<String, Map<String, Object>>> parse(String content) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("", new LinkedHashMap<>()); // Root section

        String currentSection = "";
        int lineNumber = 0;

        for (String rawLine : content.split("\n")) {
            lineNumber++;
            String line = rawLine.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Check for section header
            var sectionMatcher = SECTION_PATTERN.matcher(line);
            if (sectionMatcher.matches()) {
                currentSection = sectionMatcher.group(1);
                result.putIfAbsent(currentSection, new LinkedHashMap<>());
                continue;
            }

            // Check for key = value
            var kvMatcher = KEY_VALUE_PATTERN.matcher(line);
            if (kvMatcher.matches()) {
                String key = kvMatcher.group(1);
                String rawValue = kvMatcher.group(2).trim();

                var parseResult = parseValue(rawValue, lineNumber);
                if (parseResult.isFailure()) {
                    return parseResult.mapError(c -> c).map(_ -> null);
                }

                result.get(currentSection).put(key, parseResult.unwrap());
                continue;
            }

            return Causes.cause("Invalid TOML syntax at line " + lineNumber + ": " + line).result();
        }

        return Result.success(result);
    }

    private static Result<Object> parseValue(String value, int lineNumber) {
        // Boolean
        if ("true".equals(value)) {
            return Result.success(true);
        }
        if ("false".equals(value)) {
            return Result.success(false);
        }

        // Quoted string
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return Result.success(value.substring(1, value.length() - 1));
        }

        // Array
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseArray(value.substring(1, value.length() - 1), lineNumber);
        }

        // Integer
        try {
            return Result.success(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // Not an integer, treat as unquoted string
        }

        // Unquoted string
        return Result.success(value);
    }

    private static Result<Object> parseArray(String content, int lineNumber) {
        List<Object> items = new ArrayList<>();
        if (content.trim().isEmpty()) {
            return Result.success(items);
        }

        // Simple array parsing - split by comma, handle quoted strings
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                var itemResult = parseValue(current.toString().trim(), lineNumber);
                if (itemResult.isFailure()) {
                    return itemResult;
                }
                items.add(itemResult.unwrap());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        // Add last item
        if (!current.toString().trim().isEmpty()) {
            var itemResult = parseValue(current.toString().trim(), lineNumber);
            if (itemResult.isFailure()) {
                return itemResult;
            }
            items.add(itemResult.unwrap());
        }

        return Result.success(items);
    }

    /**
     * Get a string value from parsed TOML.
     */
    public static String getString(Map<String, Map<String, Object>> toml, String section, String key, String defaultValue) {
        var sectionMap = toml.get(section);
        if (sectionMap == null) {
            return defaultValue;
        }
        var value = sectionMap.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Get an integer value from parsed TOML.
     */
    public static int getInt(Map<String, Map<String, Object>> toml, String section, String key, int defaultValue) {
        var sectionMap = toml.get(section);
        if (sectionMap == null) {
            return defaultValue;
        }
        var value = sectionMap.get(key);
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Get a boolean value from parsed TOML.
     */
    public static boolean getBoolean(Map<String, Map<String, Object>> toml, String section, String key, boolean defaultValue) {
        var sectionMap = toml.get(section);
        if (sectionMap == null) {
            return defaultValue;
        }
        var value = sectionMap.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s);
        }
        return defaultValue;
    }

    /**
     * Get a string list from parsed TOML.
     */
    @SuppressWarnings("unchecked")
    public static List<String> getStringList(Map<String, Map<String, Object>> toml, String section, String key, List<String> defaultValue) {
        var sectionMap = toml.get(section);
        if (sectionMap == null) {
            return defaultValue;
        }
        var value = sectionMap.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return defaultValue;
    }

    /**
     * Get all key-value pairs from a section.
     */
    public static Map<String, String> getSection(Map<String, Map<String, Object>> toml, String section) {
        var sectionMap = toml.get(section);
        if (sectionMap == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : sectionMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toString());
        }
        return result;
    }
}
