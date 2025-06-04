package ru.alkoleft.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface Exporter {
  void writeProperties(PlatformGlobalContext context, Path output) throws IOException;

  void writeMethods(PlatformGlobalContext context, Path output) throws IOException;

  void writeTypes(List<Context> contexts, Path output) throws IOException;
} 