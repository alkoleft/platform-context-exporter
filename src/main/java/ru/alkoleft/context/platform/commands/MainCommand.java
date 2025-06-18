package ru.alkoleft.context.platform.commands;

import picocli.CommandLine;

@CommandLine.Command(subcommands = {
        PlatformContext.class,
        McpServerCommand.class,
})
public class MainCommand {
}