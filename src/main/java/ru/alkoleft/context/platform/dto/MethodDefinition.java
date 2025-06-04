package ru.alkoleft.context.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
public final class MethodDefinition extends MethodSignature {
  private final String name;
  private final Signature[] signature;
  @JsonProperty("return")
  private final String returnType;

  @Builder
  public MethodDefinition(String description, ParameterDefinition[] params, String returnType, String name, Signature[] signature) {
    super(description, params);
    this.name = name;
    this.signature = signature;
    this.returnType = returnType;
  }
}
