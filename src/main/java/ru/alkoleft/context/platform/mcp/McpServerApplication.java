package ru.alkoleft.context.platform.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot приложение для MCP сервера платформы 1С Предприятие
 */
@SpringBootApplication
@EnableCaching
@ComponentScan(basePackages = {
        "ru.alkoleft.context.platform.mcp",
        "ru.alkoleft.context.platform.exporter",
        "ru.alkoleft.context.platform.dto"
})
public class McpServerApplication {
    
    public static void main(String[] args) {
        // Настройка логирования для MCP режима (в файл, не в stdout)
        System.setProperty("logging.file.name", "mcp-server.log");
        System.setProperty("logging.level.org.springframework.ai.mcp", "DEBUG");
        
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(PlatformApiSearchService searchService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(searchService)
                .build();
    }
} 