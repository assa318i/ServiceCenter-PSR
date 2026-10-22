package psr.sc;

import javax.swing.JPanel;

/**
 * Контракт своей модульной разработки. Плагин — jar в папке modules/ с манифестом:
 *   PSR-Plugin: имя.класса (реализует этот интерфейс)
 *   PSR-Title:  подпись кнопки в навигации
 */
public interface PsrPlugin {
    String title();
    JPanel createPanel();
}
