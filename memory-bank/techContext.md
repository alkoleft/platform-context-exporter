# Технический контекст bsl-context-exporter

## Технологический стек
- **Язык:** Java 17
- **Фреймворк:** Spring Boot 3.4.1
- **Сборка:** Gradle 8.x + Kotlin DSL
- **CLI:** picocli 4.7.5
- **Сериализация:** Jackson 2.15.2
- **Логирование:** Logback 1.4.14 + SLF4J
- **Тестирование:** JUnit 5 + AssertJ

## Архитектурные решения

### Паттерн Command
- Использование picocli для создания CLI команд
- `MainCommand` как корневая команда с подкомандами
- `PlatformContext` как основная рабочая команда

### Паттерн Strategy для экспорта
- Интерфейс `Exporter` определяет контракт экспорта
- Конкретные реализации: `JsonExporter`, `XmlExporter`, `MarkdownExporter`, `ContextExporter`
- Выбор экспортера на основе параметра `--format`

### Data Transfer Objects (DTO)
- `PlatformTypeDefinition` - определение типа платформы
- `MethodDefinition` - определение метода
- `PropertyDefinition` - определение свойства
- `ParameterDefinition` - определение параметра
- `Signature` - сигнатура метода

## Интеграция с bsl-context
- Зависимость от модуля `bsl-context`
- Использование `PlatformContextGrabber` для парсинга файлов .hbk
- Получение данных через `Provider` API

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