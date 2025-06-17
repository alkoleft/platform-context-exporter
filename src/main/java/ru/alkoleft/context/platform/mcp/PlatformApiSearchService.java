package ru.alkoleft.context.platform.mcp;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.BaseTypeDefinition;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.exporter.BaseExporterLogic;
import ru.alkoleft.context.platform.mcp.dto.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис поиска по API платформы 1С Предприятие через MCP протокол
 */
@Slf4j
@Service
public class PlatformApiSearchService {
    
    private final PlatformContextService contextService;
    private final MarkdownFormatterService formatter;
    private final BaseExporterLogic exporterLogic;
    
    // Индекс для быстрого поиска
    private List<SearchResult> searchIndex;
    private volatile boolean indexInitialized = false;
    
    public PlatformApiSearchService(PlatformContextService contextService, 
                                   MarkdownFormatterService formatter,
                                   BaseExporterLogic exporterLogic) {
        this.contextService = contextService;
        this.formatter = formatter;
        this.exporterLogic = exporterLogic;
    }
    
    /**
     * Поиск по API платформы 1С Предприятие
     */
    @Tool(name = "search", description = "Поиск по API платформы 1С Предприятие")
    @Cacheable("api-search")
    public String search(String query, String type, Integer limit) {
        // Устанавливаем значение по умолчанию для limit
        int effectiveLimit = (limit != null) ? limit : 10;
        if (query == null || query.trim().isEmpty()) {
            return "❌ **Ошибка:** Запрос не может быть пустым";
        }
        
        try {
            ensureIndexInitialized();
        } catch (Exception e) {
            log.error("Ошибка при инициализации индекса поиска", e);
            return "❌ **Ошибка:** " + e.getMessage();
        }
        
        // Нормализация запроса
        String normalizedQuery = query.trim().toLowerCase();
        
        // Фильтрация по типу
        List<SearchResult> filteredResults = filterByType(searchIndex, type);
        
        // Поиск с ранжированием
        List<SearchResult> rankedResults = performSearch(filteredResults, normalizedQuery)
                .stream()
                .limit(Math.min(effectiveLimit, 50)) // Максимум 50 результатов
                .collect(Collectors.toList());
        
        // Форматирование результатов
        return formatter.formatSearchResults(query, rankedResults);
    }
    
    /**
     * Получение детальной информации об API элементе
     */
    @Tool(name = "info", description = "Получение детальной информации об элементе API платформы 1С")
    @Cacheable("api-info")
    public String getInfo(String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            return "❌ **Ошибка:** Имя элемента не может быть пустым";
        }
        
        try {
            ensureIndexInitialized();
        } catch (Exception e) {
            log.error("Ошибка при инициализации индекса поиска", e);
            return "❌ **Ошибка:** " + e.getMessage();
        }
        
        // Точный поиск по имени
        Optional<SearchResult> result = searchIndex.stream()
                .filter(item -> item.getName().equalsIgnoreCase(name.trim()))
                .filter(item -> type == null || type.isEmpty() || item.getType().equalsIgnoreCase(type))
                .findFirst();
        
