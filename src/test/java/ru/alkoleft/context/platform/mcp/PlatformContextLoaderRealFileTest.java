package ru.alkoleft.context.platform.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Timeout;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты для PlatformContextLoader с реальными файлами платформы 1С
 * Тесты выполняются только если каталог платформы доступен
 */
@DisplayName("PlatformContextLoader - Тесты с реальными файлами платформы")
class PlatformContextLoaderRealFileTest {

    private static final String PLATFORM_PATH = "/opt/1cv8/x86_64/8.3.27.1508/";
    private static final String CONTEXT_FILE_NAME = "shcntx_ru.hbk";

    private PlatformContextLoader platformContextLoader;

    @BeforeEach
    void setUp() {
        platformContextLoader = new PlatformContextLoader();
    }

    /**
     * Проверяет доступность каталога платформы
     */
    static boolean isPlatformDirectoryAvailable() {
        return Files.exists(Paths.get(PLATFORM_PATH)) &&
               Files.isDirectory(Paths.get(PLATFORM_PATH)) &&
               Files.isReadable(Paths.get(PLATFORM_PATH));
    }

    @Test
    @EnabledIf("isPlatformDirectoryAvailable")
    @DisplayName("должен успешно загрузить контекст из реального каталога платформы")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldLoadContextFromRealPlatformDirectory() throws Exception {
        // given
        Path platformPath = Paths.get(PLATFORM_PATH);

        // when
        var result = platformContextLoader.loadPlatformContext(platformPath);

        // then
        assertThat(result).isNotNull();

        // Проверяем что контекст загружен
        var globalContext = result.getGlobalContext();
        var contexts = result.getContexts();

        assertThat(globalContext).isNotNull();
        assertThat(contexts).isNotNull();
        assertThat(contexts).isNotEmpty();

        System.out.println("Загружено контекстов: " + contexts.size());
        System.out.println("Глобальный контекст загружен: " + (globalContext != null));
    }

    @Test
    @EnabledIf("isPlatformDirectoryAvailable")
    @DisplayName("должен найти файл контекста в каталоге платформы")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldFindContextFileInPlatformDirectory() throws Exception {
        // given
        Path platformPath = Paths.get(PLATFORM_PATH);

        // when - пытаемся загрузить контекст
        // Если файл найден, исключение не должно быть выброшено
        var result = platformContextLoader.loadPlatformContext(platformPath);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("должен выбросить FileNotFoundException для несуществующего каталога")
    void shouldThrowFileNotFoundExceptionForNonExistentDirectory() {
        // given
        Path nonExistentPath = Paths.get("/non/existent/path");

        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(nonExistentPath))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("не найден");
    }

    @Test
    @EnabledIf("isPlatformDirectoryAvailable")
    @DisplayName("должен корректно обработать различные подкаталоги платформы")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldHandleVariousPlatformSubdirectories() throws Exception {
        // given
        Path platformPath = Paths.get(PLATFORM_PATH);

        // when
        var result = platformContextLoader.loadPlatformContext(platformPath);

        // then
        assertThat(result).isNotNull();

        // Проверяем что контекст содержит ожидаемую структуру
        var contexts = result.getContexts();
        assertThat(contexts).isNotNull();
        assertThat(contexts).isNotEmpty();

        // Проверяем наличие глобального контекста
        var globalContext = result.getGlobalContext();
        assertThat(globalContext)
            .withFailMessage("Глобальный контекст платформы не загружен")
            .isNotNull();
    }

    @Test
    @EnabledIf("isPlatformDirectoryAvailable")
    @DisplayName("должен загружать контекст с разумным временем выполнения")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void shouldLoadContextWithReasonablePerformance() throws Exception {
        // given
        Path platformPath = Paths.get(PLATFORM_PATH);

        // when
        long startTime = System.currentTimeMillis();
        var result = platformContextLoader.loadPlatformContext(platformPath);
        long endTime = System.currentTimeMillis();

        // then
        assertThat(result).isNotNull();

        long executionTime = endTime - startTime;
        System.out.println("Время загрузки контекста: " + executionTime + " мс");

        // Проверяем что загрузка заняла разумное время (менее 15 секунд)
        assertThat(executionTime)
            .withFailMessage("Загрузка контекста заняла слишком много времени: " + executionTime + " мс")
            .isLessThan(15000);
    }

    @Test
    @EnabledIf("isPlatformDirectoryAvailable")
    @DisplayName("должен правильно очищать временные файлы после загрузки")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void shouldCleanupTemporaryFilesAfterLoading() throws Exception {
        // given
        Path platformPath = Paths.get(PLATFORM_PATH);

        // Получаем количество временных каталогов до выполнения
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        long tempDirsBefore = Files.list(tempDir)
            .filter(Files::isDirectory)
            .filter(p -> p.getFileName().toString().startsWith("platform-context"))
            .count();

        // when
        var result = platformContextLoader.loadPlatformContext(platformPath);

        // Небольшая задержка для завершения очистки
        Thread.sleep(100);

        // then
        assertThat(result).isNotNull();

        // Проверяем что временные каталоги очищены
        long tempDirsAfter = Files.list(tempDir)
            .filter(Files::isDirectory)
            .filter(p -> p.getFileName().toString().startsWith("platform-context"))
            .count();

        assertThat(tempDirsAfter)
            .withFailMessage("Временные каталоги не были очищены после загрузки")
            .isEqualTo(tempDirsBefore);
    }

    @Test
    @EnabledIf("isPlatformDirectoryAvailable")
    @DisplayName("должен загружать контекст повторно без ошибок")
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void shouldLoadContextMultipleTimesWithoutErrors() throws Exception {
        // given
        Path platformPath = Paths.get(PLATFORM_PATH);

        // when & then - выполняем загрузку несколько раз
        for (int i = 0; i < 3; i++) {
            var result = platformContextLoader.loadPlatformContext(platformPath);

            assertThat(result).isNotNull();
            assertThat(result.getContexts()).isNotNull();
            assertThat(result.getContexts()).isNotEmpty();

            System.out.println("Итерация " + (i + 1) + ": загружено " + result.getContexts().size() + " контекстов");
        }
    }
}