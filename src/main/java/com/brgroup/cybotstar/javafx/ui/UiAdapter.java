package com.brgroup.cybotstar.javafx.ui;

import javafx.scene.layout.VBox;

/**
 * UI适配器接口
 * 定义UI更新方法，用于服务层与UI层的解耦
 * 服务层通过此接口更新UI，不直接依赖具体的UI实现
 *
 * @author zhiyuan.xi
 */
public interface UiAdapter {

    /**
     * 添加消息到界面（异步版本，可在任何线程调用）
     *
     * @param role 消息角色（user/assistant/system）
     * @param content 消息内容
     * @param isStreaming 是否正在流式输出
     */
    void addMessage(String role, String content, boolean isStreaming);

    /**
     * 添加消息到界面（同步版本，必须在JavaFX线程中调用）
     *
     * @param role 消息角色（user/assistant/system）
     * @param content 消息内容
     * @param isStreaming 是否正在流式输出
     */
    void addMessageSync(String role, String content, boolean isStreaming);

    /**
     * 添加AI消息占位（用于流式输出）
     * 注意：此方法必须在JavaFX线程中调用
     *
     * @return 返回消息气泡的引用，用于后续更新内容
     */
    VBox addAssistantMessagePlaceholder();

    /**
     * 更新消息气泡的内容（同步版本，必须在JavaFX线程中调用）
     *
     * @param bubble 消息气泡
     * @param content 消息内容
     * @param isStreaming 是否正在流式输出
     */
    void updateMessageContentSync(VBox bubble, String content, boolean isStreaming);

    /**
     * 更新消息气泡的内容（异步版本，可在任何线程调用）
     *
     * @param bubble 消息气泡
     * @param content 消息内容
     * @param isStreaming 是否正在流式输出
     */
    void updateMessageContent(VBox bubble, String content, boolean isStreaming);

    /**
     * 设置流式输出状态
     *
     * @param streaming 是否正在流式输出
     */
    void setStreaming(boolean streaming);

    /**
     * 获取消息容器（用于移除消息等操作）
     *
     * @return 消息容器
     */
    VBox getMessagesContainer();
}

