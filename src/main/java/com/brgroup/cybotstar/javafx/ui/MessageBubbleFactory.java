package com.brgroup.cybotstar.javafx.ui;

import com.brgroup.cybotstar.javafx.ChatConstants;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.beans.binding.Bindings;
import javafx.util.Duration;

public class MessageBubbleFactory {

    public static VBox createMessageBubble(String role, String content, boolean isStreaming, VBox messagesContainer) {
        VBox bubble = new VBox(10);
        bubble.getStyleClass().add("message-bubble");
        bubble.setSpacing(6);
        bubble.setMaxWidth(Region.USE_COMPUTED_SIZE);
        bubble.setMinWidth(Region.USE_COMPUTED_SIZE);
        bubble.setPrefWidth(Region.USE_COMPUTED_SIZE);
        bubble.setFillWidth(false);

        switch (role) {
            case "user" -> setupUserBubble(bubble, content, messagesContainer);
            case "assistant" -> setupAssistantBubble(bubble, content, isStreaming, messagesContainer);
            default -> setupSystemBubble(bubble, content);
        }

        return bubble;
    }

    private static void setupUserBubble(VBox bubble, String content, VBox messagesContainer) {
        bubble.getStyleClass().add("user-bubble");
        bubble.setAlignment(Pos.TOP_RIGHT);

        Label roleLabel = new Label(getRoleDisplayName("user"));
        roleLabel.getStyleClass().add("message-role");

        Label contentLabel = createContentLabel(content, messagesContainer);
        bubble.getChildren().addAll(roleLabel, contentLabel);
    }

    private static void setupAssistantBubble(VBox bubble, String content, boolean isStreaming, VBox messagesContainer) {
        bubble.getStyleClass().add("assistant-bubble");
        bubble.setAlignment(Pos.TOP_LEFT);

        Label roleLabel = new Label(getRoleDisplayName("assistant"));
        roleLabel.getStyleClass().add("message-role");

        Label contentLabel = createContentLabel(content, messagesContainer);
        bubble.getChildren().addAll(roleLabel, contentLabel);

        if (isStreaming) {
            Label streamingLabel = new Label("正在输入...");
            streamingLabel.getStyleClass().add("streaming-indicator");
            bubble.getChildren().add(streamingLabel);
        }
    }

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

    public static void updateMessageContent(VBox bubble, String content, boolean isStreaming, VBox messagesContainer) {
        if (bubble == null) {
            return;
        }
        
        Label contentLabel = findChildByClass(bubble, Label.class, "message-content");
        Label streamingLabel = findChildByClass(bubble, Label.class, "streaming-indicator");

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
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T findChildByClass(VBox parent, Class<T> type, String styleClass) {
        for (Node node : parent.getChildren()) {
            if (type.isInstance(node) && node.getStyleClass().contains(styleClass)) {
                return (T) node;
            }
        }
        return null;
    }

    private static void updateStreamingIndicator(VBox bubble, Label streamingLabel, boolean isStreaming) {
        if (isStreaming) {
            if (streamingLabel == null) {
                streamingLabel = new Label("正在输入...");
                streamingLabel.getStyleClass().add("streaming-indicator");
                bubble.getChildren().add(streamingLabel);
            }
        } else {
            if (streamingLabel != null) {
                bubble.getChildren().remove(streamingLabel);
            } else {
                Label foundLabel = findChildByClass(bubble, Label.class, "streaming-indicator");
                if (foundLabel != null) {
                    bubble.getChildren().remove(foundLabel);
                }
            }
        }
    }

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

    private static String getRoleDisplayName(String role) {
        return switch (role) {
            case "user" -> "👤 你";
            case "assistant" -> "🤖 AI 助手";
            case "system" -> "ℹ️ 系统";
            default -> role;
        };
    }

    public static void scrollToBottom(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        if (javafx.application.Platform.isFxApplicationThread()) {
            if (scrollPane.vvalueProperty().isBound()) {
                scrollPane.vvalueProperty().unbind();
            }
            PauseTransition pause = new PauseTransition(Duration.millis(ChatConstants.SCROLL_DELAY_MS));
            pause.setOnFinished(e -> {
                if (scrollPane != null) {
                    scrollPane.setVvalue(1.0);
                    scrollPane.requestLayout();
                }
            });
            pause.play();
        } else {
            javafx.application.Platform.runLater(() -> scrollToBottom(scrollPane));
        }
    }
}
