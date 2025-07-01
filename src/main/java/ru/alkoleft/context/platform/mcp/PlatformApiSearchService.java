package ru.alkoleft.context.platform.mcp;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.exporter.BaseExporterLogic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Улучшенный сервис поиска по API платформы 1С Предприятие через MCP протокол
 * Использует три отдельных индекса для оптимальной производительности
 */
@Slf4j
@Service
public class PlatformApiSearchService {

  // Алиасы типов для удобства LLM - расширено русскоязычными алиасами
  private static final Map<String, String> TYPE_ALIASES;
  static {
    Map<String, String> aliases = new HashMap<>();
    
    // Существующие английские алиасы
    aliases.put("object", "type");
    aliases.put("class", "type");
    aliases.put("datatype", "type");
    
    // Русскоязычные алиасы для type
    aliases.put("объект", "type");
    aliases.put("класс", "type");
    aliases.put("тип", "type");
    aliases.put("структура", "type");
    aliases.put("данные", "type");
    
    // Русскоязычные алиасы для method
    aliases.put("метод", "method");
    aliases.put("функция", "method");
    aliases.put("процедура", "method");
    
    // Русскоязычные алиасы для property
    aliases.put("свойство", "property");
    aliases.put("реквизит", "property");
    aliases.put("поле", "property");
    aliases.put("атрибут", "property");
    
    TYPE_ALIASES = Collections.unmodifiableMap(aliases);
  }
  private final PlatformContextService contextService;
  private final MarkdownFormatterService formatter;
  private final BaseExporterLogic exporterLogic;
  // Три отдельных индекса для оптимальной производительности
  private Map<String, MethodDefinition> globalMethodsIndex;
  private Map<String, PropertyDefinition> globalPropertiesIndex;
  private Map<String, PlatformTypeDefinition> typesIndex;
  private volatile boolean indexInitialized = false;

  public PlatformApiSearchService(PlatformContextService contextService,
                                  MarkdownFormatterService formatter,
                                  BaseExporterLogic exporterLogic) {
    this.contextService = contextService;
    this.formatter = formatter;
    this.exporterLogic = exporterLogic;
  }

  @PostConstruct
  void init(){
    ensureIndexInitialized();
  }
  /**
   * Поиск по API платформы 1С Предприятие
   *
   * @param query Поисковый запрос. Используйте конкретные термины из 1С:
   *              - Для поиска методов: "НайтиПоСсылке", "ВыполнитьОбработку", "ПолучитьДанные"
   *              - Для поиска типов: "Справочник", "Документ", "Обработка", "Отчет"
   *              - Для поиска свойств: "Ссылка", "Код", "Наименование", "Дата"
   *              - Для общего поиска: "параметры", "методы", "свойства", "конструкторы"
   *              Примеры хороших запросов: "методы работы со справочниками", "получение данных из базы", "работа с документами"
   * @param type  Тип искомого элемента API (опционально):
   *              - "method" - только методы и функции
   *              - "property" - только свойства и реквизиты
   *              - "type" - только типы данных (справочники, документы, обработки и т.д.)
   *              - null или пустая строка - поиск по всем типам
   * @param limit Максимальное количество результатов (по умолчанию 10, максимум 50)
   */
  @Tool(name = "search",
          description = "Поиск по API платформы 1С Предприятие. Используйте конкретные термины 1С для получения точных результатов.")
  @Cacheable("api-search")
  public String search(@ToolParam(description = "Поисковый запрос. Используйте конкретные термины из 1С: методы ('НайтиПоСсылке', 'ВыполнитьОбработку'), типы ('Справочник', 'Документ'), свойства ('Ссылка', 'Код', 'Наименование')") String query, 
                       @ToolParam(description = "Тип искомого элемента API: 'method' - методы, 'property' - свойства, 'type' - типы данных, null - все типы") String type, 
                       @ToolParam(description = "Максимальное количество результатов (по умолчанию 10, максимум 50)") Integer limit) {
    // Устанавливаем значение по умолчанию для limit
    int effectiveLimit = (limit != null) ? limit : 10;
    if (query == null || query.trim().isEmpty()) {
      return "❌ **Ошибка:** Запрос не может быть пустым";
    }

    try {
      ensureIndexInitialized();
    } catch (Exception e) {
      log.error("Ошибка при инициализации индексов поиска", e);
      return "❌ **Ошибка:** " + e.getMessage();
    }

    // Нормализация запроса
    String normalizedQuery = query.trim().toLowerCase();

    // Поиск в соответствующих индексах
    List<Object> searchResults = performIntelligentSearch(normalizedQuery, normalizeType(type));

    // Лимитирование результатов
    List<Object> limitedResults = searchResults.stream()
            .limit(Math.min(effectiveLimit, 50))
            .collect(Collectors.toList());

    // Форматирование результатов напрямую из DTO
    return formatter.formatSearchResults(query, limitedResults);
  }

