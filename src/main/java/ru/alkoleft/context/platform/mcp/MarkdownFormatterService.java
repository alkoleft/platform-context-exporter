package ru.alkoleft.context.platform.mcp;

import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.ISignature;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Улучшенный сервис форматирования результатов поиска в Markdown для MCP
 * Работает напрямую с DTO объектами без промежуточных слоев
 */
@Service
public class MarkdownFormatterService {

    /**
     * Форматирование результатов поиска из DTO объектов
     */
    public String formatSearchResults(String query, List<Object> results) {
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
            sb.append(formatSingleObject(results.get(0)));
        } else if (results.size() <= 5) {
            // Несколько результатов - краткое описание каждого
            for (int i = 0; i < results.size(); i++) {
                Object result = results.get(i);
                sb.append(formatCompactObject(result, i == 0));
                if (i < results.size() - 1) {
                    sb.append("\n---\n\n");
                }
            }
        } else {
            // Много результатов - табличный формат для топ-5
            sb.append("## Топ результаты\n\n");
            sb.append("| Название | Тип | Сигнатура |\n");
            sb.append("|----------|-----|-----------|");

            for (int i = 0; i < Math.min(5, results.size()); i++) {
                Object result = results.get(i);
                sb.append(String.format("| **%s** | %s | `%s` |\n",
                        getObjectName(result),
                        getObjectTypeIcon(result),
                        truncateSignature(getObjectSignature(result), 40)));
            }

            if (results.size() > 5) {
                sb.append(String.format("\n*... и еще %d результатов*\n", results.size() - 5));
            }

            // Детальное описание первого результата
            sb.append("\n---\n\n");
            sb.append("## ⭐ Наиболее релевантный результат\n\n");
            sb.append(formatSingleObject(results.get(0)));
        }

