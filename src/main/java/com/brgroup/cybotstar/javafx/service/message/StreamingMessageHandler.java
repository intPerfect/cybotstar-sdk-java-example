package com.brgroup.cybotstar.javafx.service.message;

import com.brgroup.cybotstar.javafx.ui.UiAdapter;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

/**
 * 流式消息处理器
 * 处理流式消息的累积和UI更新
 * 
 * 专注于消息内容的累积和处理，通过UiAdapter接口更新UI
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class StreamingMessageHandler {

    private final UiAdapter uiAdapter;
    private Object currentBubbleRef; // 使用Object类型，避免依赖UI具体类型
    private String currentMessageContent = "";
    private final StringBuilder messageBuffer = new StringBuilder();

    public StreamingMessageHandler(UiAdapter uiAdapter) {
        this.uiAdapter = uiAdapter;
    }

    /**
     * 准备接收新消息（重置状态）
     */
    public void prepareForNewMessage() {
        synchronized (this) {
            currentBubbleRef = null;
            currentMessageContent = "";
            messageBuffer.setLength(0);
        }
    }

    /**
     * 追加消息内容
     * 
     * 注意：此方法应该在 JavaFX 应用线程中调用（通过 Platform.runLater）
     *
     * @param chunk 消息片段
     * @param isFinished 是否完成
     */
    public void appendMessage(String chunk, boolean isFinished) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        // 追加到缓冲区（累积增量片段）
        synchronized (this) {
            messageBuffer.append(chunk);
            String fullContent = messageBuffer.toString();

            // 只在内容变化时更新UI（避免重复更新）
            if (!fullContent.equals(currentMessageContent)) {
                updateMessage(fullContent, !isFinished);
                currentMessageContent = fullContent;
            }
        }
    }

    /**
     * 更新消息内容
     * 
     * 注意：此方法必须在 JavaFX 应用线程中调用
     *
     * @param content 消息内容
     * @param isStreaming 是否正在流式输出
     */
    public void updateMessage(String content, boolean isStreaming) {
        if (uiAdapter == null) {
            return;
        }

        // 确保在 JavaFX 线程中执行（双重检查，以防万一）
        if (Platform.isFxApplicationThread()) {
            ensureBubbleExists();
            if (currentBubbleRef != null) {
                uiAdapter.updateMessageContentSync((javafx.scene.layout.VBox) currentBubbleRef, content, isStreaming);
            } else {
                // 备用方案：直接添加消息
                uiAdapter.addMessageSync("assistant", content, isStreaming);
            }
        } else {
            // 如果不在 JavaFX 线程中，使用 Platform.runLater（备用方案）
            Platform.runLater(() -> {
                ensureBubbleExists();
                if (currentBubbleRef != null) {
                    uiAdapter.updateMessageContentSync((javafx.scene.layout.VBox) currentBubbleRef, content, isStreaming);
                } else {
                    // 备用方案：直接添加消息
                    uiAdapter.addMessageSync("assistant", content, isStreaming);
                }
            });
        }
    }

    /**
     * 完成流式输出（异步版本）
     */
    public void finishStreaming() {
        // 保存当前气泡和内容的引用，避免在异步执行时被清除
        Object bubbleToUpdate = currentBubbleRef;
        String contentToUpdate = currentMessageContent;
        
        Platform.runLater(() -> {
            finishStreamingSync(bubbleToUpdate, contentToUpdate);
        });
    }

    /**
     * 完成流式输出（同步版本，必须在 JavaFX 线程中调用）
     */
    public void finishStreamingSync() {
        finishStreamingSync(currentBubbleRef, currentMessageContent);
    }

    /**
     * 完成流式输出（同步版本，使用指定的气泡和内容）
     */
    private void finishStreamingSync(Object bubbleToUpdate, String contentToUpdate) {
        if (uiAdapter == null) {
            return;
        }

        if (bubbleToUpdate != null) {
            // 如果气泡存在，先更新内容并移除流式指示器（即使内容为空也要更新以移除指示器）
            uiAdapter.updateMessageContentSync((javafx.scene.layout.VBox) bubbleToUpdate, contentToUpdate, false);
            
            // 如果内容为空，移除空气泡
            if (contentToUpdate != null && contentToUpdate.isEmpty()) {
                removeEmptyBubble((javafx.scene.layout.VBox) bubbleToUpdate);
            }
        } else if (contentToUpdate != null && !contentToUpdate.isEmpty()) {
            // 如果没有气泡但有内容，直接添加消息
            uiAdapter.addMessageSync("assistant", contentToUpdate, false);
        }
    }

    /**
     * 移除空气泡
     */
    private void removeEmptyBubble(javafx.scene.layout.VBox bubble) {
        if (uiAdapter == null || bubble == null) {
            return;
        }

        javafx.scene.layout.VBox messagesContainer = uiAdapter.getMessagesContainer();
        if (messagesContainer != null) {
            // 查找包含 bubble 的包装器并移除
            messagesContainer.getChildren().removeIf(node -> {
                if (node instanceof javafx.scene.layout.HBox wrapper) {
                    return wrapper.getChildren().contains(bubble);
                }
                return false;
            });
        }
    }

    /**
     * 清除消息状态
     */
    public void clearMessageState() {
        synchronized (this) {
            // 如果气泡存在但内容为空，移除空气泡
            Object bubbleToRemove = currentBubbleRef;
            if (bubbleToRemove != null && currentMessageContent.isEmpty()) {
                Platform.runLater(() -> {
                    removeEmptyBubble((javafx.scene.layout.VBox) bubbleToRemove);
                });
            }
            currentBubbleRef = null;
            currentMessageContent = "";
            messageBuffer.setLength(0);
        }
    }

    /**
     * 确保消息气泡存在
     */
    private void ensureBubbleExists() {
        synchronized (this) {
            if (currentBubbleRef == null && uiAdapter != null) {
                currentBubbleRef = uiAdapter.addAssistantMessagePlaceholder();
            }
        }
    }

    /**
     * 设置当前消息气泡（用于Agent模式）
     *
     * @param bubble 消息气泡
     */
    public void setCurrentBubble(javafx.scene.layout.VBox bubble) {
        synchronized (this) {
            this.currentBubbleRef = bubble;
        }
    }

    /**
     * 获取当前消息气泡（用于错误处理）
     *
     * @return 当前消息气泡
     */
    public javafx.scene.layout.VBox getCurrentBubble() {
        return (javafx.scene.layout.VBox) currentBubbleRef;
    }
}
