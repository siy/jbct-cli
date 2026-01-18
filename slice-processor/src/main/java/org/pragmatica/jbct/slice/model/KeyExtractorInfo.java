package org.pragmatica.jbct.slice.model;

/**
 * Information about cache key extraction from request type.
 *
 * @param keyType             Fully qualified type name of the cache key
 * @param extractorExpression Java expression for key extraction (e.g., "Request::userId" or "request -> request")
 */
public record KeyExtractorInfo(String keyType, String extractorExpression) {
    /**
     * Create extractor for a single @Key-annotated field.
     *
     * @param keyType       Type of the annotated field
     * @param fieldName     Name of the annotated field
     * @param paramTypeName Simple name of the parameter type (for method reference)
     * @return Key extractor using method reference
     */
    public static KeyExtractorInfo single(String keyType, String fieldName, String paramTypeName) {
        return new KeyExtractorInfo(keyType, paramTypeName + "::" + fieldName);
    }

    /**
     * Create identity extractor (use entire request as key).
     *
     * @param requestType Type of the request parameter
     * @return Key extractor using identity lambda
     */
    public static KeyExtractorInfo identity(String requestType) {
        return new KeyExtractorInfo(requestType, "request -> request");
    }
}
