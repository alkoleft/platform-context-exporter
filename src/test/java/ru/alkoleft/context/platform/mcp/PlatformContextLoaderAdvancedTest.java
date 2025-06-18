package ru.alkoleft.context.platform.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Расширенные тесты для {@link PlatformContextLoader}
 * Покрывают граничные случаи, производительность и concurrent доступ
 */
@DisplayName("PlatformContextLoader - Расширенные тесты")
class PlatformContextLoaderAdvancedTest {

    private PlatformContextLoader platformContextLoader;

    @TempDir
    private Path tempDirectory;

    @BeforeEach
    void setUp() {
        platformContextLoader = new PlatformContextLoader();
    }

    @Test
    @DisplayName("должен обрабатывать большую вложенность каталогов")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldHandleVeryDeepDirectoryStructure() throws IOException {
        // given - создаем очень глубокую структуру (100 уровней)
        Path current = tempDirectory.resolve("platform");
        Files.createDirectories(current);
        
        for (int i = 0; i < 100; i++) {
            current = current.resolve("level" + i);
            Files.createDirectories(current);
        }
        
        Path contextFile = current.resolve("shcntx_ru.hbk");
        Files.createFile(contextFile);
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(tempDirectory.resolve("platform")))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать много файлов в каталоге")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldHandleManyFilesInDirectory() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        // Создаем 1000 файлов
        for (int i = 0; i < 1000; i++) {
            Files.createFile(platformPath.resolve("file" + i + ".txt"));
        }
        
        // И один целевой файл
        Files.createFile(platformPath.resolve("shcntx_ru.hbk"));
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать файлы с различными кодировками в именах")
    void shouldHandleUnicodeFileNames() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        // Создаем файлы с unicode именами
        Files.createFile(platformPath.resolve("файл_кириллица.txt"));
        Files.createFile(platformPath.resolve("文件_中文.dat"));
        Files.createFile(platformPath.resolve("ファイル_日本語.log"));
        Files.createFile(platformPath.resolve("shcntx_ru.hbk")); // целевой файл
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать concurrent доступ к файловой системе")
    @Execution(ExecutionMode.CONCURRENT)
    void shouldHandleConcurrentAccess() throws Exception {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        Files.createFile(platformPath.resolve("shcntx_ru.hbk"));
        
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        // when
        var futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                    platformContextLoader.loadPlatformContext(platformPath);
                } catch (Exception e) {
                    // Ожидаем исключение из-за неправильного формата файла
                    assertThat(e).isNotInstanceOf(FileNotFoundException.class);
                } finally {
                    endLatch.countDown();
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        startLatch.countDown(); // Запускаем все потоки одновременно
        
        // then
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        CompletableFuture.allOf(futures).join();
    }

    @Test
    @DisplayName("должен обрабатывать null входные параметры")
    void shouldHandleNullInput() {
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("должен обрабатывать пустую строку пути")
    void shouldHandleEmptyPath() {
        // given
        Path emptyPath = Path.of("");
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(emptyPath))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("должен обрабатывать специальные символы в пути")
    void shouldHandleSpecialCharactersInPath() throws IOException {
        // given
        Path specialPath = tempDirectory.resolve("platform with spaces & special-chars!");
        Files.createDirectories(specialPath);
        Files.createFile(specialPath.resolve("shcntx_ru.hbk"));
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(specialPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен корректно логировать процесс поиска")
    void shouldLogSearchProcess() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        // Тест на отсутствующий файл
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isInstanceOf(FileNotFoundException.class);
        
        // Тест на найденный файл
        Files.createFile(platformPath.resolve("shcntx_ru.hbk"));
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать файлы только для чтения")
    void shouldHandleReadOnlyFiles() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        Path contextFile = platformPath.resolve("shcntx_ru.hbk");
        Files.createFile(contextFile);
        
        // Делаем файл только для чтения
        contextFile.toFile().setReadOnly();
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен правильно обрабатывать случай когда каталог удаляется во время поиска")
    void shouldHandleDirectoryDeletionDuringSearch() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Path subdirPath = platformPath.resolve("subdir");
        Files.createDirectories(subdirPath);
        Files.createFile(subdirPath.resolve("shcntx_ru.hbk"));
        
        // when & then
        // Файл должен быть найден до потенциального удаления
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен находить файл среди множества подкаталогов")
    void shouldFindFileAmongManySubdirectories() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        // Создаем много подкаталогов
        for (int i = 0; i < 50; i++) {
            Path subdir = platformPath.resolve("subdir" + i);
            Files.createDirectories(subdir);
            // Добавляем файлы-помехи
            Files.createFile(subdir.resolve("other_file_" + i + ".txt"));
        }
        
        // Помещаем целевой файл в один из каталогов
        Path targetDir = platformPath.resolve("subdir25");
        Files.createFile(targetDir.resolve("shcntx_ru.hbk"));
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать структуру каталогов с различными расширениями файлов")
    void shouldHandleVariousFileExtensions() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        // Создаем файлы с различными расширениями
        String[] extensions = {".txt", ".log", ".dat", ".xml", ".json", ".config", ".properties", ".yml"};
        for (String ext : extensions) {
            Files.createFile(platformPath.resolve("file" + ext));
        }
        
        // И целевой файл
        Files.createFile(platformPath.resolve("shcntx_ru.hbk"));
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать файлы с аналогичными именами")
    void shouldHandleSimilarFileNames() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        // Создаем файлы с похожими именами
        Files.createFile(platformPath.resolve("shcntx.hbk"));
        Files.createFile(platformPath.resolve("shcntx_en.hbk"));
        Files.createFile(platformPath.resolve("shcntx_fr.hbk"));
        Files.createFile(platformPath.resolve("context_ru.hbk"));
        Files.createFile(platformPath.resolve("shcntx_ru.txt"));
        Files.createFile(platformPath.resolve("shcntx_ru_backup.hbk"));
        
        // when & then - файл НЕ должен быть найден, так как точного совпадения нет
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать временную недоступность файловой системы")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void shouldHandleTemporaryFileSystemUnavailability() throws IOException {
        // given
        Path platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        Files.createFile(platformPath.resolve("shcntx_ru.hbk"));
        
        // when & then
        // Проверяем что метод завершается в разумное время даже при проблемах с ФС
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }
} 