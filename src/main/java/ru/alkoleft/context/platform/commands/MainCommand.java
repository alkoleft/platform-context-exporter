package ru.alkoleft.context.platform.commands;

import picocli.CommandLine;

@CommandLine.Command(subcommands = {
  PlatformContext.class,
})
public class MainCommand {
}