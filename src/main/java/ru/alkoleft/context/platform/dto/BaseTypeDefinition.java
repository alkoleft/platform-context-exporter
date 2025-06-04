package ru.alkoleft.context.platform.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BaseTypeDefinition {
  protected final String name;
  protected final String description;
  protected final MethodDefinition[] methods;
  protected final PropertyDefinition[] properties;
}
