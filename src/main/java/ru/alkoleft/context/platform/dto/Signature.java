package ru.alkoleft.context.platform.dto;

import java.util.List;

public record Signature(
        String name,
        String description,
        List<ParameterDefinition> params
) implements ISignature {

  public String getType() {
    // Для Signature возвращаем строковое представление параметров
    if (params != null && !params.isEmpty()) {
      return params.get(0).type(); // Возвращаем тип первого параметра или составной тип
    }
    return "Unknown";
  }
}
