package ru.alkoleft.context.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MethodDefinition(String name, String description, List<Signature> signature,
                               @JsonProperty("return") String returnType
) {


  public TypeDefinition getReturnTypeDefinition() {
    return new TypeDefinition(returnType, "Возвращаемое значение");
  }

  // Вспомогательный класс для типа возврата
  public record TypeDefinition(String name, String description) {
    public String getType() {
      return name;
    }

    public String getDescription() {
      return description;
    }
  }
}
