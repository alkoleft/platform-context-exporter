package ru.alkoleft.context.platform.dto;

/**
 * Заглушка для демонстрации работы MCP сервера
 * Представляет базовое определение типа платформы 1С
 */
public record BaseTypeDefinition(String name, String description, String type) {
    @Override
    public String toString() {
        return "BaseTypeDefinition{name='" + name + "', type='" + type + "'}";
    }
}
