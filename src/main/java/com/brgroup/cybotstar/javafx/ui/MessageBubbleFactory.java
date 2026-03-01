package com.brgroup.cybotstar.javafx.ui;

import com.brgroup.cybotstar.javafx.ChatConstants;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.beans.binding.Bindings;

/**
 * 消息气泡工厂类
 * 负责创建和更新消息气泡UI组件
 *
 * @author zhiyuan.xi
 */
public class MessageBubbleFactory {

    /**
     * 创建消息气泡
     */
    public static VBox createMessageBubble(String role, String content, boolean isStreaming, VBox messagesContainer) {
        VBox bubble = new VBox(10);
        bubble.getStyleClass().add("message-bubble");
        bubble.setSpacing(6);
        bubble.setMaxWidth(Region.USE_COMPUTED_SIZE);
        bubble.setMinWidth(Region.USE_COMPUTED_SIZE);
        bubble.setPrefWidth(Region.USE_COMPUTED_SIZE);
        bubble.setFillWidth(false);

        switch (role) {
            case "user":
                setupUserBubble(bubble, content, messagesContainer);
                break;
            case "assistant":
                setupAssistantBubble(bubble, content, isStreaming, messagesContainer);
                break;
            default:
                setupSystemBubble(bubble, content);
                break;
        }

        return bubble;
    }

    /**
     * 设置用户消息气泡
     */
    private static void setupUserBubble(VBox bubble, String content, VBox messagesContainer) {
        bubble.getStyleClass().add("user-bubble");
        bubble.setAlignment(Pos.TOP_RIGHT);

        Label roleLabel = new Label(getRoleDisplayName("user"));
        roleLabel.getStyleClass().add("message-role");

        Label contentLabel = createContentLabel(content, messagesContainer);
        bubble.getChildren().addAll(roleLabel, contentLabel);
    }

    /**
     * 设置助手消息气泡
     */
    private static void setupAssistantBubble(VBox bubble, String content, boolean isStreaming, VBox messagesContainer) {
        bubble.getStyleClass().add("assistant-bubble");
        bubble.setAlignment(Pos.TOP_LEFT);

        Label roleLabel = new Label(getRoleDisplayName("assistant"));
        roleLabel.getStyleClass().add("message-role");

        Label contentLabel = createContentLabel(content, messagesContainer);
        bubble.getChildren().add(roleLabel);
        bubble.getChildren().add(contentLabel);

        if (isStreaming) {
            Label streamingLabel = new Label("正在输入...");
            streamingLabel.getStyleClass().add("streaming-indicator");
            bubble.getChildren().add(streamingLabel);
        }
    }

    /**
     * 设置系统消息气泡
     */
    private static void setupSystemBubble(VBox bubble, String content) {
        bubble.getStyleClass().add("system-bubble");
        bubble.setAlignment(Pos.CENTER_LEFT);

        HBox systemContainer = new HBox(8);
        systemContainer.setAlignment(Pos.CENTER_LEFT);

        Region bar = new Region();
        bar.getStyleClass().add("system-bubble-bar");
        bar.setMinHeight(Region.USE_PREF_SIZE);

        Text systemContentText = new Text(content);
        systemContentText.getStyleClass().add("message-content");

        systemContainer.getChildren().addAll(bar, systemContentText);
        bubble.getChildren().add(systemContainer);
    }

    /**
     * 创建内容标签
     */
    private static Label createContentLabel(String content, VBox messagesContainer) {
        Label contentLabel = new Label(content);
        contentLabel.getStyleClass().add("message-content");
        contentLabel.setWrapText(true);

        if (messagesContainer != null) {
            contentLabel.maxWidthProperty().bind(
                Bindings.min(
                    messagesContainer.widthProperty().multiply(ChatConstants.MESSAGE_BUBBLE_WIDTH_RATIO),
                    Bindings.createDoubleBinding(() -> (double) ChatConstants.MESSAGE_BUBBLE_MAX_WIDTH)
                )
            );
        } else {
            contentLabel.setMaxWidth(ChatConstants.MESSAGE_BUBBLE_MAX_WIDTH);
        }

        contentLabel.setMinWidth(Region.USE_COMPUTED_SIZE);
        return contentLabel;
    }

