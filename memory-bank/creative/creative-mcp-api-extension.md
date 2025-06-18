# 🎨🎨🎨 ENTERING CREATIVE PHASE: ARCHITECTURE DESIGN

## Компонент: Расширенное API для MCP сервера поиска по платформе 1С

### Описание компонента
Система поиска должна предоставлять полную информацию о возможностях платформы 1С Предприятие, включая:
- Глобальные методы и свойства
- Типы данных с их методами, свойствами и конструкторами
- Универсальный поиск по всем категориям
- Детальную информацию о каждом элементе
- Поддержку алиасов типов для удобства LLM

### Требования и ограничения

#### Функциональные требования:
1. **Поиск объектов** - глобальные методы, свойства, типы данных, их методы, свойства и конструкторы
2. **Универсальный поиск** - возможность поиска без указания типа (все варианты)
3. **Детальная информация** о каждом аспекте возможностей платформы
4. **Краткие сводки** для типов с списком их членов
5. **Алиасы типов** - поддержка object, type, class для удобства LLM

#### Технические требования:
1. **Производительность** - время отклика < 1 секунды
2. **Кэширование** - эффективное использование памяти
3. **Расширяемость** - возможность добавления новых типов поиска
4. **Обратная совместимость** - сохранение существующих API

#### Ограничения:
1. **Память** - индексы должны быть компактными
2. **Сложность** - простота понимания и поддержки кода
3. **Производительность инициализации** - быстрая загрузка индексов
4. **Консистентность данных** - синхронизация между индексами

---

## Вариант 1: Унифицированный индекс с маркерами типов

### Описание
Единый индекс `Map<String, SearchResult>` с расширенными SearchResult, содержащими информацию о типе объекта и его родителе.

### Структура данных:
```java
// Расширенный SearchResult
public class SearchResult {
    private String name;
    private SearchResultType type; // method, property, type, constructor, method_of_type, property_of_type
    private String parentType; // для методов/свойств типов
    private String signature;
    private String description;
    private List<String> parameters;
    private String returnType;
    private List<TypeMemberInfo> membersList; // для типов
    private Object originalObject;
    private int score;
}

// Типы результатов с алиасами
public enum SearchResultType {
    method, property, type, constructor, 
    method_of_type, property_of_type,
    
    // Алиасы
    object(type), class(type), datatype(type)
}
```

### Алгоритм инициализации:
1. Загрузка глобальных методов и свойств
2. Загрузка типов данных
3. Для каждого типа - извлечение методов, свойств, конструкторов
4. Добавление всех элементов в единый индекс с уникальными ключами

### Преимущества:
- ✅ Простота реализации
- ✅ Единый алгоритм поиска
- ✅ Легкая расширяемость
- ✅ Консистентность данных

### Недостатки:
- ❌ Большой размер индекса в памяти
- ❌ Сложность формирования уникальных ключей
- ❌ Дублирование данных для методов типов
- ❌ Медленная инициализация

---

## Вариант 2: Раздельные индексы с фасадом

### Описание
Три отдельных индекса с единым фасадом для поиска:
- `globalMethodsIndex: Map<String, MethodDefinition>`
- `globalPropertiesIndex: Map<String, PropertyDefinition>`
- `typesIndex: Map<String, PlatformTypeDefinition>`

### Структура данных:
```java
public class MultiIndexService {
    private Map<String, MethodDefinition> globalMethodsIndex;
    private Map<String, PropertyDefinition> globalPropertiesIndex;
    private Map<String, PlatformTypeDefinition> typesIndex;
    
    // Кэш для методов типов
    private Map<String, List<MethodDefinition>> typeMethodsCache;
    private Map<String, List<PropertyDefinition>> typePropertiesCache;
}
```

### Алгоритм поиска:
1. Параллельный поиск по всем индексам
2. Формирование SearchResult на лету из DTO
3. Объединение результатов с приоритизацией
4. Кэширование частоиспользуемых элементов типов

### Преимущества:
- ✅ Эффективное использование памяти
- ✅ Быстрая инициализация
- ✅ Простота обновления данных
- ✅ Прямое использование DTO

### Недостатки:
- ❌ Сложность алгоритма поиска
- ❌ Необходимость синхронизации индексов
- ❌ Сложность ранжирования смешанных результатов
- ❌ Дополнительные кэши для производительности

---

## Вариант 3: Иерархический индекс с категориями

### Описание
Индекс с иерархической структурой по категориям:
```java
Map<SearchCategory, Map<String, SearchResult>> hierarchicalIndex
```

### Структура данных:
```java
public enum SearchCategory {
    GLOBAL_METHODS, GLOBAL_PROPERTIES, TYPES, 
    TYPE_METHODS, TYPE_PROPERTIES, CONSTRUCTORS
}

public class HierarchicalIndex {
    private Map<SearchCategory, Map<String, SearchResult>> categorizedIndex;
    private Map<String, SearchCategory> nameToCategory; // для быстрого поиска
}
```

### Алгоритм поиска:
1. Определение категорий для поиска
2. Поиск по соответствующим категориям
3. Агрегация результатов
4. Ранжирование с учетом категорий

### Преимущества:
- ✅ Четкая структура данных
- ✅ Быстрый поиск по категориям
- ✅ Легкая расширяемость новыми категориями
- ✅ Оптимизация по типам запросов

### Недостатки:
- ❌ Сложность универсального поиска
- ❌ Дублирование данных между категориями
- ❌ Необходимость поддержки маппинга имен
- ❌ Сложность алгоритма ранжирования

