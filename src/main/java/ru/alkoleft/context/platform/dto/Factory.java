package ru.alkoleft.context.platform.dto;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class Factory {
  public MethodDefinition[] methods(ContextType context) {
    if (context.methods().isEmpty()) {
      return null;
    }
    return context.methods().stream()
      .map(Factory::method)
      .toArray(MethodDefinition[]::new);
  }

  public PropertyDefinition[] properties(ContextType context) {
    if (context.properties().isEmpty()) {
      return null;
    }
    return context.properties().stream()
      .map(Factory::property)
      .toArray(PropertyDefinition[]::new);
  }

  public PropertyDefinition property(ContextProperty property) {
    return PropertyDefinition.builder()
      .name(property.name().getName())
      .description(property.description())
      .type(returnType(property.types()))
      .readonly(property.accessMode() == AccessMode.READ)
      .build();
  }

  public MethodDefinition method(ContextMethod method) {
    return MethodDefinition.builder()
      .name(method.name().getName())
      .description(method.description())
      .signature(signatures(method))
      .returnType(returnType(method))
      .build();
  }

  private Signature[] signatures(ContextMethod method) {
    return method.signatures().stream()
      .map(s -> Signature.builder()
        .description(s.description())
        .name(s.name().getAlias())
        .params(s.parameters().stream()
          .map(Factory::parameter)
          .toArray(ParameterDefinition[]::new))
        .build())
      .toArray(Signature[]::new);
  }

  public String returnType(ContextMethod method) {
    return returnType(method.returnValues());
  }

  public String returnType(List<Context> types) {
    return types.isEmpty() ? null : types.get(0).name().getName();
  }

  public ParameterDefinition parameter(ContextSignatureParameter parameter) {
    return ParameterDefinition.builder()
      .name(parameter.name().getName())
      .description(parameter.description())
      .required(parameter.isRequired())
      .type(returnType(parameter.types()))
      .build();
  }

  public static Signature[] constructors(PlatformContextType context) {
    if (context.constructors().isEmpty()) {
      return null;
    }
    return context.constructors().stream()
      .map(s -> Signature.builder()
        .description(s.description())
        .name(s.name().getAlias())
        .params(s.parameters().stream()
          .map(Factory::parameter)
          .toArray(ParameterDefinition[]::new))
        .build())
      .toArray(Signature[]::new);
  }
}
