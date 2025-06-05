package ru.alkoleft.context.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MethodDefinition(String name, String description, List<Signature> signature, @JsonProperty("return") String returnType
) {
}
