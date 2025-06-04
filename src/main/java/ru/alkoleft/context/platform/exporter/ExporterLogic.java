package ru.alkoleft.context.platform.exporter;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;

import java.util.stream.Stream;
import java.util.List;

public interface ExporterLogic {
  Stream<PropertyDefinition> extractProperties(PlatformGlobalContext context);

  Stream<MethodDefinition> extractMethods(PlatformGlobalContext context);

  Stream<PlatformTypeDefinition> extractTypes(List<Context> contexts);
} 