package ru.alkoleft.context.platform.dto;

import java.util.List;

public record BaseTypeDefinition(
  String name,
  String description,
  List<MethodDefinition> methods,
  List<PropertyDefinition> properties
) {
}
