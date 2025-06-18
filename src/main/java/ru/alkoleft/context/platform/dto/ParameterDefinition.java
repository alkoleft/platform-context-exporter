package ru.alkoleft.context.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParameterDefinition(
        boolean required,
        String name,
        String description,
        String type
) {
}
