package ru.alkoleft.context.platform.mcp;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.exporter.BaseExporterLogic;

import java.util.ArrayList;
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

  // Алиасы типов для удобства LLM
  private static final Map<String, String> TYPE_ALIASES = Map.of(
          "object", "type",
          "class", "type",
          "datatype", "type"
  );
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
  public String search(String query, String type, Integer limit) {
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

    // Преобразование пользовательских запросов в более точные термины
    normalizedQuery = enhanceSearchQuery(normalizedQuery);

    // Поиск в соответствующих индексах
    List<Object> searchResults = performMultiIndexSearch(normalizedQuery, normalizeType(type));

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
  public String getInfo(String name, String type) {
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
  public String getMember(String typeName, String memberName) {
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
  public String getConstructors(String typeName) {
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
  public String getMembers(String typeName) {
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
      log.warn("Не удалось загрузить данные из контекста платформы, используем фикстуры", e);
    }
  }

  /**
   * Преобразование пользовательских запросов в более точные поисковые термины
   */
  private String enhanceSearchQuery(String originalQuery) {
    // Словарь для преобразования пользовательских запросов в термины 1С
    Map<String, String> queryEnhancements = new HashMap<>();
    queryEnhancements.put("параметры запроса", "запрос параметр установить");
    queryEnhancements.put("получение параметров запроса", "запрос получить параметр установитьпараметр");
    queryEnhancements.put("работа с запросами", "запрос выполнить установить");
    queryEnhancements.put("параметры", "параметр установить получить");
    queryEnhancements.put("работа со справочниками", "справочник найти создать");
    queryEnhancements.put("создание документов", "документ создать записать");
    queryEnhancements.put("получение данных", "получить данные найти выбрать");
    queryEnhancements.put("работа с базой", "запрос выполнить база данных");
    queryEnhancements.put("методы строк", "строка врег нрег сокрлп длина");
    queryEnhancements.put("работа с датами", "дата формат текущая добавить");
    queryEnhancements.put("массивы", "массив добавить найти количество");
    queryEnhancements.put("таблицы значений", "таблицазначений добавить найти колонка строка");

    String enhanced = originalQuery;

    // Попытка найти точное соответствие
    for (Map.Entry<String, String> entry : queryEnhancements.entrySet()) {
      if (enhanced.contains(entry.getKey())) {
        enhanced = enhanced.replace(entry.getKey(), entry.getValue());
        break;
      }
    }

    // Дополнительные улучшения для общих терминов
    enhanced = enhanced
            .replace("как получить", "получить")
            .replace("как создать", "создать")
            .replace("как найти", "найти")
            .replace("способы", "метод")
            .replace("функции", "метод")
            .replace("свойства", "свойство")
            .replace(" api", "")
            .replace("платформы", "")
            .replace("1с", "");

    return enhanced.trim();
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
   * Поиск по нескольким индексам с возвращением DTO объектов
   */
  private List<Object> performMultiIndexSearch(String query, String type) {
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

    switch (type) {
      case "method":
        return Optional.ofNullable(globalMethodsIndex.get(name)).map(o -> o);
      case "property":
        return Optional.ofNullable(globalPropertiesIndex.get(name)).map(o -> o);
      case "type":
        return Optional.ofNullable(typesIndex.get(name)).map(o -> o);
      default:
        return Optional.empty();
    }
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
}