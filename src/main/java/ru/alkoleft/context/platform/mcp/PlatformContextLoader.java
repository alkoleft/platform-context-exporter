package ru.alkoleft.context.platform.mcp;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Компонент для загрузки контекста платформы 1С из файлов справочной системы
 */
@Slf4j
@Component
public class PlatformContextLoader {

    private static final String CONTEXT_FILE_NAME = "shcntx_ru.hbk";

    /**
     * Загружает контекст платформы из указанного пути
     *
     * @param platformPath путь к каталогу с файлами платформы
     * @return провайдер контекста платформы
     * @throws Exception если не удалось загрузить контекст
     */
    public ContextProvider loadPlatformContext(Path platformPath) throws Exception {
        log.info("Загрузка контекста платформы из {}", platformPath);

        Path syntaxContextFile = findContextFile(platformPath);

        if (syntaxContextFile == null) {
            throw new FileNotFoundException(
                    String.format("Не удалось найти файл %s в каталоге %s", CONTEXT_FILE_NAME, platformPath)
            );
        }

        log.info("Найден файл контекста: {}", syntaxContextFile);

        // Создаем временный каталог для распаковки
        var tmpDir = Files.createTempDirectory("platform-context");

        try {
            var grabber = new PlatformContextGrabber(syntaxContextFile, tmpDir);
            grabber.parse();

            var provider = grabber.getProvider();
            log.info("Контекст платформы успешно загружен");

            return provider;
        } finally {
            // Очищаем временный каталог
            cleanupTempDirectory(tmpDir);
        }
    }

    /**
     * Ищет файл контекста в указанном каталоге
     */
    private Path findContextFile(Path path) throws Exception {
        try (var walk = Files.walk(path)) {
            return walk.filter(p -> p.toFile().isFile())
                    .filter(p -> p.getFileName().toString().equals(CONTEXT_FILE_NAME))
                    .findAny()
                    .orElse(null);
        }
    }

    /**
     * Очищает временный каталог
     */
    private void cleanupTempDirectory(Path tmpDir) {
        try {
            if (Files.exists(tmpDir)) {
                Files.walk(tmpDir)
                        .sorted((a, b) -> b.compareTo(a)) // Удаляем файлы перед каталогами
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception e) {
                                log.warn("Не удалось удалить временный файл: {}", path, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("Ошибка при очистке временного каталога: {}", tmpDir, e);
        }
    }
} 