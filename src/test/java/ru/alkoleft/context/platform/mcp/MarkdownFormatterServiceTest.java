package ru.alkoleft.context.platform.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.alkoleft.context.platform.dto.*;

import java.util.List;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Тесты MarkdownFormatterService")
class MarkdownFormatterServiceTest {

    private final MarkdownFormatterService service = new MarkdownFormatterService();

    @Nested
    @DisplayName("formatSearchResults")
    class FormatSearchResults {
        @Test
        @DisplayName("Пустой результат поиска")
        void emptyResults() {
            String query = "тест";
            String result = service.formatSearchResults(query, Collections.emptyList());
            assertThat(result)
                    .contains("Ничего не найдено по запросу:")
                    .contains(query);
        }

        @Test
        @DisplayName("Один результат: метод")
        void singleMethodResult() {
            MethodDefinition method = new MethodDefinition(
                    "Найти",
                    "Описание метода Найти",
                    List.of(new Signature("param", "desc", List.of(new ParameterDefinition(true, "парам", "описание", "Строка")))),
                    "Число"
            );
            String result = service.formatSearchResults("Найти", List.of(method));
            assertThat(result)
                    .contains("Результаты поиска")
                    .contains("Найти")
                    .contains("Описание метода Найти");
        }

        @Test
        @DisplayName("Несколько результатов: метод и свойство")
        void multipleResults() {
            MethodDefinition method = new MethodDefinition(
                    "Добавить",
                    "Описание метода Добавить",
                    List.of(),
                    "Булево"
            );
            PropertyDefinition property = new PropertyDefinition(
                    "Количество", null, "Описание свойства Количество", true, "Число"
            );
            String result = service.formatSearchResults("Добавить", List.of(method, property));
            assertThat(result)
                    .contains("Добавить")
                    .contains("Количество")
                    .contains("Описание метода Добавить")
                    .contains("Описание свойства Количество");
        }
    }

    @Nested
    @DisplayName("formatDetailedInfo")
    class FormatDetailedInfo {
        @Test
        @DisplayName("Детальная информация о методе")
        void methodDetail() {
            MethodDefinition method = new MethodDefinition(
                    "Удалить",
                    "Удаляет элемент",
                    List.of(new Signature("id", "идентификатор", List.of(new ParameterDefinition(true, "id", "ид", "Число")))),
                    "Булево"
            );
            String result = service.formatDetailedInfo(method);
            assertThat(result)
                    .contains("Удалить")
                    .contains("Удаляет элемент")
                    .contains("Булево");
        }

        @Test
        @DisplayName("Детальная информация о свойстве")
        void propertyDetail() {
            PropertyDefinition property = new PropertyDefinition(
                    "Имя", null, "Имя пользователя", false, "Строка"
            );
            String result = service.formatDetailedInfo(property);
            assertThat(result)
                    .contains("Имя пользователя")
                    .contains("Строка");
        }

        @Test
        @DisplayName("Детальная информация о типе")
        void typeDetail() {
            PlatformTypeDefinition type = new PlatformTypeDefinition(
                    "Пользователь",
                    "Тип пользователя",
                    List.of(),
                    List.of(),
                    List.of()
            );
            String result = service.formatDetailedInfo(type);
            assertThat(result)
                    .contains("Пользователь")
                    .contains("Тип пользователя");
        }
    }

    @Test
    @DisplayName("formatConstructors: Форматирование конструкторов типа")
    void formatConstructors() {
        ISignature constructor = new Signature(
                "Новый",
                "Создает объект",
                List.of(
                        new ParameterDefinition(true, "имя", "Имя объекта", "Строка"),
                        new ParameterDefinition(false, "id", "Идентификатор", "Число")
                )
        );
        String result = service.formatConstructors(List.of(constructor), "Пользователь");
        assertThat(result)
                .contains("Конструкторы типа Пользователь")
                .contains("имя: Строка")
                .contains("id: Число")
                .contains("Создает объект");
    }

    @Test
    @DisplayName("formatTypeMembers: Форматирование членов типа")
    void formatTypeMembers() {
        MethodDefinition method = new MethodDefinition(
                "Сохранить", "Сохраняет объект", List.of(), "Булево"
        );
        PropertyDefinition property = new PropertyDefinition(
                "Имя", null, "Имя объекта", false, "Строка"
        );
        ISignature constructor = new Signature(
                "Новый", "", List.of()
        );
        PlatformTypeDefinition type = new PlatformTypeDefinition(
                "Документ",
                "Тип документа",
                List.of(method),
                List.of(property),
                List.of(constructor)
        );
        String result = service.formatTypeMembers(type);
        assertThat(result)
                .contains("Члены типа Документ")
                .contains("Сохранить")
                .contains("Имя")
                .contains("Конструкторы (1)");
    }
} 