package ru.alkoleft.context.platform.exporter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class JsonExporter implements Exporter {

  private final ExporterLogic logic;

  public JsonExporter(ExporterLogic logic) {
    this.logic = logic;
  }

  public JsonExporter() {
    this.logic = new BaseExporterLogic();
  }

  @Override
  public void writeProperties(PlatformGlobalContext context, Path output) throws IOException {
    var jfactory = new JsonFactory();
    var file = output.toFile();

    try (var generator = jfactory.createGenerator(file, JsonEncoding.UTF8)) {
      generator.writeStartArray();
      try (var properties = logic.extractProperties(context)) {
        List<PropertyDefinition> propertyList = properties.toList();
        for (PropertyDefinition property : propertyList) {
          generator.writeStartObject();
          generator.writeStringField("name", property.name());
          if (property.nameEn() != null) {
            generator.writeStringField("name_en", property.nameEn());
          }
          if (property.description() != null) {
            generator.writeStringField("description", property.description());
          }
          generator.writeBooleanField("readonly", property.readonly());
          if (property.type() != null) {
            generator.writeStringField("type", property.type());
          }
          generator.writeEndObject();
        }
      }
      generator.writeEndArray();
    }
  }

  @Override
  public void writeMethods(PlatformGlobalContext context, Path output) throws IOException {
    var jfactory = new JsonFactory();
    var file = output.toFile();

    try (var generator = jfactory.createGenerator(file, JsonEncoding.UTF8)) {
      generator.writeStartArray();
      try (var methods = logic.extractMethods(context)) {
        List<MethodDefinition> methodList = methods.toList();
        for (MethodDefinition method : methodList) {
          generator.writeStartObject();
          generator.writeStringField("name", method.name());
          if (method.description() != null) {
            generator.writeStringField("description", method.description());
          }
          generator.writeArrayFieldStart("signature");
          if (method.signature() != null) {
            for (var sig : method.signature()) {
              generator.writeStartObject();
              if (sig.description() != null) {
                generator.writeStringField("description", sig.description());
              }
              generator.writeArrayFieldStart("params");
              if (sig.params() != null) {
                for (var param : sig.params()) {
                  generator.writeStartObject();
                  generator.writeStringField("name", param.name());
                  if (param.description() != null) {
                    generator.writeStringField("description", param.description());
                  }
                  if (param.type() != null) {
                    generator.writeStringField("type", param.type());
                  }
                  generator.writeBooleanField("required", param.required());
                  generator.writeEndObject();
                }
              }
              generator.writeEndArray();
              generator.writeEndObject();
            }
          }
          generator.writeEndArray();

          if (method.returnType() != null) {
            generator.writeStringField("return", method.returnType());
          }
          generator.writeEndObject();
        }
      }
      generator.writeEndArray();
    }
  }

  @Override
  public void writeTypes(List<Context> contexts, Path output) throws IOException {
    var file = output.toFile();

    var mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    var objectWriter = mapper.writerFor(PlatformTypeDefinition.class);

    try (var generator = mapper.getFactory().createGenerator(file, JsonEncoding.UTF8)) {
      generator.writeStartArray();
      try (var types = logic.extractTypes(contexts)) {
        List<PlatformTypeDefinition> typeList = types.toList();
        for (PlatformTypeDefinition typeDef : typeList) {
          objectWriter.writeValue(generator, typeDef);
        }
      }
      generator.writeEndArray();
    } catch (IOException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getExtension() {
    return ".json";
  }
}
