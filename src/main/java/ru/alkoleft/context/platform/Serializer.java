package ru.alkoleft.context.platform;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@UtilityClass
public class JsonExporter implements Exporter {
  @Override
  public void writeProperties(PlatformGlobalContext context, Path output) throws IOException {
    JsonFactory jfactory = new JsonFactory();
    var file = output.resolve("global-properties.json").toFile();
    System.out.println(file);

    try (var generator = jfactory.createGenerator(file, JsonEncoding.UTF8)) {
      generator.writeStartArray();

      for (var property : context.properties()) {
        generator.writeStartObject();
        generator.writeStringField("name", property.name().getName());
        generator.writeStringField("name_en", property.name().getAlias());
        generator.writeStringField("description", property.description());
        generator.writeBooleanField("readonly", AccessMode.READ.equals(property.accessMode()));
        if (!property.types().isEmpty()) {
          generator.writeStringField("type", property.types().get(0).name().getName());
        }
        generator.writeEndObject();
      }
      generator.writeEndArray();
    }
  }

  @Override
  public void writeMethods(PlatformGlobalContext context, Path output) throws IOException {
    JsonFactory jfactory = new JsonFactory();
    var file = output.resolve("global-methods.json").toFile();
    System.out.println(file);

    try (JsonGenerator generator = jfactory.createGenerator(file, JsonEncoding.UTF8)) {
      generator.writeStartArray();

      for (var method : context.methods()) {
        if (!(method.availabilities().contains(Availability.SERVER) || method.availabilities().contains(Availability.THIN_CLIENT))) {
          continue;
        }
        generator.writeStartObject();
        generator.writeStringField("name", method.name().getName());
        generator.writeStringField("name_en", method.name().getAlias());
        generator.writeStringField("description", method.description());
        generator.writeArrayFieldStart("signature");
        for (var sig : method.signatures()) {
          generator.writeStartObject();
          if (sig.name().getName().equals("Основной")) {
            generator.writeStringField("description", sig.description());
          } else {
            generator.writeStringField("description", sig.name().getName() + ". " + sig.description());
          }
          generator.writeArrayFieldStart("params");
          for (var param : sig.parameters()) {
            generator.writeStartObject();
            generator.writeStringField("name", param.name().getName());
            generator.writeStringField("description", param.description());
            if (!param.types().isEmpty()) {
              generator.writeStringField("type", param.types().get(0).name().getName());
            }
            if (!param.isRequired()) {
              generator.writeBooleanField("required", param.isRequired());
            }
            generator.writeEndObject();
          }
          generator.writeEndArray();
          generator.writeEndObject();

        }
        generator.writeEndArray();

        if (method.hasReturnValue()) {
          generator.writeStringField("return", method.returnValues().get(0).name().getName());
        }

        generator.writeEndObject();
      }
      generator.writeEndArray();
    }
  }

  @Override
  public void writeTypes(List<Context> contexts, Path output) throws IOException {
    var file = output.resolve("types.json").toFile();
    System.out.println(file);
    var mapper = new ObjectMapper()
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .writerFor(PlatformTypeDefinition.class);
    try (var generator = mapper.createGenerator(file, JsonEncoding.UTF8)) {
      generator.writeStartArray();
      contexts.stream()
        .filter(PlatformContextType.class::isInstance)
        .map(PlatformContextType.class::cast)
        .map(PlatformTypeDefinition::new)
        .forEach(m -> dumpModule(m, generator, mapper));
      generator.writeEndArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @SneakyThrows
  private void dumpModule(PlatformTypeDefinition type, JsonGenerator g, ObjectWriter mapper) {
    mapper.writeValue(g, type);
  }

}
