# platform-context-exporter

Консольное приложение для экспорта информации о контексте платформы 1С:Предприятие (BSL) из файла справки `shcntx_ru.hbk` в форматы JSON, XML, Markdown и `context` для использования с LLM.

> [!WARNING]  
> Функциональность MCP сервера **переехала** в отдельный проект [mcp-bsl-context](https://github.com/alkoleft/mcp-bsl-context)

## Обзор

Приложение `platform-context-exporter` предназначено для разработчиков, работающих с языком BSL (Business Specific Language) и экосистемой 1С:Предприятие. Оно предоставляет два основных режима работы:

### 🔄 Экспорт контекста платформы
Извлечение структурированной информации о глобальных свойствах, глобальных методах и типах платформы из стандартного файла справки `shcntx_ru.hbk`. Эта информация экспортируется в файлы различных форматов для использования в:

- Предоставлении контекста для больших языковых моделей (LLM) с помощью формата `context`
- Автодополнении кода в IDE и редакторах
- Анализе структуры платформы
- Интеграции с другими инструментами разработки

### 🤖 MCP сервер для AI ассистентов
Интерактивный сервер, предоставляющий стандартизированный доступ к API платформы 1С Предприятие через протокол MCP (Model Context Protocol). Позволяет AI ассистентам выполнять поиск по документации и получать детальную информацию об элементах API в реальном времени.

Проект использует Picocli для создания удобного CLI-интерфейса и Spring Boot для MCP сервера.

## Требования

### Системные требования
- **Java**: версия 17 или выше
- **Операционная система**: Linux, macOS, Windows
- **Память**: минимум 512 МБ RAM для работы приложения
- **Место на диске**: 100 МБ для установки приложения

### Для экспорта контекста платформы
- **Платформа 1С Предприятие**: любая версия (для доступа к файлу `shcntx_ru.hbk`)
- **Файл справки**: `shcntx_ru.hbk` должен быть доступен в каталоге установки платформы

### Для MCP сервера
- **Платформа 1С Предприятие**: версия 8.3.20 или выше (рекомендуется)
- **MCP клиент**: Claude Desktop, Cursor IDE или другой совместимый MCP клиент
- **Сетевое подключение**: для загрузки зависимостей при первом запуске

### Зависимости времени выполнения
Все необходимые зависимости включены в исполняемый JAR-файл:
- Spring Boot 3.5.0
- Spring AI 1.0.0
- Jackson 2.15.2
- BSL Context Parser
- Picocli 4.7.5

## Сборка

Проект собирается с помощью Gradle. Для сборки выполните следующую команду в корневой директории проекта:

```bash
./gradlew build
```

После успешной сборки исполняемый JAR-файл будет находиться в директории `build/libs/`.

## Использование

Приложение предоставляет два основных режима работы:

### Экспорт контекста платформы (команда `platform`)

```bash
java -jar platform-context-exporter-<версия>.jar platform <путь_к_shcntx_ru_hbk_dir> <путь_к_выходной_директории>
```

**Аргументы:**

- `platform` - основная команда для запуска экспорта
- `<путь_к_shcntx_ru_hbk_dir>` (обязательный) - путь к директории, в которой находится файл справки `shcntx_ru.hbk`. Приложение будет искать этот файл внутри указанной директории (включая поддиректории)
- `<путь_к_выходной_директории>` (обязательный) - путь к директории, в которую будут сохранены сгенерированные файлы. Если директория не существует, она будет создана

**Опции:**

- `--format <формат>` - задает формат выходных файлов. Возможные значения:
  - `json` (по умолчанию) - экспорт в файлы JSON
  - `xml` - экспорт в файлы XML
  - `markdown` - экспорт в файлы формата Markdown
  - `context` - экспорт в файлы формата `context` для LLM

**Пример:**

```bash
java -jar platform-context-exporter-0.1.0.jar platform /path/to/onec/help/ /output/context/ --format context
```

### MCP сервер для AI ассистентов (команда `mcp-server`)

Приложение также предоставляет MCP (Model Context Protocol) сервер для интеграции с AI ассистентами, такими как Claude Desktop.

```bash
java -jar platform-context-exporter-<версия>.jar mcp-server --platform-path <путь_к_платформе_1С>
```

**Параметры:**

- `mcp-server` - команда для запуска MCP сервера
- `--platform-path` (обязательный) - путь к каталогу установки 1С Предприятия
- `--verbose` - включить отладочное логирование

**Пример:**

```bash
java -jar platform-context-exporter-0.1.0.jar mcp-server --platform-path "/opt/1cv8/x86_64/8.3.25.1257" --verbose
```

**Возможности MCP сервера:**

- **Поиск по API платформы** - нечеткий поиск по глобальным методам, свойствам и типам данных
- **Детальная информация** - получение полной информации об элементах API с сигнатурами и описаниями
- **Интеграция с AI** - стандартизированный доступ для AI ассистентов через MCP протокол

Подробная документация по использованию MCP сервера доступна в [MCP_SERVER_USAGE.md](MCP_SERVER_USAGE.md).

## Форматы вывода

Подробное описание форматов вывода и структуры генерируемых файлов вынесено в [отдельную документацию](./documentation/formats.md).

## Зависимости

Основные зависимости проекта:

- Spring Boot
- Picocli (для CLI)
- Jackson (для работы с JSON)
- [bsl-context](https://github.com/1c-syntax/bsl-context) (от [1c-syntax](https://github.com/1c-syntax/))
- [bsl-help-toc-parser](https://github.com/1c-syntax/bsl-help-toc-parser) (от [1c-syntax](https://github.com/1c-syntax/)) для парсинга `shcntx_ru.hbk`
- Lombok

## Лицензия

Этот проект распространяется под лицензией MIT. Подробную информацию смотрите в файле [LICENSE](LICENSE).
