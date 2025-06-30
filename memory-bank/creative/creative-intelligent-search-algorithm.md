# 🎨 CREATIVE PHASE: Интеллектуальный алгоритм составных запросов

## 📋 Описание компонента

**Компонент:** Интеллектуальный алгоритм поиска с 4-х уровневой приоритизацией  
**Цель:** Реализовать понимание русскоязычных составных запросов с интеллектуальной интерпретацией  
**Контекст:** Расширение MCP сервера для поддержки запросов вида "Таблица значений количество"

### Что делает компонент:
1. **Анализирует** пользовательский запрос на русском языке
2. **Разделяет** запрос по алгоритму 4-х приоритетов
3. **Выполняет** поиск с учетом приоритетности результатов
4. **Объединяет** результаты с корректной сортировкой по релевантности

## 🎯 Требования и ограничения

### Функциональные требования:
- ✅ **Приоритет 1**: Объединение слов в составные типы ("Таблица значений" → "ТаблицаЗначений")
- ✅ **Приоритет 2**: Поиск "тип + член" ("Таблица значений количество" → тип + его методы)
- ✅ **Приоритет 3**: Обычный поиск (существующий алгоритм)
- ✅ **Приоритет 4**: Поиск по словам в любом порядке ("Запрос выборка" → "ВыборкаИзРезультатаЗапроса")
- ✅ **Русскоязычные алиасы**: "объект", "класс", "тип" → "type"

### Технические ограничения:
- **Производительность**: Время ответа не более 2 секунд
- **Память**: Не создавать более 1000 промежуточных объектов
- **Совместимость**: Не ломать существующие API методы
- **Масштабируемость**: Работать с индексами размером до 10,000 элементов

### Архитектурные ограничения:
- **Интеграция**: Использовать существующие индексы (globalMethodsIndex, globalPropertiesIndex, typesIndex)
- **Кэширование**: Поддерживать @Cacheable аннотации
- **Логирование**: Интегрироваться с существующим Slf4j

## 🔄 Варианты архитектуры

### Вариант 1: Единый метод с цепочкой if-else

```java
public List<SearchResult> performIntelligentSearch(String query, String type) {
    String[] words = query.trim().toLowerCase().split("\\s+");
    
    // Приоритет 1: Составные типы
    List<SearchResult> results = searchCompoundTypes(words);
    if (!results.isEmpty()) return results;
    
    // Приоритет 2: Тип + член
    results = searchTypeMember(words);
    if (!results.isEmpty()) return results;
    
    // Приоритет 3: Обычный поиск
    results = performRegularSearch(query, type);
    if (!results.isEmpty()) return results;
    
    // Приоритет 4: Поиск по словам
    return searchWordOrder(words);
}
```

#### Плюсы:
- ✅ **Простота**: Легко понять логику выполнения
- ✅ **Производительность**: Ранний выход при нахождении результатов
- ✅ **Отладка**: Легко добавить логирование на каждом этапе

#### Минусы:
- ❌ **Ограниченность**: Возвращает только результаты одного приоритета
- ❌ **Потеря данных**: Не учитывает результаты других приоритетов
- ❌ **Негибкость**: Сложно настроить смешанные результаты

### Вариант 2: Параллельный поиск с ранжированием

```java
public List<SearchResult> performIntelligentSearch(String query, String type) {
    String[] words = query.trim().toLowerCase().split("\\s+");
    
    // Параллельный поиск по всем приоритетам
    List<SearchResult> priority1 = searchCompoundTypes(words);
    List<SearchResult> priority2 = searchTypeMember(words);
    List<SearchResult> priority3 = performRegularSearch(query, type);
    List<SearchResult> priority4 = searchWordOrder(words);
    
    // Объединение и сортировка по приоритету
    return Stream.of(priority1, priority2, priority3, priority4)
        .flatMap(List::stream)
        .sorted(Comparator.comparing(SearchResult::getPriority)
                .thenComparing(SearchResult::getScore))
        .collect(Collectors.toList());
}
```

#### Плюсы:
- ✅ **Полнота**: Возвращает результаты всех приоритетов
- ✅ **Гибкость**: Можно настроить количество результатов каждого приоритета
- ✅ **Релевантность**: Лучшие результаты поднимаются наверх

#### Минусы:
- ❌ **Производительность**: Выполняет все алгоритмы даже при ранних совпадениях
- ❌ **Сложность**: Более сложная логика объединения
- ❌ **Память**: Создает больше промежуточных объектов

### Вариант 3: Адаптивный алгоритм с эвристиками

```java
public List<SearchResult> performIntelligentSearch(String query, String type) {
    String[] words = query.trim().toLowerCase().split("\\s+");
    
    // Эвристика определения наиболее вероятного приоритета
    int suggestedPriority = determinePriority(words, type);
    
    List<SearchResult> results = switch (suggestedPriority) {
        case 1 -> searchWithFallback(words, this::searchCompoundTypes);
        case 2 -> searchWithFallback(words, this::searchTypeMember);
        case 3 -> searchWithFallback(words, w -> performRegularSearch(query, type));
        case 4 -> searchWithFallback(words, this::searchWordOrder);
        default -> performFullSearch(words, query, type);
    };
    
    return results;
}

private int determinePriority(String[] words, String type) {
    // Логика определения наиболее вероятного приоритета
    if (words.length == 2 && hasPotentialCompoundType(words)) return 1;
    if (words.length >= 3 && hasTypeInFirstWords(words)) return 2;
    if (type != null && !type.isEmpty()) return 3;
    return 4;
}
```

