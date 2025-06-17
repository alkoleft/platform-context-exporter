package ru.alkoleft.context.platform.mcp;

import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.BaseTypeDefinition;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.mcp.dto.SearchResult;

import java.util.List;

/**
 * Сервис форматирования результатов поиска в Markdown для MCP
 */
@Service
public class MarkdownFormatterService {
    
    /**
     * Форматирование результатов поиска
     */
    public String formatSearchResults(String query, List<SearchResult> results) {
        if (results.isEmpty()) {
            return String.format("❌ **Ничего не найдено по запросу:** `%s`\n\n" +
                    "💡 **Попробуйте:**\n" +
                    "- Проверить правописание\n" +
                    "- Использовать более короткий запрос\n" +
                    "- Попробовать синонимы", query);
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Заголовок с количеством результатов
        sb.append(String.format("# 🔎 Результаты поиска: \"%s\" (%d найдено)\n\n", query, results.size()));
        
        // Адаптивное форматирование в зависимости от количества результатов
        if (results.size() == 1) {
            // Один результат - детальное описание
            sb.append(formatSingleResult(results.get(0)));
        } else if (results.size() <= 5) {
            // Несколько результатов - краткое описание каждого
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                sb.append(formatCompactResult(result, i == 0));
                if (i < results.size() - 1) {
                    sb.append("\n---\n\n");
                }
            }
        } else {
            // Много результатов - табличный формат для топ-5
            sb.append("## Топ результаты\n\n");
            sb.append("| Название | Тип | Сигнатура | Релевантность |\n");
            sb.append("|----------|-----|-----------|---------------|\n");
            
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                SearchResult result = results.get(i);
                sb.append(String.format("| **%s** | %s | `%s` | %d%% |\n",
                        result.getName(),
                        getTypeIcon(result.getType()),
                        truncateSignature(result.getSignature(), 40),
                        result.getScore()));
            }
            
            if (results.size() > 5) {
                sb.append(String.format("\n*... и еще %d результатов*\n", results.size() - 5));
            }
            
            // Детальное описание первого результата
            sb.append("\n---\n\n");
            sb.append("## ⭐ Наиболее релевантный результат\n\n");
            sb.append(formatSingleResult(results.get(0)));
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирование детальной информации об элементе
     */
    public String formatDetailedInfo(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("# %s %s\n\n", getTypeIcon(result.getType()), result.getName()));
        
        // Сигнатура
        sb.append("## Сигнатура\n");
        sb.append("```bsl\n");
        sb.append(result.getSignature()).append("\n");
        sb.append("```\n\n");
        
        // Детальная информация в зависимости от типа
        if (result.getOriginalObject() instanceof MethodDefinition) {
            formatMethodDetails(sb, (MethodDefinition) result.getOriginalObject());
        } else if (result.getOriginalObject() instanceof PropertyDefinition) {
            formatPropertyDetails(sb, (PropertyDefinition) result.getOriginalObject());
        } else if (result.getOriginalObject() instanceof BaseTypeDefinition) {
            formatTypeDetails(sb, (BaseTypeDefinition) result.getOriginalObject());
        }
        
        // Описание
        if (!result.getDescription().isEmpty()) {
            sb.append("## Описание\n");
            sb.append(result.getDescription()).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирование одного результата (детальное)
     */
    private String formatSingleResult(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("## %s %s\n", getTypeIcon(result.getType()), result.getName()));
        sb.append("```bsl\n");
        sb.append(result.getSignature()).append("\n");
        sb.append("```\n");
        sb.append(String.format("*%s* • **Релевантность: %d%%**\n\n", 
                getTypeDescription(result.getType()), result.getScore()));
        
        if (!result.getDescription().isEmpty()) {
            sb.append(result.getDescription()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирование компактного результата
     */
    private String formatCompactResult(SearchResult result, boolean isFirst) {
        StringBuilder sb = new StringBuilder();
        
        String prefix = isFirst ? "## ⭐ " : "## ";
        sb.append(String.format("%s%s\n", prefix, result.getName()));
        sb.append("```bsl\n");
        sb.append(result.getSignature()).append("\n");
        sb.append("```\n");
        
        String relevanceNote = isFirst ? "" : " • *Менее релевантно*";
        sb.append(String.format("*%s* • **%s**%s\n", 
                getTypeDescription(result.getType()),
                getTypeBadge(result.getType()),
                relevanceNote));
        
        if (!result.getDescription().isEmpty()) {
            sb.append("\n").append(truncateDescription(result.getDescription(), 100)).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирование деталей метода
     */
    private void formatMethodDetails(StringBuilder sb, MethodDefinition method) {
        // Параметры
        if (method.signature() != null && !method.signature().isEmpty()) {
            sb.append("## Параметры\n");
            method.signature().forEach(param -> {
                sb.append(String.format("- **%s** *(%s)* - %s\n", 
                        param.name(),
                        param.getType(),
                        param.description() != null ? param.description() : "Описание отсутствует"));
            });
            sb.append("\n");
        }
        
        // Возвращаемое значение
        if (method.getReturnTypeDefinition() != null) {
            sb.append("## Возвращаемое значение\n");
            sb.append(String.format("**%s** - %s\n\n", 
                    method.getReturnTypeDefinition().getType(),
                    method.getReturnTypeDefinition().getDescription() != null ? 
                            method.getReturnTypeDefinition().getDescription() : "Описание отсутствует"));
        }
    }
    
    /**
     * Форматирование деталей свойства
     */
    private void formatPropertyDetails(StringBuilder sb, PropertyDefinition property) {
        sb.append("## Информация о свойстве\n");
        sb.append(String.format("- **Тип:** %s\n", property.type()));
        sb.append(String.format("- **Только чтение:** %s\n", property.readonly() ? "Да" : "Нет"));
        sb.append("\n");
    }
    
    /**
     * Форматирование деталей типа
     */
    private void formatTypeDetails(StringBuilder sb, BaseTypeDefinition type) {
        sb.append("## Информация о типе\n");
        sb.append(String.format("- **Базовый тип:** %s\n", type.getClass().getSimpleName()));
        sb.append("\n");
    }
    
    /**
     * Получение иконки для типа
     */
    private String getTypeIcon(String type) {
        switch (type.toLowerCase()) {
            case "method": return "🔧";
            case "property": return "📋";
            case "type": return "📦";
            default: return "❓";
        }
    }
    
    /**
     * Получение описания типа
     */
    private String getTypeDescription(String type) {
        switch (type.toLowerCase()) {
            case "method": return "Глобальный метод";
            case "property": return "Глобальное свойство";
            case "type": return "Тип данных";
            default: return "Неизвестный тип";
        }
    }
    
    /**
     * Получение бейджа типа
     */
    private String getTypeBadge(String type) {
        switch (type.toLowerCase()) {
            case "method": return "Методы";
            case "property": return "Свойства";
            case "type": return "Типы";
            default: return "Разное";
        }
    }
    
    /**
     * Обрезка сигнатуры
     */
    private String truncateSignature(String signature, int maxLength) {
        if (signature.length() <= maxLength) {
            return signature;
        }
        return signature.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Обрезка описания
     */
    private String truncateDescription(String description, int maxLength) {
        if (description.length() <= maxLength) {
            return description;
        }
        return description.substring(0, maxLength - 3) + "...";
    }
} 