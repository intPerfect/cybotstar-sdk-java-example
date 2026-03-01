package com.brgroup.cybotstar.javafx.ui;

import javafx.scene.layout.VBox;

/**
 * UI适配器接口
 * 定义UI更新方法，用于服务层与UI层的解耦
 */
public interface UiAdapter {

    void addMessage(String role, String content, boolean isStreaming);

    void addMessageSync(String role, String content, boolean isStreaming);

    VBox addAssistantMessagePlaceholder();

    void updateMessageContentSync(VBox bubble, String content, boolean isStreaming);

    void setStreaming(boolean streaming);

    VBox getMessagesContainer();
}
