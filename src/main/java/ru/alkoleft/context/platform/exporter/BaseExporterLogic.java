package ru.alkoleft.context.platform.exporter;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.Factory;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.ParameterDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.dto.Signature;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base implementation for exporting platform context data.
 * Provides test data for 1C platform methods and properties demonstration.
 */
@Service
public class BaseExporterLogic implements ExporterLogic {

  private static final String PRIMARY_SIGNATURE_NAME = "Основной";

  @Override
  public Stream<PropertyDefinition> extractProperties(PlatformGlobalContext context) {
    Objects.requireNonNull(context, "PlatformGlobalContext cannot be null");

    return Optional.ofNullable(context.properties())
            .map(List::stream)
            .orElse(Stream.empty())
            .map(property -> {
              String type = Optional.ofNullable(property.types())
                      .filter(types -> !types.isEmpty())
                      .map(types -> types.get(0).name().getName())
                      .orElse(null);

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
    Objects.requireNonNull(context, "PlatformGlobalContext cannot be null");

    return Optional.ofNullable(context.methods()).stream().flatMap(Collection::stream)
            .map(method -> {
              List<Signature> signatures = Optional.ofNullable(method.signatures())
                      .map(sigs -> sigs.stream()
                              .map(this::toSignature)
                              .toList())
                      .orElse(Collections.emptyList());

              String returnValue = null;
              if (method.hasReturnValue() && method.returnValues() != null && !method.returnValues().isEmpty()) {
                returnValue = method.returnValues().get(0).name().getName();
              }

              return new MethodDefinition(
                      method.name().getName(),
                      method.description(),
                      signatures,
                      returnValue
              );
            });
  }

  private Signature toSignature(ContextMethodSignature sig){
    String sigDescription = PRIMARY_SIGNATURE_NAME.equals(sig.name().getName())
            ? sig.description()
            : sig.name().getName() + ". " + sig.description();

    List<ParameterDefinition> paramsList = Optional.ofNullable(sig.parameters())
            .filter(params -> !params.isEmpty())
            .map(params -> params.stream()
                    .map(Factory::parameter)
                    .toList())
            .orElse(Collections.emptyList());

    return new Signature(sig.name().getAlias(), sigDescription, paramsList);
  }

  @Override
  public Stream<PlatformTypeDefinition> extractTypes(List<Context> contexts) {
    return Optional.ofNullable(contexts).stream().flatMap(Collection::stream)
            .filter(PlatformContextType.class::isInstance)
            .map(PlatformContextType.class::cast)
            .map(this::createTypeDefinition);
  }

  private PlatformTypeDefinition createTypeDefinition(PlatformContextType context) {
    return new PlatformTypeDefinition(
            context.name().getName(),
            null,
            Factory.methods(context),
            Factory.properties(context),
            Factory.constructors(context)
    );
  }
} 