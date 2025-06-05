package ru.alkoleft.context.platform.exporter;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.ParameterDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.dto.Signature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MarkdownExporter implements Exporter {

    private final ExporterLogic logic;

    public MarkdownExporter() {
        this.logic = new BaseExporterLogic();
    }

    @Override
    public void writeProperties(PlatformGlobalContext context, Path output) throws IOException {
        var markdown = new StringBuilder();
        markdown.append("# Свойства\n\n");
        try (var properties = logic.extractProperties(context)) {
            appendProperties(markdown, properties.collect(Collectors.toList()));
        }
        Files.writeString(output, markdown.toString());
    }

    @Override
    public void writeMethods(PlatformGlobalContext context, Path output) throws IOException {
        var markdown = new StringBuilder();
        markdown.append("# Методы\n\n");
        try (var methods = logic.extractMethods(context)) {
            appendMethods(markdown, methods.collect(Collectors.toList()), "##");
        }
        Files.writeString(output, markdown.toString());
    }

    @Override
    public void writeTypes(List<Context> contexts, Path output) throws IOException {
        var markdown = new StringBuilder();
        markdown.append("# Типы\n\n");

        try (var types = logic.extractTypes(contexts)) {
            types.sorted(Comparator.comparing(PlatformTypeDefinition::name))
                .forEach(context -> {
                    markdown.append("## ").append(context.name()).append("\n\n");

                    if (!context.properties().isEmpty()) {
                        markdown.append("### Свойства\n\n");
                        appendProperties(markdown, context.properties());
                    }

                    if (!context.methods().isEmpty()) {
                        markdown.append("### Методы\n\n");
                        appendMethods(markdown, context.methods(), "####");
                    }
                });
        }

        Files.writeString(output, markdown.toString());
    }

    private void appendProperties(StringBuilder markdown, Collection<PropertyDefinition> properties) {
        if (properties.isEmpty()) {
            return;
        }
        markdown.append("| Имя | Описание |\n");
        markdown.append("|---|---|\n");
        properties.stream()
                .sorted(Comparator.comparing(PropertyDefinition::name))
                .forEach(prop -> {
                    markdown.append(String.format("| `%s` | %s |\n",
                            escapeMarkdown(prop.name()),
                            escapeMarkdown(prop.description())));
                });
        markdown.append("\n");
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
        var paramsString = formatParametersForSignature(sig.params());
        var returnTypeString = (method.returnType() != null && !method.returnType().isEmpty()) ? ":" + method.returnType() : "";
        return String.format("%s(%s)%s", method.name(), paramsString, returnTypeString);
    }

    private void appendMethods(StringBuilder markdown, Collection<MethodDefinition> methods, String headerLevel) {
        if (methods.isEmpty()) {
            return;
        }
        methods.stream()
                .sorted(Comparator.comparing(MethodDefinition::name))
                .forEach(method -> appendMethodDetails(markdown, method, headerLevel));
    }

    private void appendMethodDetails(StringBuilder markdown, MethodDefinition method, String headerLevel) {
        appendMethodHeader(markdown, method, headerLevel);
        appendMethodSignaturesAndParameters(markdown, method);
    }

    private void appendMethodHeader(StringBuilder markdown, MethodDefinition method, String headerLevel) {
        markdown.append(headerLevel).append(" ").append(method.name()).append("\n\n");
        if (method.description() != null && !method.description().isEmpty()) {
            markdown.append(method.description()).append("\n\n");
        }
    }

    private void appendMethodSignaturesAndParameters(StringBuilder markdown, MethodDefinition method) {
        if (method.signature() != null && !method.signature().isEmpty()) {
            method.signature().forEach(sig -> {
                appendSignatureBlock(markdown, method, sig);
                appendParameterTable(markdown, sig.params());
            });
        } else {
            appendSignatureBlockForMethodWithoutParams(markdown, method);
        }
    }

    private void appendSignatureBlock(StringBuilder markdown, MethodDefinition method, Signature sig) {
        markdown.append("```bsl\n");
        markdown.append(formatCompleteSignature(method, sig)).append("\n");
        markdown.append("```\n\n");
    }

    private void appendSignatureBlockForMethodWithoutParams(StringBuilder markdown, MethodDefinition method) {
        markdown.append("```bsl\n");
        var returnTypeString = (method.returnType() != null && !method.returnType().isEmpty()) ? ":" + method.returnType() : "";
        markdown.append(String.format("%s()%s", method.name(), returnTypeString));
        markdown.append("\n```\n\n");
    }

    private void appendParameterTable(StringBuilder markdown, List<ParameterDefinition> params) {
        if (params == null || params.isEmpty()) {
            return;
        }

        markdown.append("**Параметры**\n\n");
        markdown.append("| Имя | Тип | Обязательность | Описание |\n");
        markdown.append("|---|---|---|---|\n");
        params.forEach(p ->
                markdown.append(String.format("| `%s` | `%s` | %s | %s |\n",
                        escapeMarkdown(p.name()),
                        escapeMarkdown(p.type()),
                        p.required() ? "Да" : "Нет",
                        escapeMarkdown(p.description())))
        );
        markdown.append("\n");
    }

    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\n", " ").replace("|", "\\|");
    }
} 