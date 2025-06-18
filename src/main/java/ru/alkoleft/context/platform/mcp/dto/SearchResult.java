package ru.alkoleft.context.platform.mcp.dto;

import lombok.Data;

/**
 * Результат поиска по API платформы 1С Предприятие
 */
@Data
public class SearchResult {

    private String name;
    private SearchResultType type;  // method, property, type
    private String signature;
    private String description;
    private Object originalObject; // MethodDefinition, PropertyDefinition, BaseTypeDefinition
    private int score; // Релевантность результата (0-100)

    public SearchResult(String name, SearchResultType type, String signature, String description, Object originalObject) {
        this.name = name;
        this.type = type;
        this.signature = signature;
        this.description = description;
        this.originalObject = originalObject;
        this.score = 0;
    }
}

