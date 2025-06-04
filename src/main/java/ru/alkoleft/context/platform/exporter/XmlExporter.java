package ru.alkoleft.context.platform.exporter;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class XmlExporter implements Exporter {

  private final ExporterLogic logic;
  private final XmlMapper xmlMapper;

  public XmlExporter(ExporterLogic logic) {
    this.logic = logic;
    this.xmlMapper = XmlMapper.builder()
      .defaultUseWrapper(false) // Используем false, чтобы избежать лишних оберток по умолчанию
      .build();
    // Для более красивого вывода XML
    this.xmlMapper.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
    this.xmlMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

  }

  public XmlExporter() {
    this(new BaseExporterLogic());
  }

  @Override
  public void writeProperties(PlatformGlobalContext context, Path output) throws IOException {
    var file = output.resolve("global-properties.xml").toFile();
    System.out.println("Writing global properties to: " + file.getAbsolutePath());

    try (Stream<PropertyDefinition> properties = logic.extractProperties(context)) {
      // Оборачиваем Stream в List для сериализации корневого элемента
      List<PropertyDefinition> propertyList = properties.toList();
      xmlMapper.writer().withRootName("properties").writeValue(file, propertyList);
    }
  }

  @Override
  public void writeMethods(PlatformGlobalContext context, Path output) throws IOException {
    var file = output.resolve("global-methods.xml").toFile();
    System.out.println("Writing global methods to: " + file.getAbsolutePath());

    try (Stream<MethodDefinition> methods = logic.extractMethods(context)) {
      // Оборачиваем Stream в List для сериализации корневого элемента
      List<MethodDefinition> methodList = methods.toList();
      xmlMapper.writer().withRootName("methods").writeValue(file, methodList);
    }
  }

  @Override
  public void writeTypes(List<Context> contexts, Path output) throws IOException {
    var file = output.resolve("types.xml").toFile();
    System.out.println("Writing types to: " + file.getAbsolutePath());

    try (Stream<PlatformTypeDefinition> types = logic.extractTypes(contexts)) {
      // Оборачиваем Stream в List для сериализации корневого элемента
      List<PlatformTypeDefinition> typeList = types.toList();
      xmlMapper.writer().withRootName("types").writeValue(file, typeList);
    }
  }
} 