  /**
   * Получение детальной информации об API элементе
   *
   * @param name Точное имя элемента API в 1С. Примеры:
   *             - Для методов: "НайтиПоСсылке", "ВыполнитьОбработку", "ПолучитьДанные"
   *             - Для типов: "СправочникСсылка", "ДокументОбъект", "ОбработкаОбъект"
   *             - Для свойств: "Ссылка", "Код", "Наименование", "Дата"
   *             Регистр важен! Используйте точные имена из 1С.
   * @param type Уточнение типа элемента (опционально):
   *             - "method" - если ищете метод или функцию
   *             - "property" - если ищете свойство или реквизит
   *             - "type" - если ищете тип данных
   *             - null - автоматическое определение типа
   */
  @Tool(name = "info",
          description = "Получение детальной информации об элементе API платформы 1С. Требует точное имя элемента.")
  @Cacheable("api-info")
  public String getInfo(@ToolParam(description = "Точное имя элемента API в 1С. Примеры: 'НайтиПоСсылке', 'СправочникСсылка', 'Ссылка', 'Код'") String name, 
                        @ToolParam(description = "Уточнение типа элемента: 'method' - метод/функция, 'property' - свойство/реквизит, 'type' - тип данных, null - автоматическое определение") String type) {
    if (name == null || name.trim().isEmpty()) {
      return "❌ **Ошибка:** Имя элемента не может быть пустым";
    }

    try {
      ensureIndexInitialized();
    } catch (Exception e) {
      log.error("Ошибка при инициализации индексов поиска", e);
      return "❌ **Ошибка:** " + e.getMessage();
    }

    String normalizedName = name.trim().toLowerCase();
    String normalizedType = normalizeType(type);

    // Поиск точного совпадения в соответствующих индексах
    Optional<Object> result = findExactMatch(normalizedName, normalizedType);

    if (result.isPresent()) {
      return formatter.formatDetailedInfo(result.get());
    } else {
      return String.format("❌ **Не найдено:** %s типа %s", name, type != null ? type : "любого");
    }
  }

  /**
   * Получение информации о члене типа (методе или свойстве)
   *
   * @param typeName   Имя типа 1С. Примеры:
   *                   - "СправочникСсылка" - для работы со ссылками справочников
   *                   - "ДокументОбъект" - для работы с объектами документов
   *                   - "Строка" - для строковых операций
   *                   - "Число" - для числовых операций
   *                   - "Дата" - для работы с датами
   * @param memberName Имя метода или свойства типа. Примеры:
   *                   - Для справочников: "НайтиПоКоду", "НайтиПоНаименованию", "Код", "Наименование"
   *                   - Для документов: "Записать", "Провести", "ОтменитьПроведение", "Дата", "Номер"
   *                   - Для строк: "Длина", "ВРег", "НРег", "СокрЛП"
   */
  @Tool(name = "getMember",
          description = "Получение информации о методе или свойстве конкретного типа 1С. Используйте точные имена типов и членов.")
  @Cacheable("api-member")
  public String getMember(@ToolParam(description = "Имя типа 1С. Примеры: 'СправочникСсылка', 'ДокументОбъект', 'Строка', 'Число', 'Дата'") String typeName, 
                          @ToolParam(description = "Имя метода или свойства типа. Примеры: 'НайтиПоКоду', 'Записать', 'Код', 'Наименование', 'Длина'") String memberName) {
    if (typeName == null || typeName.trim().isEmpty() ||
            memberName == null || memberName.trim().isEmpty()) {
      return "❌ **Ошибка:** Имя типа и имя члена не могут быть пустыми";
    }

    try {
      ensureIndexInitialized();
    } catch (Exception e) {
      log.error("Ошибка при инициализации индексов поиска", e);
      return "❌ **Ошибка:** " + e.getMessage();
    }

    String normalizedTypeName = typeName.trim().toLowerCase();
    String normalizedMemberName = memberName.trim().toLowerCase();

    PlatformTypeDefinition type = typesIndex.get(normalizedTypeName);
    if (type == null) {
      return String.format("❌ **Тип не найден:** %s", typeName);
    }

    // Поиск среди методов типа
    Optional<MethodDefinition> method = type.methods().stream()
            .filter(m -> m.name().toLowerCase().equals(normalizedMemberName))
            .findFirst();

    if (method.isPresent()) {
      return formatter.formatDetailedInfo(method.get());
    }

    // Поиск среди свойств типа
    Optional<PropertyDefinition> property = type.properties().stream()
            .filter(p -> p.name().toLowerCase().equals(normalizedMemberName))
            .findFirst();

    if (property.isPresent()) {
      return formatter.formatDetailedInfo(property.get());
    }

    return String.format("❌ **Член не найден:** %s в типе %s", memberName, typeName);
  }

