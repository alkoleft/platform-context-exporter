package ru.alkoleft.context.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertyDefinition(
        String name,
        String nameEn,
        String description,
        boolean readonly,
        String type
) {
} 