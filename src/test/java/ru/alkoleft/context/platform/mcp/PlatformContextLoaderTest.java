package ru.alkoleft.context.platform.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты для {@link PlatformContextLoader}
 */
@DisplayName("PlatformContextLoader")
class PlatformContextLoaderTest {

    private PlatformContextLoader platformContextLoader;

    @TempDir
    private Path tempDirectory;

    private Path platformPath;
    private Path contextFile;

    @BeforeEach
    void setUp() throws IOException {
        platformContextLoader = new PlatformContextLoader();
        platformPath = tempDirectory.resolve("platform");
        Files.createDirectories(platformPath);
        
        contextFile = platformPath.resolve("shcntx_ru.hbk");
    }

    @Test
    @DisplayName("должен выбросить FileNotFoundException если файл контекста не найден")
    void shouldThrowFileNotFoundExceptionWhenContextFileNotFound() {
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("Не удалось найти файл shcntx_ru.hbk в каталоге")
            .hasMessageContaining(platformPath.toString());
    }

    @Test
    @DisplayName("должен найти файл контекста в основном каталоге")
    void shouldFindContextFileInMainDirectory() throws IOException {
        // given
        Files.createFile(contextFile);
        
        // when & then
        // Поскольку файл существует, должна попытаться создать PlatformContextGrabber
        // что приведет к исключению из-за неправильного формата файла, но это ожидаемо
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен найти файл контекста в подкаталоге")
    void shouldFindContextFileInSubdirectory() throws IOException {
        // given
        Path subDirectory = platformPath.resolve("help").resolve("system");
        Files.createDirectories(subDirectory);
        Path contextFileInSubdir = subDirectory.resolve("shcntx_ru.hbk");
        Files.createFile(contextFileInSubdir);
        
        // when & then
        // Поскольку файл существует, должна попытаться создать PlatformContextGrabber
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен корректно обрабатывать пустой каталог")
    void shouldHandleEmptyDirectory() {
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("Не удалось найти файл shcntx_ru.hbk");
    }

    @Test
    @DisplayName("должен найти файл среди нескольких файлов")
    void shouldFindContextFileAmongMultipleFiles() throws IOException {
        // given
        Files.createFile(platformPath.resolve("other_file.txt"));
        Files.createFile(platformPath.resolve("another_file.dat"));
        Files.createFile(contextFile); // целевой файл
        Files.createFile(platformPath.resolve("readme.md"));
        
        // when & then
        // Поскольку целевой файл существует среди других, не должно быть FileNotFoundException
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен найти только файл с правильным именем")
    void shouldFindOnlyFileWithCorrectName() throws IOException {
        // given
        Files.createFile(platformPath.resolve("shcntx_en.hbk")); // неправильное имя
        Files.createFile(platformPath.resolve("other_shcntx_ru.hbk")); // неправильное имя
        Files.createFile(platformPath.resolve("shcntx_ru.txt")); // неправильное расширение
        
        // when & then
        // Ни один из файлов не соответствует точному паттерну "shcntx_ru.hbk"
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("Не удалось найти файл shcntx_ru.hbk");
    }

    @Test
    @DisplayName("должен обрабатывать каталоги с глубокой вложенностью")
    void shouldHandleDeepDirectoryStructure() throws IOException {
        // given
        Path deepPath = platformPath.resolve("level1")
                                  .resolve("level2")
                                  .resolve("level3")
                                  .resolve("level4");
        Files.createDirectories(deepPath);
        Path contextFileDeep = deepPath.resolve("shcntx_ru.hbk");
        Files.createFile(contextFileDeep);
        
        // when & then
        // Файл должен быть найден даже в глубоко вложенной структуре
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("должен обрабатывать исключения при обходе файловой системы")
    void shouldHandleFileSystemExceptions() throws IOException {
        // given
        // Создаем файл и делаем его нечитаемым
        Files.createFile(contextFile);
        Path unreadableDir = platformPath.resolve("unreadable");
        Files.createDirectories(unreadableDir);
        
        // Попытка сделать каталог нечитаемым (работает не на всех системах)
        unreadableDir.toFile().setReadable(false);
        
        try {
            // when & then
            // Даже если есть проблемы с некоторыми каталогами, 
            // поиск должен найти доступный файл
            assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
                .isNotInstanceOf(FileNotFoundException.class);
        } finally {
            // Восстанавливаем права доступа для очистки
            unreadableDir.toFile().setReadable(true);
        }
    }

    @Test
    @DisplayName("должен обрабатывать несуществующий путь")
    void shouldHandleNonExistentPath() {
        // given
        Path nonExistentPath = tempDirectory.resolve("non-existent");
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(nonExistentPath))
            .isInstanceOf(Exception.class); // Может быть различные типы исключений
    }

    @Test
    @DisplayName("должен обрабатывать путь к файлу вместо каталога")
    void shouldHandleFileInsteadOfDirectory() throws IOException {
        // given
        Path filePath = tempDirectory.resolve("some-file.txt");
        Files.createFile(filePath);
        
        // when & then
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(filePath))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("должен найти первый подходящий файл если есть несколько")
    void shouldFindFirstMatchingFileWhenMultipleExist() throws IOException {
        // given
        // Создаем несколько файлов с одинаковым именем в разных каталогах
        Path dir1 = platformPath.resolve("dir1");
        Path dir2 = platformPath.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        
        Files.createFile(dir1.resolve("shcntx_ru.hbk"));
        Files.createFile(dir2.resolve("shcntx_ru.hbk"));
        
        // when & then
        // Должен найти хотя бы один файл (не важно какой именно)
        assertThatThrownBy(() -> platformContextLoader.loadPlatformContext(platformPath))
            .isNotInstanceOf(FileNotFoundException.class);
    }
} 