  /**
   * Получение конструкторов типа
   *
   * @param typeName Имя типа данных 1С для получения его конструкторов. Примеры:
   *                 - "Массив" - создание массивов
   *                 - "Структура" - создание структур данных
   *                 - "Соответствие" - создание соответствий (словарей)
   *                 - "ТаблицаЗначений" - создание таблиц значений
   *                 - "СписокЗначений" - создание списков значений
   *                 - "УниверсальнаяДата" - создание дат
   */
  @Tool(name = "getConstructors",
          description = "Получение списка конструкторов для указанного типа 1С. Показывает способы создания объектов данного типа.")
  @Cacheable("api-constructors")
  public String getConstructors(@ToolParam(description = "Имя типа 1С для получения конструкторов. Примеры: 'СправочникМенеджер', 'ДокументМенеджер', 'Запрос', 'ТаблицаЗначений'") String typeName) {
    if (typeName == null || typeName.trim().isEmpty()) {
      return "❌ **Ошибка:** Имя типа не может быть пустым";
    }

    try {
      ensureIndexInitialized();
    } catch (Exception e) {
      log.error("Ошибка при инициализации индексов поиска", e);
      return "❌ **Ошибка:** " + e.getMessage();
    }

    String normalizedTypeName = typeName.trim().toLowerCase();
    PlatformTypeDefinition type = typesIndex.get(normalizedTypeName);

    if (type == null) {
      return String.format("❌ **Тип не найден:** %s", typeName);
    }

    if (type.constructors().isEmpty()) {
      return String.format("❌ **Конструкторы не найдены** для типа %s", typeName);
    }

    return formatter.formatConstructors(type.constructors(), typeName);
  }

  /**
   * Получение всех членов типа (методы + свойства)
   *
   * @param typeName Имя типа 1С для получения полного списка его методов и свойств. Примеры:
   *                 - "СправочникСсылка" - все методы и свойства ссылок справочников
   *                 - "ДокументОбъект" - все методы и свойства объектов документов
   *                 - "Строка" - все строковые функции и свойства
   *                 - "ТаблицаЗначений" - методы работы с таблицами значений
   *                 - "Запрос" - методы построения и выполнения запросов к базе данных
   */
  @Tool(name = "getMembers",
          description = "Получение полного списка всех методов и свойств для указанного типа 1С. Полный справочник API типа.")
  @Cacheable("api-members")
  public String getMembers(@ToolParam(description = "Имя типа 1С для получения полного списка методов и свойств. Примеры: 'СправочникСсылка', 'ДокументОбъект', 'Строка', 'ТаблицаЗначений', 'Запрос'") String typeName) {
    if (typeName == null || typeName.trim().isEmpty()) {
      return "❌ **Ошибка:** Имя типа не может быть пустым";
    }

    try {
      ensureIndexInitialized();
    } catch (Exception e) {
      log.error("Ошибка при инициализации индексов поиска", e);
      return "❌ **Ошибка:** " + e.getMessage();
    }

    String normalizedTypeName = typeName.trim().toLowerCase();
    PlatformTypeDefinition type = typesIndex.get(normalizedTypeName);

    if (type == null) {
      return String.format("❌ **Тип не найден:** %s", typeName);
    }

    return formatter.formatTypeMembers(type);
  }

  /**
   * Инициализация поисковых индексов из реального контекста платформы
   */
  private void ensureIndexInitialized() {
    if (indexInitialized && globalMethodsIndex != null && !globalMethodsIndex.isEmpty()) {
      return;
    }

    synchronized (this) {
      if (indexInitialized && globalMethodsIndex != null && !globalMethodsIndex.isEmpty()) {
        return;
      }

      log.info("Инициализация поисковых индексов из контекста платформы");
      initializeSearchIndexes();
      indexInitialized = true;
      log.info("Поисковые индексы инициализированы. Методов: {}, Свойств: {}, Типов: {}",
              globalMethodsIndex.size(),
              globalPropertiesIndex.size(),
              typesIndex.size());
    }
  }

