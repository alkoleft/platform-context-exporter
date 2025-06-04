package ru.alkoleft.context.platform;

import picocli.CommandLine;
import ru.alkoleft.context.platform.commands.MainCommand;

public class Main {
  public static void main(String[] args) {
    new CommandLine(new MainCommand()).execute(args);
  }
}
