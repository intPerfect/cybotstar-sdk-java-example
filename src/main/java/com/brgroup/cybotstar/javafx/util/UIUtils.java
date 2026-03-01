package com.brgroup.cybotstar.javafx.util;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import com.brgroup.cybotstar.javafx.ChatConstants;

/**
 * UI工具类
 * 提供通用的UI操作方法
 *
 * @author zhiyuan.xi
 */
public final class UIUtils {

    private UIUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 在JavaFX应用线程中执行操作
     * 如果当前已在JavaFX线程中，直接执行；否则使用Platform.runLater
     *
     * @param action 要执行的操作
     */
    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * 滚动ScrollPane到底部
     * 使用延迟确保内容已经完全渲染和布局
     *
     * @param scrollPane 要滚动的ScrollPane
     */
    public static void scrollToBottom(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }

        runOnFxThread(() -> {
            // 如果已绑定，先解绑
            if (scrollPane.vvalueProperty().isBound()) {
                scrollPane.vvalueProperty().unbind();
            }

            // 延迟一小段时间，确保内容已经完全渲染和布局
            PauseTransition pause = new PauseTransition(Duration.millis(ChatConstants.SCROLL_DELAY_MS));
            pause.setOnFinished(e -> {
                if (scrollPane != null) {
                    scrollPane.setVvalue(1.0);
                    // 强制布局更新
                    scrollPane.requestLayout();
                }
            });
            pause.play();
        });
    }
}

