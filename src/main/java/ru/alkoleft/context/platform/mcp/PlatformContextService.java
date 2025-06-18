package ru.alkoleft.context.platform.mcp;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Сервис для работы с контекстом платформы 1С
 * Предоставляет кэшированный доступ к данным платформы
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformContextService {

  private final PlatformContextLoader contextLoader;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  @Value("${platform.context.path:}")
  private String platformContextPath;

  private ContextProvider cachedProvider;
  private volatile boolean isLoaded = false;

  /**
   * Получает провайдер контекста платформы.
   * При первом обращении загружает данные из файлов платформы.
   *
   * @return провайдер контекста платформы
   * @throws RuntimeException если не удалось загрузить контекст
   */
  public ContextProvider getContextProvider() {
    // Используем read lock для проверки
    lock.readLock().lock();
    try {
      if (isLoaded && cachedProvider != null) {
        return cachedProvider;
      }
    } finally {
      lock.readLock().unlock();
    }

    // Переходим на write lock для загрузки
    lock.writeLock().lock();
    try {
      // Двойная проверка
      if (isLoaded && cachedProvider != null) {
        return cachedProvider;
      }

      loadPlatformContext();
      return cachedProvider;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Принудительно перезагружает контекст платформы
   */
  public void reloadContext() {
    lock.writeLock().lock();
    try {
      log.info("Принудительная перезагрузка контекста платформы");
      isLoaded = false;
      cachedProvider = null;
      loadPlatformContext();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Устанавливает путь к файлам платформы
   */
  public void setPlatformContextPath(String path) {
    lock.writeLock().lock();
    try {
      if (!path.equals(this.platformContextPath)) {
        this.platformContextPath = path;
        isLoaded = false;
        cachedProvider = null;
        log.info("Изменен путь к контексту платформы: {}", path);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Проверяет, загружен ли контекст
   */
  public boolean isContextLoaded() {
    lock.readLock().lock();
    try {
      return isLoaded;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Загружает контекст платформы
   */
  private void loadPlatformContext() {
    try {
      if (platformContextPath == null || platformContextPath.isEmpty()) {
        throw new IllegalStateException(
                "Путь к контексту платформы не настроен. " +
                        "Установите свойство platform.context.path или используйте setPlatformContextPath()"
        );
      }

      Path path = Paths.get(platformContextPath);
      cachedProvider = contextLoader.loadPlatformContext(path);
      isLoaded = true;

      log.info("Контекст платформы успешно загружен и кэширован");

    } catch (Exception e) {
      log.error("Ошибка при загрузке контекста платформы", e);
      throw new RuntimeException("Не удалось загрузить контекст платформы", e);
    }
  }
} 