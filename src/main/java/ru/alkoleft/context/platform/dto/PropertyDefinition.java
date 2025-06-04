package ru.alkoleft.context.platform.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PropertyDefinition {
  String name;
  String description;
  boolean readonly;
  String type;
}
