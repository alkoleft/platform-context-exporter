package ru.alkoleft.context.platform.dto;

import java.util.List;

public record Signature(
  String name,
  String description,
  List<ParameterDefinition> params
) implements ISignature {
}