  /**
   * Инициализация трех отдельных индексов
   */
  private void initializeSearchIndexes() {
    globalMethodsIndex = new HashMap<>();
    globalPropertiesIndex = new HashMap<>();
    typesIndex = new HashMap<>();

    try {
      ContextProvider provider = contextService.getContextProvider();

      // Заполняем индекс глобальных методов и свойств
      var globalContext = provider.getGlobalContext();
      if (globalContext != null) {
        exporterLogic.extractMethods(globalContext).forEach(methodDef -> {
          globalMethodsIndex.put(methodDef.name().toLowerCase(), methodDef);
        });

        exporterLogic.extractProperties(globalContext).forEach(propertyDef -> {
          globalPropertiesIndex.put(propertyDef.name().toLowerCase(), propertyDef);
        });
      }

      // Заполняем индекс типов данных
      var contexts = provider.getContexts();
      if (contexts != null) {
        exporterLogic.extractTypes(List.copyOf(contexts)).forEach(typeDefinition -> {
          typesIndex.put(typeDefinition.name().toLowerCase(), typeDefinition);
        });
      }

    } catch (Exception e) {
      log.warn("Не удалось загрузить данные из контекста платформы", e);
    }
  }

  /**
   * Нормализация типа с учетом алиасов
   */
  private String normalizeType(String type) {
    if (type == null || type.trim().isEmpty()) {
      return null;
    }

    String normalized = type.trim().toLowerCase();
    return TYPE_ALIASES.getOrDefault(normalized, normalized);
  }

  /**
   * Главный интеллектуальный алгоритм поиска с 4 уровнями приоритета
   *
   * Приоритет 1 (ВЫСШИЙ): Объединение слов в составные типы
   * Приоритет 2 (ВЫСОКИЙ): Тип + член
   * Приоритет 3 (СРЕДНИЙ): Обычный поиск (существующий алгоритм)
   * Приоритет 4 (НИЗШИЙ): Поиск по словам в любом порядке
   * 
   * @param query поисковый запрос
   * @param type тип поиска (или null)
   * @return отсортированный список результатов по приоритетам
   */
  private List<Object> performIntelligentSearch(String query, String type) {
    List<SearchResult> allResults = new ArrayList<>();
    
    // Разбиваем запрос на слова для интеллектуального поиска
    String[] words = query.trim().toLowerCase().split("\\s+");
    
    // Приоритет 1: Объединение слов в составные типы
    if (words.length >= 2 && (type == null || type.equals("type"))) {
      List<SearchResult> compoundResults = searchCompoundTypes(words, query);
      allResults.addAll(compoundResults);
      
      log.debug("Приоритет 1 (составные типы): найдено {} результатов для '{}'", 
               compoundResults.size(), query);
    }
    
    // Приоритет 2: Тип + член
    if (words.length >= 2) {
      List<SearchResult> typeMemberResults = searchTypeMember(words, query);
      allResults.addAll(typeMemberResults);
      
      log.debug("Приоритет 2 (тип + член): найдено {} результатов для '{}'", 
               typeMemberResults.size(), query);
    }
    
    // Приоритет 3: Обычный поиск (существующий алгоритм)
    List<Object> regularResults = performRegularSearch(query, type);
    for (Object item : regularResults) {
      allResults.add(SearchResult.regular(item, query));
    }
    
    log.debug("Приоритет 3 (обычный поиск): найдено {} результатов для '{}'", 
             regularResults.size(), query);
    
    // Приоритет 4: Поиск по словам в любом порядке (только если нет хороших результатов)
    if (words.length >= 2 && allResults.size() < 5) {
      List<SearchResult> wordOrderResults = searchWordOrder(words, query, type);
      allResults.addAll(wordOrderResults);
      
      log.debug("Приоритет 4 (поиск по словам): найдено {} результатов для '{}'", 
               wordOrderResults.size(), query);
    }
    
    // Сортируем результаты по приоритетам и убираем дубликаты
    List<SearchResult> uniqueResults = removeDuplicates(allResults);
    uniqueResults.sort(SearchResult::compareTo);
    
    // Преобразуем обратно в список Object для совместимости
    List<Object> finalResults = uniqueResults.stream()
            .map(SearchResult::getItem)
            .collect(Collectors.toList());
    
    log.info("Интеллектуальный поиск '{}': всего найдено {} уникальных результатов", 
             query, finalResults.size());
    
    return finalResults;
  }
  
