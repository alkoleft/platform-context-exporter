package ru.alkoleft.context.platform.dto;

import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import lombok.Getter;

@Getter
public class PlatformTypeDefinition extends BaseTypeDefinition {
  private final Signature[] constructors;

  public PlatformTypeDefinition(PlatformContextType context) {

    super(context.name().getName(), null, Factory.methods(context), Factory.properties(context));
    constructors = Factory.constructors(context);
  }
}
