package ru.alkoleft.context.platform.dto;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class Factory {
  public List<MethodDefinition> methods(ContextType context) {
    if (context.methods().isEmpty()) {
      return Collections.emptyList();
    }
    return context.methods().stream()
            .map(Factory::method)
            .collect(Collectors.toList());
  }

  public List<PropertyDefinition> properties(ContextType context) {
    if (context.properties().isEmpty()) {
      return Collections.emptyList();
    }
    return context.properties().stream()
            .map(Factory::property)
            .collect(Collectors.toList());
  }

  public PropertyDefinition property(ContextProperty property) {
    return new PropertyDefinition(
            property.name().getName(),
            null, // nameEn
            property.description(),
            property.accessMode() == AccessMode.READ,
            returnType(property.types())
    );
  }

  public MethodDefinition method(ContextMethod method) {
    List<Signature> methodSignatures = signatures(method);

    return new MethodDefinition(
            method.name().getName(),
            method.description(),
            methodSignatures,
            returnType(method)
    );
  }

  private List<Signature> signatures(ContextMethod method) {
    return method.signatures().stream()
            .map(s -> new Signature(
                    s.name().getAlias(),
                    s.description(),
                    s.parameters().stream()
                            .map(Factory::parameter)
                            .collect(Collectors.toList())))
            .collect(Collectors.toList());
  }

  public String returnType(ContextMethod method) {
    return returnType(method.returnValues());
  }

  public String returnType(List<Context> types) {
    return types.isEmpty() ? null : types.get(0).name().getName();
  }

  public ParameterDefinition parameter(ContextSignatureParameter parameter) {
    return new ParameterDefinition(
            parameter.isRequired(),
            parameter.name().getName(),
            parameter.description(),
            returnType(parameter.types())
    );
  }

  public static List<ISignature> constructors(PlatformContextType context) {
    if (context.constructors().isEmpty()) {
      return Collections.emptyList();
    }
    return context.constructors().stream()
            .map(s -> new Signature(
                    s.name().getAlias(),
                    s.description(),
                    s.parameters().stream()
                            .map(Factory::parameter)
                            .collect(Collectors.toList())))
            .collect(Collectors.toList());
  }
}