---

## Вариант 4: Гибридный подход с индексами типов

### Описание
Комбинация раздельных индексов с оптимизацией под типы запросов:
- Базовые индексы: global, types
- Динамические индексы: type_members (генерируются по запросу)
- Кэширование: популярные типы и их элементы

### Структура данных:
```java
public class HybridSearchService {
    // Основные индексы
    private Map<String, MethodDefinition> globalMethodsIndex;
    private Map<String, PropertyDefinition> globalPropertiesIndex;
    private Map<String, PlatformTypeDefinition> typesIndex;
    
    // Динамические кэши
    private LRUCache<String, List<SearchResult>> typeMembersCache;
    private LRUCache<String, SearchResult> detailedInfoCache;
    
    // Алиасы
    private Map<String, SearchResultType> typeAliases;
}
```

### Алгоритм поиска:
1. Определение типа запроса (global/type-specific)
2. Поиск в соответствующих индексах
3. Динамическое извлечение членов типов при необходимости
4. Кэширование результатов

### Преимущества:
- ✅ Оптимальная производительность
- ✅ Эффективное использование памяти
- ✅ Адаптивность под паттерны использования
- ✅ Простота базовых операций

### Недостатки:
- ❌ Сложность реализации
- ❌ Необходимость мониторинга кэшей
- ❌ Потенциальная несогласованность кэшей
- ❌ Сложность отладки

---

## Анализ вариантов

### Критерии оценки:
1. **Производительность** - время поиска и инициализации
2. **Память** - объем используемой памяти
3. **Сложность** - легкость понимания и поддержки
4. **Расширяемость** - возможность добавления новых типов
5. **Надежность** - стабильность и предсказуемость

### Сравнительная таблица:

| Критерий | Вариант 1 | Вариант 2 | Вариант 3 | Вариант 4 |
|----------|-----------|-----------|-----------|-----------|
| Производительность | 6/10 | 8/10 | 7/10 | 9/10 |
| Память | 4/10 | 9/10 | 6/10 | 8/10 |
| Сложность | 9/10 | 7/10 | 6/10 | 5/10 |
| Расширяемость | 8/10 | 8/10 | 9/10 | 7/10 |
| Надежность | 9/10 | 7/10 | 8/10 | 6/10 |
| **ИТОГО** | **36/50** | **39/50** | **36/50** | **35/50** |

---

## Рекомендуемый подход: Вариант 2 (Раздельные индексы с фасадом) - УПРОЩЕННЫЙ

### Обоснование выбора:
1. **Оптимальный баланс** производительности и сложности
2. **Эффективное использование памяти** - только необходимые данные
3. **Прямое использование DTO** - без промежуточных слоев
4. **Простота тестирования** - каждый индекс независим
5. **Минимальная сложность** - убраны лишние абстракции

### Упрощенная архитектура:

```java
@Service
public class EnhancedPlatformApiSearchService {
    
    // Основные индексы - только DTO объекты
    private Map<String, MethodDefinition> globalMethodsIndex;
    private Map<String, PropertyDefinition> globalPropertiesIndex;
    private Map<String, PlatformTypeDefinition> typesIndex;
    
    // Алиасы типов
    private static final Map<String, String> TYPE_ALIASES = Map.of(
        "object", "type",
        "class", "type",
        "datatype", "type"
    );
    
    // API методы возвращают строки (markdown)
    public String search(String query, String type, Integer limit);
    public String getInfo(String name, String type);
    public String getMember(String typeName, String memberName);
    public String getConstructors(String typeName);
    public String getMembers(String typeName);
}
```

### Ключевые компоненты (упрощенные):

1. **Индексы данных:**
   - `globalMethodsIndex` - Map<String, MethodDefinition>
   - `globalPropertiesIndex` - Map<String, PropertyDefinition>
   - `typesIndex` - Map<String, PlatformTypeDefinition>

2. **Без промежуточных слоев:**
   - ❌ SearchResult убран - работаем напрямую с DTO
   - ❌ Кэши методов/свойств типов убраны - данные уже в PlatformTypeDefinition
   - ✅ Только Spring кэш на уровне методов API (@Cacheable)

3. **Алгоритм поиска:**
   - Параллельный поиск по индексам
   - Прямая работа с DTO объектами
   - Формирование markdown в MarkdownFormatterService
   - Приоритизация результатов при объединении

4. **Система алиасов:**
   - Простой Map<String, String> для маппинга
   - Нормализация типов при поиске

### Алгоритм поиска (упрощенный):
1. Нормализация запроса и типа (обработка алиасов)
2. Определение индексов для поиска
3. Параллельный поиск по выбранным индексам → List<DTO>
4. Приоритизация и лимитирование списка DTO
5. Передача списка DTO в MarkdownFormatterService
6. Возврат готового markdown

### Алгоритм получения информации о типе:
1. Поиск типа в typesIndex → PlatformTypeDefinition
2. Передача PlatformTypeDefinition в MarkdownFormatterService
3. Формирование краткой сводки из methods/properties/constructors
4. Возврат готового markdown

### Преимущества упрощенной архитектуры:
- ✅ **Меньше кода** - убраны промежуточные слои
- ✅ **Меньше памяти** - нет дублирования данных
- ✅ **Проще тестирование** - меньше компонентов
- ✅ **Быстрее разработка** - меньше абстракций
- ✅ **Проще отладка** - прямой поток данных DTO → Markdown

# 🎨🎨🎨 EXITING CREATIVE PHASE 