        if (result.isPresent()) {
            return formatter.formatDetailedInfo(result.get());
        } else {
            return String.format("❌ **Не найдено:** %s типа %s", name, type != null ? type : "любого");
        }
    }
    
    /**
     * Инициализация поискового индекса из реального контекста платформы
     */
    private void ensureIndexInitialized() {
        if (indexInitialized && searchIndex != null && !searchIndex.isEmpty()) {
            return;
        }
        
        synchronized (this) {
            if (indexInitialized && searchIndex != null && !searchIndex.isEmpty()) {
                return;
            }
            
            log.info("Инициализация поискового индекса из контекста платформы");
            initializeSearchIndex();
            indexInitialized = true;
            log.info("Поисковый индекс инициализирован. Элементов: {}", searchIndex.size());
        }
    }
    
    /**
     * Инициализация поискового индекса
     */
    private void initializeSearchIndex() {
        searchIndex = new ArrayList<>();
        
        try {
            ContextProvider provider = contextService.getContextProvider();
            
            // Добавляем глобальные методы и свойства из реального контекста
            var globalContext = provider.getGlobalContext();
            if (globalContext != null) {
                // Используем exporterLogic для извлечения методов и свойств глобального контекста
                exporterLogic.extractMethods(globalContext).forEach(methodDef -> {
                    searchIndex.add(new SearchResult(
                            methodDef.name(),
                            "method",
                            buildMethodSignature(methodDef),
                            methodDef.description() != null ? methodDef.description() : "",
                            methodDef
                    ));
                });
                
                exporterLogic.extractProperties(globalContext).forEach(propertyDef -> {
                    searchIndex.add(new SearchResult(
                            propertyDef.name(),
                            "property",
                            propertyDef.type(),
                            propertyDef.description() != null ? propertyDef.description() : "",
                            propertyDef
                    ));
                });
            }
            
            // Добавляем типы данных из реального контекста
            var contexts = provider.getContexts();
            if (contexts != null) {
                exporterLogic.extractTypes(List.copyOf(contexts)).forEach(typeDefinition -> {
                    searchIndex.add(new SearchResult(
                            typeDefinition.name(),
                            "type",
                            "Тип данных платформы",
                            typeDefinition.description() != null ? typeDefinition.description() : "",
                            typeDefinition
                    ));
                });
            }
            
        } catch (Exception e) {
            log.warn("Не удалось загрузить данные из контекста платформы, используем фикстуры", e);
            initializeWithFixtures();
        }
    }
    
    /**
     * Резервная инициализация с фикстурами (если не удалось загрузить реальные данные)
     */
    private void initializeWithFixtures() {
        searchIndex = new ArrayList<>();
        
        // Добавляем фикстуры как fallback
        for (MethodDefinition method : BaseExporterLogic.getGlobalMethods()) {
            searchIndex.add(new SearchResult(
                    method.name(),
                    "method",
                    buildMethodSignature(method),
                    method.description() != null ? method.description() : "",
                    method
            ));
        }
        
        for (PropertyDefinition property : BaseExporterLogic.getGlobalProperties()) {
            searchIndex.add(new SearchResult(
                    property.name(),
                    "property", 
                    property.type(),
                    property.description() != null ? property.description() : "",
                    property
            ));
        }
        
        for (BaseTypeDefinition typeDefinition : BaseExporterLogic.getTypes()) {
            searchIndex.add(new SearchResult(
                    typeDefinition.name(),
                    "type",
                    "Тип данных",
                    typeDefinition.description() != null ? typeDefinition.description() : "",
                    typeDefinition
            ));
        }
        
        log.info("Инициализация завершена с использованием фикстур. Элементов: {}", searchIndex.size());
    }
    
    /**
     * Фильтрация результатов по типу
     */
    private List<SearchResult> filterByType(List<SearchResult> results, String type) {
        if (type == null || type.trim().isEmpty()) {
            return results;
        }
        
        String normalizedType = type.trim().toLowerCase();
        return results.stream()
                .filter(result -> result.getType().toLowerCase().equals(normalizedType))
                .collect(Collectors.toList());
    }
    
    /**
     * Выполнение поиска с ранжированием
     */
    private List<SearchResult> performSearch(List<SearchResult> candidates, String query) {
        return candidates.stream()
                .map(result -> {
                    int score = calculateScore(result, query);
                    result.setScore(score);
                    return result;
                })
                .filter(result -> result.getScore() > 0)
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }
    
    /**
     * Вычисление релевантности результата
     */
    private int calculateScore(SearchResult result, String query) {
        String name = result.getName().toLowerCase();
        String signature = result.getSignature().toLowerCase();
        String description = result.getDescription().toLowerCase();
        
        // Точное совпадение имени - максимальный балл
        if (name.equals(query)) {
            return 100;
        }
        
        // Имя начинается с запроса
        if (name.startsWith(query)) {
            return 80;
        }
        
        // Имя содержит запрос
        if (name.contains(query)) {
            return 60;
        }
        
        // Сигнатура содержит запрос
        if (signature.contains(query)) {
            return 40;
        }
        
        // Описание содержит запрос
        if (description.contains(query)) {
            return 20;
        }
        
        // Fuzzy matching для имени
        int editDistance = levenshteinDistance(name, query);
        if (editDistance <= Math.max(1, query.length() / 3)) {
            return Math.max(0, 50 - editDistance * 10);
        }
        
        return 0;
    }
    
    /**
     * Простая реализация расстояния Левенштейна
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
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
} 