    /**
     * 更新消息气泡内容
     */
    public static void updateMessageContent(VBox bubble, String content, boolean isStreaming, VBox messagesContainer) {
        if (bubble == null) {
            // 如果气泡为null，直接返回，避免NullPointerException
            return;
        }
        Label contentLabel = findContentLabel(bubble);
        Label streamingLabel = findStreamingLabel(bubble);

        if (contentLabel != null) {
            contentLabel.setText(content);
            contentLabel.setWrapText(true);

            if (messagesContainer != null && !contentLabel.maxWidthProperty().isBound()) {
                contentLabel.maxWidthProperty().bind(
                    Bindings.min(
                        messagesContainer.widthProperty().multiply(ChatConstants.MESSAGE_BUBBLE_WIDTH_RATIO),
                        Bindings.createDoubleBinding(() -> (double) ChatConstants.MESSAGE_BUBBLE_MAX_WIDTH)
                    )
                );
            }

            contentLabel.setMinWidth(Region.USE_COMPUTED_SIZE);
            bubble.setFillWidth(false);
            bubble.setPrefWidth(Region.USE_COMPUTED_SIZE);
            bubble.setMaxWidth(Region.USE_COMPUTED_SIZE);

            updateStreamingIndicator(bubble, streamingLabel, isStreaming);
            contentLabel.requestLayout();
            bubble.requestLayout();
        } else {
            // 如果找不到内容标签，重新创建（这种情况不应该发生）
            recreateBubbleContent(bubble, content, isStreaming, messagesContainer);
        }
    }

    /**
     * 查找内容标签
     */
    private static Label findContentLabel(VBox bubble) {
        for (Node node : bubble.getChildren()) {
            if (node instanceof Label label) {
                if (label.getStyleClass().contains("message-content")) {
                    return label;
                }
            }
        }
        return null;
    }

    /**
     * 查找流式指示器标签
     */
    private static Label findStreamingLabel(VBox bubble) {
        for (Node node : bubble.getChildren()) {
            if (node instanceof Label label) {
                if (label.getStyleClass().contains("streaming-indicator")) {
                    return label;
                }
            }
        }
        return null;
    }

    /**
     * 更新流式指示器
     */
    private static void updateStreamingIndicator(VBox bubble, Label streamingLabel, boolean isStreaming) {
        if (isStreaming) {
            if (streamingLabel == null) {
                streamingLabel = new Label("正在输入...");
                streamingLabel.getStyleClass().add("streaming-indicator");
                bubble.getChildren().add(streamingLabel);
            }
        } else {
            // 当 isStreaming=false 时，强制移除所有流式指示器
            // 即使 streamingLabel 参数是 null，也要重新查找并移除
            if (streamingLabel != null) {
                bubble.getChildren().remove(streamingLabel);
            } else {
                // 如果参数是 null，重新查找并移除所有流式指示器
                Label foundLabel = findStreamingLabel(bubble);
                if (foundLabel != null) {
                    bubble.getChildren().remove(foundLabel);
                }
            }
        }
    }

    /**
     * 重新创建气泡内容（备用方案）
     */
    private static void recreateBubbleContent(VBox bubble, String content, boolean isStreaming, VBox messagesContainer) {
        bubble.getChildren().clear();

        Label roleLabel = new Label(getRoleDisplayName("assistant"));
        roleLabel.getStyleClass().add("message-role");
        bubble.getChildren().add(roleLabel);

        Label contentLabel = createContentLabel(content, messagesContainer);
        bubble.getChildren().add(contentLabel);

        if (isStreaming) {
            Label streamingLabel = new Label("正在输入...");
            streamingLabel.getStyleClass().add("streaming-indicator");
            bubble.getChildren().add(streamingLabel);
        }
    }

    /**
     * 创建消息包装器（用于对齐）
     */
    public static HBox createMessageWrapper(String role, VBox bubble) {
        HBox wrapper = new HBox();
        wrapper.setMaxWidth(Double.MAX_VALUE);
        wrapper.getChildren().add(bubble);

        if ("user".equals(role)) {
            wrapper.setAlignment(Pos.CENTER_RIGHT);
        } else if ("assistant".equals(role)) {
            wrapper.setAlignment(Pos.CENTER_LEFT);
        } else {
            wrapper.setAlignment(Pos.CENTER);
        }

        HBox.setHgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    /**
     * 获取角色显示名称
     */
    private static String getRoleDisplayName(String role) {
        return switch (role) {
            case "user" -> "👤 你";
            case "assistant" -> "🤖 AI 助手";
            case "system" -> "ℹ️ 系统";
            default -> role;
        };
    }
}

