package ru.alkoleft.context.platform.exporter;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import lombok.extern.slf4j.Slf4j;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.ParameterDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.dto.Signature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class MarkdownExporter implements Exporter {

  private static final String SEPARATOR = "----------------------------------------";
  private final ExporterLogic logic;

  public MarkdownExporter() {
    this.logic = new BaseExporterLogic();
  }

  private void appendLine(BufferedWriter writer, String line) throws IOException {
    writer.write(line);
    writer.newLine();
  }

  private void appendIfNotNullOrEmpty(BufferedWriter writer, String prefix, String value) throws IOException {
    if (value != null && !value.isEmpty()) {
      appendLine(writer, prefix + value);
    }
  }

  private void writeSnippetStart(BufferedWriter writer, String title, String description) throws IOException {
    appendLine(writer, "TITLE: " + title);
    appendIfNotNullOrEmpty(writer, "DESCRIPTION: ", description);
  }

  private void writeSeparator(BufferedWriter writer, boolean firstSnippet) throws IOException {
    if (!firstSnippet) {
      writer.newLine();
      appendLine(writer, SEPARATOR);
      writer.newLine();
    }
  }

  private <T> void writeEntries(
    Path file,
    String exportMessageFormat,
    Supplier<Stream<T>> streamSupplier,
    EntryWriter<T> entryWriterFunc
  ) throws IOException {
    log.info(exportMessageFormat, file.toAbsolutePath());

    try (BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
         Stream<T> entryStream = streamSupplier.get()) {

      Iterator<T> iterator = entryStream.iterator();
      boolean isFirst = true;

      while (iterator.hasNext()) {
        T entry = iterator.next();
        writeSeparator(writer, isFirst);
        entryWriterFunc.write(writer, entry);
        isFirst = false;
      }
    }
  }

  @Override
  public void writeProperties(PlatformGlobalContext context, Path output) throws IOException {
    Path file = output.resolve("global-properties.md");
    writeEntries(
      file,
      "Exporting global properties to: {}",
      () -> logic.extractProperties(context),
      (writer, property) -> {
        writeSnippetStart(writer, "Global Property: " + property.name(), property.description());
        appendLine(writer, "Name: " + property.name());
        appendLine(writer, "Readonly: " + property.readonly());
        appendIfNotNullOrEmpty(writer, "Type: ", property.type());
      }
    );
  }

  @Override
  public void writeMethods(PlatformGlobalContext context, Path output) throws IOException {
    Path file = output.resolve("global-methods.md");
    writeEntries(
      file,
      "Exporting global methods to: {}",
      () -> logic.extractMethods(context),
      (writer, method) -> {
        writeSnippetStart(writer, "Global Method: " + method.name(), method.description());
        appendLine(writer, "Name: " + method.name());
        writer.newLine();
        appendLine(writer, "Signatures:");

        if (method.signature() != null && !method.signature().isEmpty()) {
          for (var sig : method.signature()) {
            appendLine(writer, "  ---");
            String signatureLine = formatCompleteSignature(method, sig);
            appendLine(writer, "  " + signatureLine);
            appendIfNotNullOrEmpty(writer, "    Description: ", sig.description());
            appendSignatureParameterDescriptions(writer, sig.params(), "    ");
          }
        } else {
          String returnTypeString = (method.returnType() != null && !method.returnType().isEmpty()) ? ":" + method.returnType() : "";
          appendLine(writer, String.format("  %s()%s", method.name(), returnTypeString));
          appendLine(writer, "    (No specific parameter details provided for this general signature)");
        }
      }
    );
  }

  private void appendTypeProperties(BufferedWriter writer, List<PropertyDefinition> properties) throws IOException {
    writer.newLine();
    appendLine(writer, "Properties:");
    if (properties != null && !properties.isEmpty()) {
      for (var prop : properties) {
        var propType = (prop.type() != null && !prop.type().isEmpty()) ? prop.type() : "any";
        var readonlyStatus = prop.readonly() ? "readonly" : "readwrite";
        var propDesc = (prop.description() != null && !prop.description().isEmpty()) ? " - " + prop.description() : "";
        var namePart = prop.name();
        if (prop.nameEn() != null && !prop.nameEn().isEmpty()) {
          namePart += " (" + prop.nameEn() + ")";
        }
        appendLine(writer, String.format("  - %s: %s (%s)%s", namePart, propType, readonlyStatus, propDesc));
      }
    } else {
      appendLine(writer, "  (No properties)");
    }
  }

  private String formatParametersForSignature(List<ParameterDefinition> params) {
    if (params == null || params.isEmpty()) {
        return "";
    }
    return params.stream()
        .map(p -> {
            String paramType = (p.type() != null && !p.type().isEmpty()) ? p.type() : "any";
            return p.name() + ":" + paramType;
        })
        .collect(Collectors.joining(", "));
  }

  private String formatCompleteSignature(MethodDefinition method, Signature sig) {
    String paramsString = formatParametersForSignature(sig.params());
    String returnTypeString = (method.returnType() != null && !method.returnType().isEmpty()) ? ":" + method.returnType() : "";
    return String.format("%s(%s)%s", method.name(), paramsString, returnTypeString);
  }

  private void appendSignatureParameterDescriptions(BufferedWriter writer, List<ParameterDefinition> params, String indentPrefix) throws IOException {
    if (params != null && !params.isEmpty()) {
      appendLine(writer, indentPrefix + "Parameters:");
      for (var param : params) {
        String paramType = (param.type() != null && !param.type().isEmpty()) ? param.type() : "any";
        String requiredStatus = param.required() ? "required" : "optional";
        String paramDesc = (param.description() != null && !param.description().isEmpty()) ? " - " + param.description() : "";
        appendLine(writer, String.format("%s  - %s: %s (%s)%s", indentPrefix, param.name(), paramType, requiredStatus, paramDesc));
      }
    } else {
      appendLine(writer, indentPrefix + "Parameters: (None)");
    }
  }

  private void appendTypeMethods(BufferedWriter writer, List<MethodDefinition> methods) throws IOException {
    writer.newLine();
    appendLine(writer, "Methods:");
    if (methods != null && !methods.isEmpty()) {
      for (MethodDefinition method : methods) {
        appendLine(writer, "  ---");
        appendLine(writer, "  Method: " + method.name());
        appendIfNotNullOrEmpty(writer, "    Description: ", method.description());
        appendLine(writer, "    Signatures:");

        if (method.signature() != null && !method.signature().isEmpty()) {
          for (var sig : method.signature()) {
            appendLine(writer, "      ---");
            String signatureLine = formatCompleteSignature(method, sig);
            appendLine(writer, "      " + signatureLine);
            appendIfNotNullOrEmpty(writer, "        Description: ", sig.description());
            appendSignatureParameterDescriptions(writer, sig.params(), "        ");
          }
        } else {
          String returnTypeString = (method.returnType() != null && !method.returnType().isEmpty()) ? ":" + method.returnType() : "";
          appendLine(writer, String.format("      %s()%s", method.name(), returnTypeString));
          appendLine(writer, "        (No specific parameter details provided for this general signature)");
        }
      }
    } else {
      appendLine(writer, "  (No methods)");
    }
  }

  @Override
  public void writeTypes(List<Context> contexts, Path output) throws IOException {
    Path file = output.resolve("types.md");
    writeEntries(
      file,
      "Exporting types to: {}",
      () -> logic.extractTypes(contexts),
      (writer, typeDef) -> {
        writeSnippetStart(writer, "Type: " + typeDef.name(), typeDef.description());
        appendLine(writer, "Name: " + typeDef.name());
        appendTypeProperties(writer, typeDef.properties());
        appendTypeMethods(writer, typeDef.methods());
      }
    );
  }

  @FunctionalInterface
  private interface EntryWriter<T> {
    void write(BufferedWriter writer, T entry) throws IOException;
  }
} 