  /**
   * Существующий алгоритм поиска (Приоритет 3)
   * Переименован из performMultiIndexSearch для ясности
   */
  private List<Object> performRegularSearch(String query, String type) {
    List<Object> results = new ArrayList<>();

    // Определяем в каких индексах искать
    boolean searchMethods = type == null || type.equals("method");
    boolean searchProperties = type == null || type.equals("property");
    boolean searchTypes = type == null || type.equals("type");

    // Поиск в глобальных методах
    if (searchMethods) {
      globalMethodsIndex.entrySet().stream()
              .filter(entry -> entry.getKey().contains(query))
              .map(Map.Entry::getValue)
              .forEach(results::add);
    }

    // Поиск в глобальных свойствах
    if (searchProperties) {
      globalPropertiesIndex.entrySet().stream()
              .filter(entry -> entry.getKey().contains(query))
              .map(Map.Entry::getValue)
              .forEach(results::add);
    }

    // Поиск в типах данных
    if (searchTypes) {
      typesIndex.entrySet().stream()
              .filter(entry -> entry.getKey().contains(query))
              .map(Map.Entry::getValue)
              .forEach(results::add);
    }

    // Поиск в методах и свойствах типов (если тип не указан)
    if (type == null) {
      for (PlatformTypeDefinition typeDefinition : typesIndex.values()) {
        // Методы типов
        typeDefinition.methods().stream()
                .filter(method -> method.name().toLowerCase().contains(query))
                .forEach(results::add);

        // Свойства типов
        typeDefinition.properties().stream()
                .filter(property -> property.name().toLowerCase().contains(query))
                .forEach(results::add);
      }
    }

    // Сортировка по релевантности (точные совпадения в начале)
    return results.stream()
            .sorted((a, b) -> {
              String nameA = getObjectName(a).toLowerCase();
              String nameB = getObjectName(b).toLowerCase();

              boolean exactA = nameA.equals(query);
              boolean exactB = nameB.equals(query);

              if (exactA && !exactB) return -1;
              if (!exactA && exactB) return 1;

              boolean startsA = nameA.startsWith(query);
              boolean startsB = nameB.startsWith(query);

              if (startsA && !startsB) return -1;
              if (!startsA && startsB) return 1;

              return nameA.compareTo(nameB);
            })
            .collect(Collectors.toList());
  }
  
  /**
   * Удаляет дубликаты из результатов поиска
   * Сравнивает по имени элемента, оставляет результат с более высоким приоритетом
   */
  private List<SearchResult> removeDuplicates(List<SearchResult> results) {
    Map<String, SearchResult> uniqueResults = new HashMap<>();
    
    for (SearchResult result : results) {
      String itemName = getObjectName(result.getItem()).toLowerCase();
      
      // Если элемент уже есть, оставляем тот, у которого приоритет выше (меньше число)
      SearchResult existing = uniqueResults.get(itemName);
      if (existing == null || result.getPriority() < existing.getPriority()) {
        uniqueResults.put(itemName, result);
      }
    }
    
    return new ArrayList<>(uniqueResults.values());
  }

  /**
   * Алгоритм поиска Приоритета 1: Объединение слов в составные типы
   * Кейс: "Таблица значений" → "ТаблицаЗначений"
   * 
   * @param words массив слов запроса
   * @param originalQuery исходный запрос для контекста
   * @return список найденных составных типов
   */
  private List<SearchResult> searchCompoundTypes(String[] words, String originalQuery) {
    List<SearchResult> results = new ArrayList<>();
    
    if (words.length < 2) {
      return results; // Составные типы требуют минимум 2 слова
    }
    
    // Генерируем варианты объединения слов
    List<String> compoundVariants = generateCompoundVariants(words);
    
    // Ищем в индексе типов
    for (String variant : compoundVariants) {
      PlatformTypeDefinition type = typesIndex.get(variant.toLowerCase());
      if (type != null) {
        // Количество объединенных слов = приоритет внутри группы
        int wordsMatched = countWordsInVariant(variant, words);
        results.add(SearchResult.compoundType(type, wordsMatched, originalQuery));
        
        log.debug("Найден составной тип: '{}' для запроса '{}' (слов: {})", 
                 type.name(), originalQuery, wordsMatched);
      }
    }
    
    return results;
  }
  
