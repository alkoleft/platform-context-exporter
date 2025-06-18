# Технический контекст bsl-context-exporter

## Технологический стек
- **Язык:** Java 17
- **Фреймворк:** Spring Boot 3.5.0
- **Сборка:** Gradle 8.x + Kotlin DSL
- **CLI:** picocli 4.7.5
- **Сериализация:** Jackson 2.15.2
- **Логирование:** Logback 1.5.18 + SLF4J
- **Кодогенерация:** Lombok 8.11 (плагин io.freefair.lombok)
- **Тестирование:** JUnit 5 + AssertJ + Spring Boot Test
- **MCP Server:** Spring AI MCP Server 1.0.0

## Принципы разработки

### SOLID принципы (ОБЯЗАТЕЛЬНО)
- **S** - Single Responsibility Principle: каждый класс отвечает за одну задачу
- **O** - Open/Closed Principle: открыт для расширения, закрыт для изменения
- **L** - Liskov Substitution Principle: подклассы должны заменять базовые классы
- **I** - Interface Segregation Principle: интерфейсы должны быть специфичными
- **D** - Dependency Inversion Principle: зависимость от абстракций, не от конкретики

### Lombok - обязательное использование
- **@Data** - для POJO классов (геттеры, сеттеры, equals, hashCode, toString)
- **@Builder** - для создания объектов с паттерном Builder
- **@RequiredArgsConstructor** - для конструкторов с final полями
- **@Slf4j** - для логирования вместо ручного создания логгеров
- **@Value** - для immutable объектов
- **@NoArgsConstructor** / **@AllArgsConstructor** - для конструкторов

### Тестирование (ОБЯЗАТЕЛЬНО)
- **Покрытие:** минимум 80% unit тестов для бизнес-логики
- **Интеграционные тесты:** для всех экспортеров и MCP Server функциональности
- **Naming convention:** `shouldReturnExpectedResult_WhenGivenValidInput()`
- **AAA pattern:** Arrange, Act, Assert
- **Обязательные тесты для:**
  - Всех публичных методов сервисов
  - Всех экспортеров
  - CLI команд
  - MCP Server endpoints

### Документирование (ОБЯЗАТЕЛЬНО)
- **JavaDoc:** для всех публичных классов и методов
- **README.md:** актуальная документация по использованию
- **API документация:** для MCP Server endpoints
- **Архитектурные решения:** в ADR формате
- **Примеры использования:** в документации

## Архитектурные решения

### Паттерн Command (SOLID: SRP, OCP)
- Использование picocli для создания CLI команд
- `MainCommand` как корневая команда с подкомандами
- `McpServerCommand` для MCP Server функциональности
- `PlatformContext` как основная рабочая команда

### Паттерн Strategy для экспорта (SOLID: OCP, DIP)
- Интерфейс `Exporter` определяет контракт экспорта
- Конкретные реализации: `JsonExporter`, `XmlExporter`, `MarkdownExporter`, `ContextExporter`
- Выбор экспортера на основе параметра `--format`
- Базовая логика в `BaseExporterLogic` (SOLID: DRY)

### Data Transfer Objects (DTO) - с Lombok
- `@Data` для всех DTO классов
- `PlatformTypeDefinition` - определение типа платформы
- `MethodDefinition` - определение метода
- `PropertyDefinition` - определение свойства
- `ParameterDefinition` - определение параметра
- `Signature` - сигнатура метода
- `SearchResult` / `SearchResultType` - для MCP поиска

### Сервисная архитектура (SOLID: SRP, DIP)
- `PlatformContextService` - основной сервис работы с контекстом
- `PlatformApiSearchService` - сервис поиска API
- `MarkdownFormatterService` - форматирование Markdown
- `PlatformContextLoader` - загрузка контекста платформы

## Интеграция с bsl-context
- Зависимость от модуля `bsl-context`
- Использование `PlatformContextGrabber` для парсинга файлов .hbk
- Получение данных через `Provider` API

## MCP Server интеграция
- Spring AI MCP Server для поддержки Model Context Protocol
- Endpoints для поиска и получения контекста платформы
- Интеграция с IDE через MCP протокол

## Файловая структура
```
src/main/java/ru/alkoleft/context/platform/
├── Main.java                    # Точка входа
├── commands/                    # CLI команды
│   ├── MainCommand.java
│   └── PlatformContext.java
├── dto/                         # Data Transfer Objects
│   ├── BaseTypeDefinition.java
│   ├── MethodDefinition.java
│   ├── ParameterDefinition.java
│   └── ...
└── exporter/                    # Экспортеры
    ├── Exporter.java           # Интерфейс
    ├── BaseExporterLogic.java  # Базовая логика
    ├── JsonExporter.java
    ├── MarkdownExporter.java
    └── XmlExporter.java
```

## Конфигурация логирования
- Конфигурация в `logback.xml`
- Логирование основных операций экспорта
- Структурированные логи для отслеживания процесса

## Система сборки
- Gradle с поддержкой Git версионирования
- Поддержка создания fat JAR через `bootJar`
- Публикация в GitHub Packages
- Lombok для генерации boilerplate кода 