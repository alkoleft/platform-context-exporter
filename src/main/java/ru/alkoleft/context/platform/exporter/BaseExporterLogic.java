package ru.alkoleft.context.platform.exporter;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.Factory;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.ParameterDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.dto.Signature;
import ru.alkoleft.context.platform.dto.BaseTypeDefinition;

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
    
    return Optional.ofNullable(context.methods())
      .map(List::stream)
      .orElse(Stream.empty())
      .filter(method -> method.availabilities().contains(Availability.SERVER) 
          || method.availabilities().contains(Availability.THIN_CLIENT))
      .map(method -> {
        List<Signature> signatures = Optional.ofNullable(method.signatures())
          .map(sigs -> sigs.stream()
            .map(sig -> {
              String sigDescription = PRIMARY_SIGNATURE_NAME.equals(sig.name().getName())
                ? sig.description()
                : sig.name().getName() + ". " + sig.description();

              List<ParameterDefinition> paramsList = Optional.ofNullable(sig.parameters())
                .filter(params -> !params.isEmpty())
                .map(params -> params.stream()
                  .map(Factory::parameter)
                  .collect(Collectors.toUnmodifiableList()))
                .orElse(Collections.emptyList());
                
              return new Signature(sig.name().getAlias(), sigDescription, paramsList);
            })
            .collect(Collectors.toUnmodifiableList()))
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

  @Override
  public Stream<PlatformTypeDefinition> extractTypes(List<Context> contexts) {
    return Optional.ofNullable(contexts)
      .map(List::stream)
      .orElse(Stream.empty())
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

  /**
   * Returns list of global 1C platform methods (demo data).
   * 
   * @return unmodifiable list of method definitions
   */
  public static List<MethodDefinition> getGlobalMethods() {
    return List.of(
      new MethodDefinition("Сообщить", "Выводит сообщение пользователю", List.of(), "Void"),
      new MethodDefinition("Число", "Преобразует значение в число", List.of(), "Number"),
      new MethodDefinition("Строка", "Преобразует значение в строку", List.of(), "String"),
      new MethodDefinition("ТекущаяДата", "Возвращает текущую дату", List.of(), "Date"),
      new MethodDefinition("ПолучитьВремяИсполнения", "Возвращает время выполнения операции", List.of(), "Number")
    );
  }

  /**
   * Returns list of global 1C platform properties (demo data).
   * 
   * @return unmodifiable list of property definitions
   */
  public static List<PropertyDefinition> getGlobalProperties() {
    return List.of(
      new PropertyDefinition("КаталогПрограммы", "ProgramDirectory", "Каталог программы", true, "String"),
      new PropertyDefinition("КаталогВременныхФайлов", "TempFilesDir", "Каталог временных файлов", true, "String"),
      new PropertyDefinition("РазделительСтрок", "LineSeparator", "Разделитель строк", true, "String"),
      new PropertyDefinition("РазделительПути", "PathSeparator", "Разделитель пути", true, "String")
    );
  }

  /**
   * Returns list of 1C platform types (demo data).
   * 
   * @return unmodifiable list of type definitions
   */
  public static List<BaseTypeDefinition> getTypes() {
    return List.of(
      new BaseTypeDefinition("Строка", "Строковый тип данных", "String"),
      new BaseTypeDefinition("Число", "Числовой тип данных", "Number"),
      new BaseTypeDefinition("Булево", "Логический тип данных", "Boolean"),
      new BaseTypeDefinition("Дата", "Тип данных для работы с датой и временем", "Date"),
      new BaseTypeDefinition("Неопределено", "Неопределенное значение", "Undefined")
    );
  }
} 