  /**
   * Генерирует варианты объединения слов для поиска составных типов
   * 
   * @param words массив слов
   * @return список вариантов объединения
   */
  private List<String> generateCompoundVariants(String[] words) {
    List<String> variants = new ArrayList<>();
    
    // Вариант 1: Все слова без пробелов, все строчные
    // "таблица значений" → "таблицазначений"
    variants.add(String.join("", words));
    
    // Вариант 2: Все слова с заглавными буквами (CamelCase)
    // "таблица значений" → "ТаблицаЗначений"
    StringBuilder camelCase = new StringBuilder();
    for (String word : words) {
      if (!word.isEmpty()) {
        camelCase.append(Character.toUpperCase(word.charAt(0)));
        if (word.length() > 1) {
          camelCase.append(word.substring(1).toLowerCase());
        }
      }
    }
    variants.add(camelCase.toString());
    
    // Вариант 3: Первое слово строчными, остальные с заглавной (lowerCamelCase)
    // "таблица значений" → "таблицаЗначений"
    if (words.length >= 2) {
      StringBuilder lowerCamelCase = new StringBuilder(words[0].toLowerCase());
      for (int i = 1; i < words.length; i++) {
        String word = words[i];
        if (!word.isEmpty()) {
          lowerCamelCase.append(Character.toUpperCase(word.charAt(0)));
          if (word.length() > 1) {
            lowerCamelCase.append(word.substring(1).toLowerCase());
          }
        }
      }
      variants.add(lowerCamelCase.toString());
    }
    
    // Вариант 4: Комбинации подмножеств слов (для длинных запросов)
    // Генерируем варианты для первых N слов (N = 2, 3, 4...)
    for (int len = 2; len <= Math.min(words.length, 4); len++) {
      String[] subset = Arrays.copyOf(words, len);
      
      // Все строчные без пробелов
      variants.add(String.join("", subset));
      
      // CamelCase для подмножества
      StringBuilder subsetCamelCase = new StringBuilder();
      for (String word : subset) {
        if (!word.isEmpty()) {
          subsetCamelCase.append(Character.toUpperCase(word.charAt(0)));
          if (word.length() > 1) {
            subsetCamelCase.append(word.substring(1).toLowerCase());
          }
        }
      }
      variants.add(subsetCamelCase.toString());
    }
    
    // Удаляем дубликаты и возвращаем
    return variants.stream().distinct().collect(Collectors.toList());
  }
  
  /**
   * Подсчитывает количество слов из исходного запроса, 
   * которые вошли в найденный вариант
   * 
   * @param variant найденный вариант составного типа
   * @param originalWords исходные слова запроса
   * @return количество совпавших слов
   */
  private int countWordsInVariant(String variant, String[] originalWords) {
    String lowerVariant = variant.toLowerCase();
    int count = 0;
    
    for (String word : originalWords) {
      if (lowerVariant.contains(word.toLowerCase())) {
        count++;
      }
    }
    
    return count;
  }

  /**
   * Алгоритм поиска Приоритета 2: Тип + член
   * Кейс: "Таблица значений количество" → тип "ТаблицаЗначений" + метод "количество"
   * Кейс: "Таблица колонки" → тип "Таблица" + свойство "колонки"
   * 
   * @param words массив слов запроса
   * @param originalQuery исходный запрос для контекста
   * @return список найденных результатов тип + член
   */
  private List<SearchResult> searchTypeMember(String[] words, String originalQuery) {
    List<SearchResult> results = new ArrayList<>();
    
    if (words.length < 2) {
      return results; // Нужно минимум 2 слова для тип + член
    }
    
    // Пробуем разделить запрос на [тип] + [член]
    // Попытка найти тип в первых N словах (N = 1, 2, 3...)
    for (int typeWordCount = 1; typeWordCount < words.length; typeWordCount++) {
      // Извлекаем слова для типа
      String[] typeWords = Arrays.copyOfRange(words, 0, typeWordCount);
      // Извлекаем слова для члена
      String[] memberWords = Arrays.copyOfRange(words, typeWordCount, words.length);
      
      // Ищем тип используя алгоритм составных типов
      List<String> typeVariants = generateCompoundVariants(typeWords);
      
      for (String typeVariant : typeVariants) {
        PlatformTypeDefinition type = typesIndex.get(typeVariant.toLowerCase());
        if (type != null) {
          // Найден тип, теперь ищем член в этом типе
          String memberQuery = String.join(" ", memberWords).toLowerCase();
          List<Object> foundMembers = searchMembersInType(type, memberQuery);
          
          for (Object member : foundMembers) {
            results.add(SearchResult.typeMember(member, originalQuery));
            
            log.debug("Найден член '{}' типа '{}' для запроса '{}'", 
                     getObjectName(member), type.name(), originalQuery);
          }
        }
      }
    }
    
    return results;
  }
  
