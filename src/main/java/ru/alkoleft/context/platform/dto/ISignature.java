package ru.alkoleft.context.platform.dto;

import java.util.List;

public interface ISignature {
  String name();

  String description();

  List<ParameterDefinition> params();
}
