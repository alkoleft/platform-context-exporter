package ru.alkoleft.context.platform.commands;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import ru.alkoleft.context.platform.exporter.ContextExporter;
import ru.alkoleft.context.platform.exporter.Exporter;
import ru.alkoleft.context.platform.exporter.JsonExporter;
import ru.alkoleft.context.platform.exporter.MarkdownExporter;
import ru.alkoleft.context.platform.exporter.XmlExporter;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@CommandLine.Command(helpCommand = true, name = "platform")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformContext implements Runnable {
  @CommandLine.Parameters(description = "path")
  private Path path;
  @CommandLine.Parameters(description = "out")
  private Path output;
  @CommandLine.Option(names = "--format", description = "Output format: json, markdown, xml, context (default: ${DEFAULT-VALUE})", defaultValue = "json")
  private String format;

  @Override
  @SneakyThrows
  public void run() {
    Path syntaxContextFile;
    var fileName = "shcntx_ru.hbk";
    try (var walk = Files.walk(path)) {
      syntaxContextFile = walk.filter(p -> p.toFile().isFile())
              .filter(p -> p.toString().endsWith(fileName))
              .findAny()
              .orElse(null);
    }

    if (syntaxContextFile == null) {
      throw new FileNotFoundException(String.format("Не удалось найти файл %s в каталоге %s", fileName, path));
    }

    Files.createDirectories(output);

    var tmpDir = Files.createTempDirectory("test");

    var grabber = new PlatformContextGrabber(syntaxContextFile, tmpDir);
    grabber.parse();

    var provider = grabber.getProvider();

    Exporter exporter = switch (format.toLowerCase()) {
      case "json" -> new JsonExporter();
      case "markdown" -> new MarkdownExporter();
      case "xml" -> new XmlExporter();
      case "context" -> new ContextExporter();
      default -> throw new IllegalArgumentException("Unsupported format: " + format);
    };

    var extension = exporter.getExtension();
    var propertiesFile = output.resolve("global-properties" + extension);
    var methodsFile = output.resolve("global-methods" + extension);
    var typesFile = output.resolve("types" + extension);

    log.info("Writing properties to {}", propertiesFile);
    exporter.writeProperties(provider.getGlobalContext(), propertiesFile);

    log.info("Writing methods to {}", methodsFile);
    exporter.writeMethods(provider.getGlobalContext(), methodsFile);

    log.info("Writing types to {}", typesFile);
    exporter.writeTypes(provider.getContexts(), typesFile);
  }
}