        return sb.toString();
    }

    /**
     * Форматирование детальной информации об элементе DTO
     */
    public String formatDetailedInfo(Object obj) {
        if (obj instanceof MethodDefinition) {
            return formatMethodDefinition((MethodDefinition) obj);
        } else if (obj instanceof PropertyDefinition) {
            return formatPropertyDefinition((PropertyDefinition) obj);
        } else if (obj instanceof PlatformTypeDefinition) {
            return formatPlatformTypeDefinition((PlatformTypeDefinition) obj);
        }
        
        return "❌ **Неподдерживаемый тип объекта**";
    }

    /**
     * Форматирование конструкторов типа
     */
    public String formatConstructors(List<ISignature> constructors, String typeName) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("# 🔨 Конструкторы типа %s (%d найдено)\n\n", typeName, constructors.size()));
        
        for (int i = 0; i < constructors.size(); i++) {
            ISignature constructor = constructors.get(i);
            sb.append(String.format("## Конструктор %d\n", i + 1));
            sb.append("```bsl\n");
            sb.append(String.format("Новый %s(%s)\n", typeName, 
                    constructor.params().stream()
                            .map(param -> param.name() + ": " + param.type())
                            .collect(Collectors.joining(", "))));
            sb.append("```\n\n");
            
            if (constructor.description() != null && !constructor.description().isEmpty()) {
                sb.append("**Описание:** ").append(constructor.description()).append("\n\n");
            }
            
            if (!constructor.params().isEmpty()) {
                sb.append("**Параметры:**\n");
                constructor.params().forEach(param -> {
                    sb.append(String.format("- **%s** *(%s)* - %s\n",
                            param.name(),
                            param.type(),
                            param.description() != null ? param.description() : "Описание отсутствует"));
                });
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Форматирование всех членов типа
     */
    public String formatTypeMembers(PlatformTypeDefinition type) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("# 📦 Члены типа %s\n\n", type.name()));
        
        // Методы
        if (!type.methods().isEmpty()) {
            sb.append(String.format("## 🔧 Методы (%d)\n\n", type.methods().size()));
            for (MethodDefinition method : type.methods()) {
                sb.append(String.format("- **%s** - %s\n", 
                        method.name(),
                        method.description() != null ? method.description() : "Описание отсутствует"));
            }
            sb.append("\n");
        }
        
        // Свойства
        if (!type.properties().isEmpty()) {
            sb.append(String.format("## 📋 Свойства (%d)\n\n", type.properties().size()));
            for (PropertyDefinition property : type.properties()) {
                sb.append(String.format("- **%s** *(%s)* - %s\n", 
                        property.name(),
                        property.type(),
                        property.description() != null ? property.description() : "Описание отсутствует"));
            }
            sb.append("\n");
        }
        
        // Конструкторы
        if (!type.constructors().isEmpty()) {
            sb.append(String.format("## 🔨 Конструкторы (%d)\n\n", type.constructors().size()));
            sb.append("*Для получения детальной информации о конструкторах используйте getConstructors*\n\n");
        }
        
        return sb.toString();
    }

    /**
     * Форматирование определения метода
     */
    private String formatMethodDefinition(MethodDefinition method) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("# 🔧 %s\n\n", method.name()));

        // Сигнатура
        sb.append("## Сигнатура\n");
        sb.append("```bsl\n");
        sb.append(buildMethodSignature(method)).append("\n");
        sb.append("```\n\n");

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

        // Описание
        if (method.description() != null && !method.description().isEmpty()) {
            sb.append("## Описание\n");
            sb.append(method.description()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Форматирование определения свойства
     */
    private String formatPropertyDefinition(PropertyDefinition property) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("# 📋 %s\n\n", property.name()));

        // Тип
        sb.append("## Тип\n");
        sb.append("```bsl\n");
        sb.append(String.format("%s: %s", property.name(), property.type()));
        sb.append("```\n\n");

        // Информация о свойстве
        sb.append("## Информация о свойстве\n");
        sb.append(String.format("- **Тип:** %s\n", property.type()));
        sb.append(String.format("- **Только чтение:** %s\n", property.readonly() ? "Да" : "Нет"));
        sb.append("\n");

        // Описание
        if (property.description() != null && !property.description().isEmpty()) {
            sb.append("## Описание\n");
            sb.append(property.description()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Форматирование определения типа платформы
     */
    private String formatPlatformTypeDefinition(PlatformTypeDefinition type) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("# 📦 %s\n\n", type.name()));

        // Краткая информация
        sb.append("## Краткая информация\n");
        sb.append(String.format("- **Методы:** %d\n", type.methods().size()));
        sb.append(String.format("- **Свойства:** %d\n", type.properties().size()));
        sb.append(String.format("- **Конструкторы:** %d\n", type.constructors().size()));
        sb.append("\n");

        // Описание
        if (type.description() != null && !type.description().isEmpty()) {
            sb.append("## Описание\n");
            sb.append(type.description()).append("\n\n");
        }

        // Краткий список методов
        if (!type.methods().isEmpty()) {
            sb.append("## Доступные методы\n");
            type.methods().stream()
                    .limit(10)
                    .forEach(method -> sb.append(String.format("- %s\n", method.name())));
            if (type.methods().size() > 10) {
                sb.append(String.format("*... и еще %d методов*\n", type.methods().size() - 10));
            }
            sb.append("\n");
        }

        // Краткий список свойств
        if (!type.properties().isEmpty()) {
            sb.append("## Доступные свойства\n");
            type.properties().stream()
                    .limit(10)
                    .forEach(property -> sb.append(String.format("- %s (%s)\n", property.name(), property.type())));
            if (type.properties().size() > 10) {
                sb.append(String.format("*... и еще %d свойств*\n", type.properties().size() - 10));
            }
            sb.append("\n");
        }

        sb.append("💡 **Подсказка:** Используйте getMembers для получения полного списка\n");

        return sb.toString();
    }

    /**
     * Форматирование одного объекта (детальное)
     */
    private String formatSingleObject(Object obj) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("## %s %s\n", getObjectTypeIcon(obj), getObjectName(obj)));
        sb.append("```bsl\n");
        sb.append(getObjectSignature(obj)).append("\n");
        sb.append("```\n");
        sb.append(String.format("*%s*\n\n", getObjectTypeDescription(obj)));

        String description = getObjectDescription(obj);
        if (!description.isEmpty()) {
            sb.append(description).append("\n");
        }

        return sb.toString();
    }

    /**
     * Форматирование компактного объекта
     */
    private String formatCompactObject(Object obj, boolean isFirst) {
        StringBuilder sb = new StringBuilder();

        String prefix = isFirst ? "## ⭐ " : "## ";
        sb.append(String.format("%s%s\n", prefix, getObjectName(obj)));
        sb.append("```bsl\n");
        sb.append(getObjectSignature(obj)).append("\n");
        sb.append("```\n");

        String relevanceNote = isFirst ? "" : " • *Менее релевантно*";
        sb.append(String.format("*%s*%s\n", getObjectTypeDescription(obj), relevanceNote));

        String description = getObjectDescription(obj);
        if (!description.isEmpty()) {
            sb.append("\n").append(truncateDescription(description, 100)).append("\n");
        }

        return sb.toString();
    }

    /**
     * Получение имени объекта
     */
    private String getObjectName(Object obj) {
        if (obj instanceof MethodDefinition) {
            return ((MethodDefinition) obj).name();
        } else if (obj instanceof PropertyDefinition) {
            return ((PropertyDefinition) obj).name();
        } else if (obj instanceof PlatformTypeDefinition) {
            return ((PlatformTypeDefinition) obj).name();
        }
        return "Неизвестно";
    }

    /**
     * Получение сигнатуры объекта
     */
    private String getObjectSignature(Object obj) {
        if (obj instanceof MethodDefinition) {
            return buildMethodSignature((MethodDefinition) obj);
        } else if (obj instanceof PropertyDefinition) {
            PropertyDefinition prop = (PropertyDefinition) obj;
            return String.format("%s: %s", prop.name(), prop.type());
        } else if (obj instanceof PlatformTypeDefinition) {
            return "Тип данных платформы";
        }
        return "";
    }

    /**
     * Получение описания объекта
     */
    private String getObjectDescription(Object obj) {
        if (obj instanceof MethodDefinition) {
            String desc = ((MethodDefinition) obj).description();
            return desc != null ? desc : "";
        } else if (obj instanceof PropertyDefinition) {
            String desc = ((PropertyDefinition) obj).description();
            return desc != null ? desc : "";
        } else if (obj instanceof PlatformTypeDefinition) {
            String desc = ((PlatformTypeDefinition) obj).description();
            return desc != null ? desc : "";
        }
        return "";
    }

    /**
     * Получение иконки типа объекта
     */
    private String getObjectTypeIcon(Object obj) {
        if (obj instanceof MethodDefinition) {
            return "🔧";
        } else if (obj instanceof PropertyDefinition) {
            return "📋";
        } else if (obj instanceof PlatformTypeDefinition) {
            return "📦";
        }
        return "❓";
    }

    /**
     * Получение описания типа объекта
     */
    private String getObjectTypeDescription(Object obj) {
        if (obj instanceof MethodDefinition) {
            return "Метод";
        } else if (obj instanceof PropertyDefinition) {
            return "Свойство";
        } else if (obj instanceof PlatformTypeDefinition) {
            return "Тип данных";
        }
        return "Неизвестный тип";
    }

    /**
     * Построение сигнатуры метода
     */
    private String buildMethodSignature(MethodDefinition method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.name()).append("(");

        if (method.signature() != null) {
            sb.append(method.signature().stream()
                    .map(param -> param.name() + ": " + param.getType())
                    .collect(Collectors.joining(", ")));
        }

        sb.append(")");

        if (method.getReturnTypeDefinition() != null) {
            sb.append(": ").append(method.getReturnTypeDefinition().getType());
        }

        return sb.toString();
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