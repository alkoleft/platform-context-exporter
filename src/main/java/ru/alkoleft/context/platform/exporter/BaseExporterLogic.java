package ru.alkoleft.context.platform.exporter;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import ru.alkoleft.context.platform.dto.Factory;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.ParameterDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.dto.Signature;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BaseExporterLogic implements ExporterLogic {

  @Override
  public Stream<PropertyDefinition> extractProperties(PlatformGlobalContext context) {
    if (context.properties() == null) {
      return Stream.empty();
    }
    return context.properties().stream()
      .map(property -> {
        var type = (String) null;
        if (property.types() != null && !property.types().isEmpty()) {
          type = property.types().get(0).name().getName();
        }
        return new PropertyDefinition(
          property.name().getName(),
          property.name().getAlias(),
          property.description(),
          AccessMode.READ.equals(property.accessMode()),
          type
        );
      });
  }

  @Override
  public Stream<MethodDefinition> extractMethods(PlatformGlobalContext context) {
    if (context.methods() == null) {
      return Stream.empty();
    }
    return context.methods().stream()
      .filter(method -> method.availabilities().contains(Availability.SERVER) || method.availabilities().contains(Availability.THIN_CLIENT))
      .map(method -> {
        List<Signature> sigs = Collections.emptyList();
        if (method.signatures() != null) {
          sigs = method.signatures().stream()
            .map(sig -> {
              var sigDescription = (String) null;
              if (sig.name().getName().equals("Основной")) {
                sigDescription = sig.description();
              } else {
                sigDescription = sig.name().getName() + ". " + sig.description();
              }

              List<ParameterDefinition> paramsList;
              if (sig.parameters() != null && !sig.parameters().isEmpty()) {
                paramsList = sig.parameters().stream()
                  .map(Factory::parameter)
                  .collect(Collectors.toList());
              } else {
                paramsList = Collections.emptyList();
              }
              return new Signature(sig.name().getAlias(), sigDescription, paramsList);
            })
            .collect(Collectors.toList());
        }

        var returnValue = (String) null;
        if (method.hasReturnValue() && method.returnValues() != null && !method.returnValues().isEmpty()) {
          returnValue = method.returnValues().get(0).name().getName();
        }

        List<ParameterDefinition> firstSignatureParams = sigs.isEmpty()
          ? Collections.emptyList()
          : sigs.get(0).params();

        return new MethodDefinition(
          method.description(),
          firstSignatureParams,
          method.name().getName(),
          method.name().getAlias(),
          sigs,
          returnValue
        );
      });
  }

  @Override
  public Stream<PlatformTypeDefinition> extractTypes(List<Context> contexts) {
    if (contexts == null) {
      return Stream.empty();
    }
    return contexts.stream()
      .filter(PlatformContextType.class::isInstance)
      .map(PlatformContextType.class::cast)
      .map(this::createTypeDefinition);
  }

  PlatformTypeDefinition createTypeDefinition(PlatformContextType context) {
    return new PlatformTypeDefinition(context.name().getName(), null, Factory.methods(context), Factory.properties(context), Factory.constructors(context));
  }
} 