  /**
   * Поиск членов (методов и свойств) в конкретном типе
   * 
   * @param type тип для поиска
   * @param memberQuery запрос для поиска члена
   * @return список найденных членов
   */
  private List<Object> searchMembersInType(PlatformTypeDefinition type, String memberQuery) {
    List<Object> members = new ArrayList<>();
    
    // Нормализуем запрос для поиска
    String normalizedQuery = memberQuery.trim().toLowerCase();
    
    // Поиск в методах типа
    for (MethodDefinition method : type.methods()) {
      String methodName = method.name().toLowerCase();
      
      // Точное совпадение имеет приоритет
      if (methodName.equals(normalizedQuery)) {
        members.add(0, method); // Добавляем в начало
      }
      // Начинается с запроса
      else if (methodName.startsWith(normalizedQuery)) {
        members.add(method);
      }
      // Содержит запрос
      else if (methodName.contains(normalizedQuery)) {
        members.add(method);
      }
      // Поиск по словам (если запрос состоит из нескольких слов)
      else if (normalizedQuery.contains(" ")) {
        String[] queryWords = normalizedQuery.split("\\s+");
        boolean allWordsMatch = true;
        for (String word : queryWords) {
          if (!methodName.contains(word)) {
            allWordsMatch = false;
            break;
          }
        }
        if (allWordsMatch) {
          members.add(method);
        }
      }
    }
    
    // Поиск в свойствах типа
    for (PropertyDefinition property : type.properties()) {
      String propertyName = property.name().toLowerCase();
      
      // Точное совпадение имеет приоритет
      if (propertyName.equals(normalizedQuery)) {
        members.add(0, property); // Добавляем в начало
      }
      // Начинается с запроса
      else if (propertyName.startsWith(normalizedQuery)) {
        members.add(property);
      }
      // Содержит запрос
      else if (propertyName.contains(normalizedQuery)) {
        members.add(property);
      }
      // Поиск по словам (если запрос состоит из нескольких слов)
      else if (normalizedQuery.contains(" ")) {
        String[] queryWords = normalizedQuery.split("\\s+");
        boolean allWordsMatch = true;
        for (String word : queryWords) {
          if (!propertyName.contains(word)) {
            allWordsMatch = false;
            break;
          }
        }
        if (allWordsMatch) {
          members.add(property);
        }
      }
    }
    
    return members;
  }

  /**
   * Алгоритм поиска Приоритета 4: Поиск по словам в любом порядке
   * Кейс: "Запрос выборка" → тип "ВыборкаИзРезультатаЗапроса"
   * Кейс: "Документ проведение" → поиск типов содержащих "документ" И "проведение"
   * 
   * @param words массив слов запроса  
   * @param originalQuery исходный запрос для контекста
   * @param type тип поиска (или null для всех типов)
   * @return список найденных результатов по словам
   */
  private List<SearchResult> searchWordOrder(String[] words, String originalQuery, String type) {
    List<SearchResult> results = new ArrayList<>();
    
    if (words.length < 2) {
      return results; // Поиск по словам требует минимум 2 слова
    }
    
    // Определяем в каких индексах искать
    boolean searchMethods = type == null || type.equals("method");
    boolean searchProperties = type == null || type.equals("property");
    boolean searchTypes = type == null || type.equals("type");
    
    // Поиск в глобальных методах
    if (searchMethods) {
      for (MethodDefinition method : globalMethodsIndex.values()) {
        int matchedWords = countMatchingWords(method.name().toLowerCase(), words);
        if (matchedWords == words.length) { // Все слова должны совпадать
          results.add(SearchResult.wordOrder(method, matchedWords, originalQuery));
        }
      }
    }
    
    // Поиск в глобальных свойствах
    if (searchProperties) {
      for (PropertyDefinition property : globalPropertiesIndex.values()) {
        int matchedWords = countMatchingWords(property.name().toLowerCase(), words);
        if (matchedWords == words.length) { // Все слова должны совпадать
          results.add(SearchResult.wordOrder(property, matchedWords, originalQuery));
        }
      }
    }
    
    // Поиск в типах данных
    if (searchTypes) {
      for (PlatformTypeDefinition typeDefinition : typesIndex.values()) {
        int matchedWords = countMatchingWords(typeDefinition.name().toLowerCase(), words);
        if (matchedWords == words.length) { // Все слова должны совпадать
          results.add(SearchResult.wordOrder(typeDefinition, matchedWords, originalQuery));
        }
      }
    }
    
    // Поиск в методах и свойствах типов (если тип не указан)
    if (type == null) {
      for (PlatformTypeDefinition typeDefinition : typesIndex.values()) {
        // Методы типов
        for (MethodDefinition method : typeDefinition.methods()) {
          int matchedWords = countMatchingWords(method.name().toLowerCase(), words);
          if (matchedWords == words.length) { // Все слова должны совпадать
            results.add(SearchResult.wordOrder(method, matchedWords, originalQuery));
          }
        }
        
        // Свойства типов
        for (PropertyDefinition property : typeDefinition.properties()) {
          int matchedWords = countMatchingWords(property.name().toLowerCase(), words);
          if (matchedWords == words.length) { // Все слова должны совпадать
            results.add(SearchResult.wordOrder(property, matchedWords, originalQuery));
          }
        }
      }
    }
    
    return results;
  }
  