#### Плюсы:
- ✅ **Интеллектуальность**: Предугадывает наиболее вероятный результат
- ✅ **Производительность**: Начинает с наиболее вероятного алгоритма
- ✅ **Адаптивность**: Может учиться на основе статистики запросов

#### Минусы:
- ❌ **Сложность**: Самая сложная реализация
- ❌ **Ошибки**: Риск неправильного определения приоритета
- ❌ **Поддержка**: Требует настройки эвристик

### Вариант 4: Поэтапный поиск с лимитами

```java
public List<SearchResult> performIntelligentSearch(String query, String type) {
    String[] words = query.trim().toLowerCase().split("\\s+");
    List<SearchResult> results = new ArrayList<>();
    
    // Приоритет 1: до 3 результатов
    results.addAll(searchCompoundTypes(words).stream()
        .limit(3).collect(Collectors.toList()));
    
    // Приоритет 2: до 5 результатов
    results.addAll(searchTypeMember(words).stream()
        .limit(5).collect(Collectors.toList()));
    
    // Приоритет 3: до 7 результатов
    results.addAll(performRegularSearch(query, type).stream()
        .limit(7).collect(Collectors.toList()));
    
    // Приоритет 4: до 10 результатов
    results.addAll(searchWordOrder(words).stream()
        .limit(10).collect(Collectors.toList()));
    
    return results.stream()
        .sorted(Comparator.comparing(SearchResult::getPriority))
        .collect(Collectors.toList());
}
```

#### Плюсы:
- ✅ **Баланс**: Сочетает полноту и производительность
- ✅ **Предсказуемость**: Всегда возвращает смешанные результаты
- ✅ **Контроль**: Можно настроить лимиты для каждого приоритета

#### Минусы:
- ❌ **Фиксированность**: Жесткие лимиты могут не подходить для всех случаев
- ❌ **Производительность**: Все равно выполняет все алгоритмы

## ⚖️ Анализ вариантов

### Критерии оценки:
1. **Производительность** (время выполнения)
2. **Релевантность** (качество результатов)
3. **Простота реализации** (сложность кода)
4. **Поддержка** (легкость изменений)
5. **Память** (расход ресурсов)

### Оценочная таблица:

| Критерий | Вариант 1 | Вариант 2 | Вариант 3 | Вариант 4 |
|----------|-----------|-----------|-----------|-----------|
| Производительность | 🟢 9/10 | 🟡 6/10 | 🟢 8/10 | 🟡 7/10 |
| Релевантность | 🟡 6/10 | 🟢 9/10 | 🟢 8/10 | 🟢 8/10 |
| Простота | 🟢 9/10 | 🟡 7/10 | 🔴 4/10 | 🟡 6/10 |
| Поддержка | 🟢 8/10 | 🟡 7/10 | 🔴 5/10 | 🟡 7/10 |
| Память | 🟢 9/10 | 🟡 6/10 | 🟢 8/10 | 🟡 7/10 |
| **ИТОГО** | **41/50** | **35/50** | **33/50** | **35/50** |

## ✅ Рекомендуемый подход

### Выбор: Вариант 1 с модификацией для улучшения релевантности

**Обоснование решения:**
1. **Высокая производительность**: Ранний выход при нахождении результатов
2. **Простота реализации**: Легко понять, отладить и поддерживать
3. **Низкий расход памяти**: Не создает лишних промежуточных объектов
4. **Хорошая поддержка**: Легко добавить новые приоритеты или изменить логику

### Модификация для улучшения релевантности:

```java
public List<SearchResult> performIntelligentSearch(String query, String type) {
    String[] words = query.trim().toLowerCase().split("\\s+");
    
    // Приоритет 1: Составные типы
    List<SearchResult> results = searchCompoundTypes(words);
    if (results.size() >= 3) return results; // Достаточно хороших результатов
    
    // Приоритет 2: Тип + член
    List<SearchResult> priority2 = searchTypeMember(words);
    results.addAll(priority2);
    if (results.size() >= 5) return results.stream()
        .sorted(Comparator.comparing(SearchResult::getPriority))
        .collect(Collectors.toList());
    
    // Приоритет 3: Обычный поиск
    List<SearchResult> priority3 = performRegularSearch(query, type);
    results.addAll(priority3);
    if (results.size() >= 8) return results.stream()
        .sorted(Comparator.comparing(SearchResult::getPriority))
        .collect(Collectors.toList());
    
    // Приоритет 4: Поиск по словам
    results.addAll(searchWordOrder(words));
    
    return results.stream()
        .sorted(Comparator.comparing(SearchResult::getPriority))
        .collect(Collectors.toList());
}
```

## 📝 Руководство по реализации

### Ключевые компоненты:

1. **SearchResult класс** - для хранения результатов с приоритетами
2. **Расширение TYPE_ALIASES** - добавление русскоязычных алиасов
3. **searchCompoundTypes()** - алгоритм приоритета 1
4. **searchTypeMember()** - алгоритм приоритета 2
5. **searchWordOrder()** - алгоритм приоритета 4
6. **performIntelligentSearch()** - главный метод координации

### Временная сложность: O(n) где n - размер индекса
### Пространственная сложность: O(k) где k - количество результатов

## ✓ Верификация решения

**Тестовые кейсы:**
1. "Таблица значений" → Приоритет 1 → "ТаблицаЗначений" ✅
2. "Таблица значений количество" → Приоритет 2 → тип + метод ✅  
3. "параметры" → Приоритет 3 → обычный поиск ✅
4. "Запрос выборка" → Приоритет 4 → "ВыборкаИзРезультатаЗапроса" ✅

**Следующий шаг:** IMPLEMENT - реализация алгоритма  
**Ожидаемое время:** 6-8 часов  
**Риски:** Минимальные 