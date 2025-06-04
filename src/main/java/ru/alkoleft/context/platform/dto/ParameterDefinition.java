package ru.alkoleft.context.platform.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParameterDefinition {
  boolean required;
  String name;
  String description;
  String type;
}
