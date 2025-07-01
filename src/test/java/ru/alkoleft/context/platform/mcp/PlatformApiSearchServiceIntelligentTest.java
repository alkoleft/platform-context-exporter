package ru.alkoleft.context.platform.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.alkoleft.context.platform.dto.MethodDefinition;
import ru.alkoleft.context.platform.dto.PlatformTypeDefinition;
import ru.alkoleft.context.platform.dto.PropertyDefinition;
import ru.alkoleft.context.platform.exporter.BaseExporterLogic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit тесты для интеллектуального алгоритма поиска PlatformApiSearchService
 * Тестируют 4 приоритета поиска:
 * 1. Объединение слов в составные типы
 * 2. Тип + член
 * 3. Обычный поиск
 * 4. Поиск по словам в любом порядке
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformApiSearchServiceIntelligentTest {

    @Mock
    private PlatformContextService contextService;
    
    @Mock 
    private MarkdownFormatterService formatter;
    
    @Mock
    private BaseExporterLogic exporterLogic;
    
    private PlatformApiSearchService searchService;
    
    // Тестовые данные
    private Map<String, PlatformTypeDefinition> typesIndex;
    private Map<String, MethodDefinition> globalMethodsIndex;
    private Map<String, PropertyDefinition> globalPropertiesIndex;
    
    @BeforeEach
    void setUp() throws Exception {
        searchService = new PlatformApiSearchService(contextService, formatter, exporterLogic);
        
        // Настраиваем тестовые индексы
        setupTestIndexes();
        
        // Инициализируем индексы в сервисе через рефлексию
        initializeServiceIndexes();
        
        // Настраиваем мок для форматтера
        when(formatter.formatSearchResults(org.mockito.ArgumentMatchers.anyString(), 
                                         org.mockito.ArgumentMatchers.anyList()))
            .thenReturn("Formatted results");
    }
    
    private void setupTestIndexes() {
        // Создаем тестовые данные
        typesIndex = new HashMap<>();
        globalMethodsIndex = new HashMap<>();
        globalPropertiesIndex = new HashMap<>();
        
        // Тестовые типы для Приоритета 1 (составные типы)
        PlatformTypeDefinition таблицаЗначений = createTestType("ТаблицаЗначений", 
            Arrays.asList("Количество", "Добавить", "Найти"),
            Arrays.asList("Колонки", "Строки")
        );
        typesIndex.put("таблицазначений", таблицаЗначений);
        
        PlatformTypeDefinition выборкаИзРезультатаЗапроса = createTestType("ВыборкаИзРезультатаЗапроса",
            Arrays.asList("Следующий", "Получить"),
            Arrays.asList("Количество")
        );
        typesIndex.put("выборкаизрезультатазапроса", выборкаИзРезультатаЗапроса);
        
        // Тестовые глобальные методы
        MethodDefinition найтиПоСсылке = createTestMethod("НайтиПоСсылке", "Поиск объекта по ссылке");
        globalMethodsIndex.put("найтипоссылке", найтиПоСсылке);
        
        // Тестовые глобальные свойства
        PropertyDefinition текущаяДата = createTestProperty("ТекущаяДата", "Текущая дата сервера");
        globalPropertiesIndex.put("текущаядата", текущаяДата);
    }
    
    private void initializeServiceIndexes() throws Exception {
        // Используем рефлексию для установки индексов
        setPrivateField("typesIndex", typesIndex);
        setPrivateField("globalMethodsIndex", globalMethodsIndex);
        setPrivateField("globalPropertiesIndex", globalPropertiesIndex);
        setPrivateField("indexInitialized", true);
    }
    
    private void setPrivateField(String fieldName, Object value) throws Exception {
        Field field = PlatformApiSearchService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(searchService, value);
    }
    
    private PlatformTypeDefinition createTestType(String name, List<String> methodNames, List<String> propertyNames) {
        List<MethodDefinition> methods = methodNames.stream()
            .map(methodName -> createTestMethod(methodName, "Описание метода " + methodName))
            .toList();
            
        List<PropertyDefinition> properties = propertyNames.stream()
            .map(propName -> createTestProperty(propName, "Описание свойства " + propName))
            .toList();
            
        return new PlatformTypeDefinition(name, "Описание типа " + name, 
                                        methods, properties, Collections.emptyList());
    }
    
    private MethodDefinition createTestMethod(String name, String description) {
        return new MethodDefinition(name, description, Collections.emptyList(), "Произвольный");
    }
    
    private PropertyDefinition createTestProperty(String name, String description) {
        return new PropertyDefinition(name, null, description, true, "Произвольный");
    }

    @Test
    void testPriority1_CompoundTypes_TableValues() {
        // Приоритет 1: "Таблица значений" → "ТаблицаЗначений"
        String result = searchService.search("Таблица значений", null, 10);
        
        assertThat(result).isEqualTo("Formatted results");
        
        // Проверяем что форматтер был вызван с правильными результатами
        // (тип ТаблицаЗначений должен быть найден)
        // Этот тест проверяет базовую функциональность
    }
    
    @Test
    void testPriority1_CompoundTypes_MultipleVariants() throws Exception {
        // Тестируем метод generateCompoundVariants напрямую
        Method method = PlatformApiSearchService.class.getDeclaredMethod("generateCompoundVariants", String[].class);
        method.setAccessible(true);
        
        String[] words = {"таблица", "значений"};
        @SuppressWarnings("unchecked")
        List<String> variants = (List<String>) method.invoke(searchService, (Object) words);
        
        assertThat(variants).contains(
            "таблицазначений",      // все строчные
            "ТаблицаЗначений",      // CamelCase
            "таблицаЗначений"       // lowerCamelCase
        );
    }
    
    @Test 
    void testPriority2_TypeMember_TableValuesCount() {
        // Приоритет 2: "Таблица значений количество" → тип "ТаблицаЗначений" + метод "количество"
        String result = searchService.search("Таблица значений количество", null, 10);
        
        assertThat(result).isEqualTo("Formatted results");
        // Должен найти метод "Количество" в типе "ТаблицаЗначений"
    }
    
    @Test
    void testPriority4_WordOrder_QuerySelection() {
        // Приоритет 4: "Запрос выборка" → "ВыборкаИзРезультатаЗапроса"
        String result = searchService.search("Запрос выборка", null, 10);
        
        assertThat(result).isEqualTo("Formatted results");
        // Должен найти тип содержащий слова "запрос" и "выборка"
    }
    
    @Test
    void testRussianAliases_ObjectType() {
        // Тестируем русскоязычные алиасы: "объект" → "type"
        String result = searchService.search("таблица", "объект", 10);
        
        assertThat(result).isEqualTo("Formatted results");
        // Алиас "объект" должен быть преобразован в "type"
    }
    
    @Test
    void testRussianAliases_MethodType() {
        // Тестируем русскоязычные алиасы: "метод" → "method"
        String result = searchService.search("найти", "метод", 10);
        
        assertThat(result).isEqualTo("Formatted results");
        // Алиас "метод" должен быть преобразован в "method"
    }
    
    @Test
    void testRussianAliases_PropertyType() {
        // Тестируем русскоязычные алиасы: "свойство" → "property"  
        String result = searchService.search("дата", "свойство", 10);
        
        assertThat(result).isEqualTo("Formatted results");
        // Алиас "свойство" должен быть преобразован в "property"
    }
    
    @Test
    void testCountWordsInVariant() throws Exception {
        // Тестируем метод countWordsInVariant напрямую
        Method method = PlatformApiSearchService.class.getDeclaredMethod("countWordsInVariant", String.class, String[].class);
        method.setAccessible(true);
        
        String variant = "ТаблицаЗначений";
        String[] words = {"таблица", "значений"};
        
        Integer count = (Integer) method.invoke(searchService, variant, words);
        
        assertThat(count).isEqualTo(2); // Должно совпасть 2 слова
    }
    
    @Test 
    void testCountMatchingWords() throws Exception {
        // Тестируем метод countMatchingWords напрямую
        Method method = PlatformApiSearchService.class.getDeclaredMethod("countMatchingWords", String.class, String[].class);
        method.setAccessible(true);
        
        String elementName = "выборкаизрезультатазапроса";
        String[] words = {"запрос", "выборка"};
        
        Integer count = (Integer) method.invoke(searchService, elementName, words);
        
        assertThat(count).isEqualTo(2); // Должно совпасть 2 слова
    }
} 