package ru.alkoleft.context.platform.dto;

import java.util.List;

public record PlatformTypeDefinition(
        String name,
        String description,
        List<MethodDefinition> methods,
        List<PropertyDefinition> properties,
        List<ISignature> constructors
) {
}
