package ru.alkoleft.context.platform.commands;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import ru.alkoleft.context.platform.mcp.McpServerApplication;

import java.util.concurrent.Callable;

/**
 * CLI команда для запуска MCP сервера платформы 1С Предприятие
 */
@Slf4j
@CommandLine.Command(
        name = "mcp-server",
        description = "Запускает MCP сервер для API платформы 1С Предприятие",
        mixinStandardHelpOptions = true
)
public class McpServerCommand implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-p", "--platform-path"},
            description = "Путь к каталогу установки 1С Предприятия",
            required = true
    )
    private String platformPath;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Включить отладочное логирование"
    )
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        try {
            // Настройка системных свойств для Spring Boot
            System.setProperty("platform.context.path", platformPath);

            if (verbose) {
                System.setProperty("logging.level.ru.alkoleft.context.platform.mcp", "DEBUG");
                System.setProperty("logging.level.org.springframework.ai.mcp", "DEBUG");
            }

            // Настройка логирования для MCP режима
            configureLoggingForMcp();

            // Логирование запуска сервера
            log.debug("Запуск MCP сервера для API платформы 1С Предприятие");
            log.debug("Путь к платформе: {}", platformPath);
            log.debug("Логи записываются в: mcp-server.log");
            log.debug("Готов к приему MCP команд через stdin/stdout");

            // Запуск Spring Boot приложения для MCP сервера
            McpServerApplication.main(new String[]{});

            return 0;

        } catch (Exception e) {
            log.error("❌ Ошибка запуска MCP сервера: {}", e.getMessage(), e);
            if (verbose) {
                log.debug("Подробная информация об ошибке:", e);
            }
            return 1;
        }
    }

    /**
     * Настройка логирования для MCP режима
     */
    private void configureLoggingForMcp() {
        // Перенаправляем все логи в файл, чтобы они не мешали JSON-RPC через stdout
        System.setProperty("logging.config", "classpath:logback-mcp.xml");

        // Отключаем консольное логирование Spring Boot
        System.setProperty("spring.main.banner-mode", "off");
        System.setProperty("logging.pattern.console", "");

        // Настройка логирования в файл
        System.setProperty("logging.file.name", "/tmp/mcp-server.log");
        System.setProperty("logging.file.max-size", "10MB");
        System.setProperty("logging.file.max-history", "5");
    }
} 