  /**
   * Подсчитывает количество слов из запроса, которые содержатся в имени элемента
   * 
   * @param elementName имя элемента (в нижнем регистре)
   * @param queryWords массив слов запроса
   * @return количество совпавших слов
   */
  private int countMatchingWords(String elementName, String[] queryWords) {
    int matchedCount = 0;
    
    for (String word : queryWords) {
      String lowerWord = word.toLowerCase();
      if (elementName.contains(lowerWord)) {
        matchedCount++;
      }
    }
    
    return matchedCount;
  }

  /**
   * Получение имени объекта независимо от его типа
   */
  private String getObjectName(Object obj) {
    if (obj instanceof MethodDefinition) {
      return ((MethodDefinition) obj).name();
    } else if (obj instanceof PropertyDefinition) {
      return ((PropertyDefinition) obj).name();
    } else if (obj instanceof PlatformTypeDefinition) {
      return ((PlatformTypeDefinition) obj).name();
    }
    return "";
  }

  /**
   * Поиск точного совпадения в соответствующих индексах
   */
  private Optional<Object> findExactMatch(String name, String type) {
    if (type == null) {
      // Поиск во всех индексах
      return Stream.of(
                      Optional.ofNullable(globalMethodsIndex.get(name)).map(o -> (Object) o),
                      Optional.ofNullable(globalPropertiesIndex.get(name)).map(o -> (Object) o),
                      Optional.ofNullable(typesIndex.get(name)).map(o -> (Object) o)
              ).filter(Optional::isPresent)
              .map(Optional::get)
              .findFirst();
    }

    return switch (type) {
      case "method" -> Optional.ofNullable(globalMethodsIndex.get(name)).map(o -> o);
      case "property" -> Optional.ofNullable(globalPropertiesIndex.get(name)).map(o -> o);
      case "type" -> Optional.ofNullable(typesIndex.get(name)).map(o -> o);
      default -> Optional.empty();
    };
  }

  /**
   * Класс для хранения результатов поиска с приоритетами
   */
  @Getter
  private static class SearchResult {
    // Геттеры
    private final Object item;
    private final int priority; // 1-4, где 1 - высший приоритет
    private final int wordsMatched; // количество объединенных слов для приоритета 1
    private final String matchType; // тип совпадения: "compound-type", "type-member", "regular", "word-order"
    private final String originalQuery; // исходный запрос для контекста
    
    public SearchResult(Object item, int priority, int wordsMatched, String matchType, String originalQuery) {
      this.item = item;
      this.priority = priority;
      this.wordsMatched = wordsMatched;
      this.matchType = matchType;
      this.originalQuery = originalQuery;
    }
    
    // Статические методы для создания результатов разных типов
    public static SearchResult compoundType(Object item, int wordsMatched, String originalQuery) {
      return new SearchResult(item, 1, wordsMatched, "compound-type", originalQuery);
    }
    
    public static SearchResult typeMember(Object item, String originalQuery) {
      return new SearchResult(item, 2, 0, "type-member", originalQuery);
    }
    
    public static SearchResult regular(Object item, String originalQuery) {
      return new SearchResult(item, 3, 0, "regular", originalQuery);
    }
    
    public static SearchResult wordOrder(Object item, int wordsMatched, String originalQuery) {
      return new SearchResult(item, 4, wordsMatched, "word-order", originalQuery);
    }

    /**
     * Сравнение результатов для сортировки по приоритету
     * Приоритет 1 - высший, 4 - низший
     * В рамках одного приоритета сортируем по количеству совпавших слов (больше = лучше)
     */
    public int compareTo(SearchResult other) {
      // Сначала по приоритету (1 - лучше, 4 - хуже)
      int priorityCompare = Integer.compare(this.priority, other.priority);
      if (priorityCompare != 0) {
        return priorityCompare;
      }
      
      // Для приоритета 1 и 4 учитываем количество совпавших слов (больше = лучше)
      if (this.priority == 1 || this.priority == 4) {
        int wordsCompare = Integer.compare(other.wordsMatched, this.wordsMatched);
        if (wordsCompare != 0) {
          return wordsCompare;
        }
      }
      
      // В конце по алфавитному порядку имен
      String nameA = getObjectName(this.item).toLowerCase();
      String nameB = getObjectName(other.item).toLowerCase();
      return nameA.compareTo(nameB);
    }
    
    private String getObjectName(Object obj) {
      if (obj instanceof MethodDefinition) {
        return ((MethodDefinition) obj).name();
      } else if (obj instanceof PropertyDefinition) {
        return ((PropertyDefinition) obj).name();
      } else if (obj instanceof PlatformTypeDefinition) {
        return ((PlatformTypeDefinition) obj).name();
      }
      return "";
    }
  }
}