package ru.alkoleft.context.platform.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class Signature extends MethodSignature {
  String name;

  @Builder
  public Signature(String name, String description, ParameterDefinition[] params) {
    super(description, params);
    this.name = name;
  }
}
