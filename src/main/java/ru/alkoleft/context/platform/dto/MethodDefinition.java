package ru.alkoleft.context.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MethodDefinition(
    String description,
    List<ParameterDefinition> params,
    String name,
    @JsonProperty("name_en") String nameEn,
    List<Signature> signature,
    @JsonProperty("return") String returnType
) {
}
