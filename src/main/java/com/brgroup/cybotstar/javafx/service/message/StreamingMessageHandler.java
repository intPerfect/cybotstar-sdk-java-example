package com.brgroup.cybotstar.javafx.service.message;

import com.brgroup.cybotstar.javafx.ui.UiAdapter;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StreamingMessageHandler {

    private final UiAdapter uiAdapter;
    private Object currentBubbleRef;
    private String currentMessageContent = "";
    private final StringBuilder messageBuffer = new StringBuilder();

    public StreamingMessageHandler(UiAdapter uiAdapter) {
        this.uiAdapter = uiAdapter;
    }

    public void prepareForNewMessage() {
        synchronized (this) {
            currentBubbleRef = null;
            currentMessageContent = "";
            messageBuffer.setLength(0);
        }
    }

    public void appendMessage(String chunk, boolean isFinished) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        synchronized (this) {
            messageBuffer.append(chunk);
            String fullContent = messageBuffer.toString();

            if (!fullContent.equals(currentMessageContent)) {
                updateMessage(fullContent, !isFinished);
                currentMessageContent = fullContent;
            }
        }
    }

    public void updateMessage(String content, boolean isStreaming) {
        if (uiAdapter == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            ensureBubbleExists();
            if (currentBubbleRef != null) {
                uiAdapter.updateMessageContentSync((javafx.scene.layout.VBox) currentBubbleRef, content, isStreaming);
            } else {
                uiAdapter.addMessageSync("assistant", content, isStreaming);
            }
        } else {
            Platform.runLater(() -> {
                ensureBubbleExists();
                if (currentBubbleRef != null) {
                    uiAdapter.updateMessageContentSync((javafx.scene.layout.VBox) currentBubbleRef, content, isStreaming);
                } else {
                    uiAdapter.addMessageSync("assistant", content, isStreaming);
                }
            });
        }
    }

    public void finishStreaming() {
        Object bubbleToUpdate = currentBubbleRef;
        String contentToUpdate = currentMessageContent;
        
        Platform.runLater(() -> {
            finishStreamingSync(bubbleToUpdate, contentToUpdate);
        });
    }

    public void finishStreamingSync() {
        finishStreamingSync(currentBubbleRef, currentMessageContent);
    }

    private void finishStreamingSync(Object bubbleToUpdate, String contentToUpdate) {
        if (uiAdapter == null) {
            return;
        }

        if (bubbleToUpdate != null) {
            uiAdapter.updateMessageContentSync((javafx.scene.layout.VBox) bubbleToUpdate, contentToUpdate, false);
            
            if (contentToUpdate != null && contentToUpdate.isEmpty()) {
                removeEmptyBubble((javafx.scene.layout.VBox) bubbleToUpdate);
            }
        } else if (contentToUpdate != null && !contentToUpdate.isEmpty()) {
            uiAdapter.addMessageSync("assistant", contentToUpdate, false);
        }
    }

    private void removeEmptyBubble(javafx.scene.layout.VBox bubble) {
        if (uiAdapter == null || bubble == null) {
            return;
        }

        javafx.scene.layout.VBox messagesContainer = uiAdapter.getMessagesContainer();
        if (messagesContainer != null) {
            messagesContainer.getChildren().removeIf(node -> {
                if (node instanceof javafx.scene.layout.HBox wrapper) {
                    return wrapper.getChildren().contains(bubble);
                }
                return false;
            });
        }
    }

    public void clearMessageState() {
        synchronized (this) {
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

    private void ensureBubbleExists() {
        synchronized (this) {
            if (currentBubbleRef == null && uiAdapter != null) {
                currentBubbleRef = uiAdapter.addAssistantMessagePlaceholder();
            }
        }
    }
}
