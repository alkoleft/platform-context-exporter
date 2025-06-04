package ru.alkoleft.context.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MethodSignature {
  protected final String description;
  protected final ParameterDefinition[] params;
}
