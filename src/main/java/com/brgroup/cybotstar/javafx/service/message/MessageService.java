package com.brgroup.cybotstar.javafx.service.message;

import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.javafx.ui.UiAdapter;
import com.brgroup.cybotstar.flow.exception.FlowException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 消息服务
 * 统一处理Agent和Flow模式的消息发送
 *
 * 专注于SDK调用逻辑，通过UiAdapter接口更新UI，不直接依赖UI实现
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class MessageService {

    private final AgentClient agentClient;
    private final FlowClient flowClient;
    private final boolean isFlowMode;
    private final UiAdapter uiAdapter;
    private final StreamingMessageHandler streamingHandler;
    private final Consumer<String> onSystemMessageCallback;
    private final Runnable onStreamingFinishedCallback;

    public MessageService(AgentClient agentClient,
            FlowClient flowClient,
            boolean isFlowMode,
            UiAdapter uiAdapter,
            Consumer<String> onSystemMessageCallback,
            Runnable onStreamingFinishedCallback) {
        this.agentClient = agentClient;
        this.flowClient = flowClient;
        this.isFlowMode = isFlowMode;
        this.uiAdapter = uiAdapter;
        this.onSystemMessageCallback = onSystemMessageCallback;
        this.onStreamingFinishedCallback = onStreamingFinishedCallback;
        this.streamingHandler = new StreamingMessageHandler(uiAdapter);
    }


    /**
     * 发送消息
     *
     * @param question    用户问题
     * @param modelOptions 模型参数
     * @return CompletableFuture，消息发送完成后完成
     */
    public CompletableFuture<Void> sendMessage(String question, ModelOptions modelOptions) {
        if (question == null || question.trim().isEmpty()) {
            notifySystemMessage("错误：请输入消息内容");
            return CompletableFuture.completedFuture(null);
        }

        // 显示用户消息
        if (uiAdapter != null) {
            uiAdapter.addMessage("user", question, false);
        }

        if (isFlowMode && flowClient != null) {
            return sendFlowMessage(question);
        } else if (agentClient != null) {
            ModelOptions params = modelOptions != null ? modelOptions : ModelOptions.builder().build();
            return sendAgentMessage(question, params);
        } else {
            notifySystemMessage("错误：客户端未初始化");
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 发送Flow消息
     * SDK调用：flowClient.send(question)
     */
    private CompletableFuture<Void> sendFlowMessage(String question) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            // Flow模式：重置消息状态
            streamingHandler.prepareForNewMessage();

            // 检查Flow状态（用于调试）
            if (flowClient != null) {
                log.debug("准备发送Flow消息，当前状态: {}", flowClient.getState());
            }

            // ========== SDK调用：Flow模式（Reactive） ==========
            // Flow 模式：使用 FlowClient.send() 返回 Mono<Void>
            flowClient.send(question).block();
            // ======================================

            // Flow 模式下，消息更新由事件处理器处理
            log.debug("Flow消息发送成功");
            future.complete(null);
        } catch (FlowException e) {
            // Flow特定的异常，提供更详细的错误信息
            String errorMsg = e.getMessage();
            log.error("发送Flow消息失败: Flow状态异常 - {}, 当前状态: {}",
                errorMsg, flowClient != null ? flowClient.getState() : "unknown", e);

            // 根据错误类型提供更友好的提示
            if (e.getCode() != null) {
                if (e.getCode().equals("NOT_WAITING")) {
                    notifySystemMessage("错误：Flow当前未等待输入，请等待Flow完成当前处理");
                } else if (e.getCode().equals("NOT_RUNNING")) {
                    notifySystemMessage("错误：Flow对话未运行，请先连接Flow");
                } else {
                    notifySystemMessage("错误：发送Flow消息失败 - " + errorMsg);
                }
            } else {
                notifySystemMessage("错误：发送Flow消息失败 - " + errorMsg);
            }

            streamingHandler.finishStreaming();
            finishStreaming();
            future.completeExceptionally(e);
        } catch (Exception e) {
            log.error("发送Flow消息失败", e);
            notifySystemMessage("错误：发送Flow消息失败 - " + e.getMessage());
            streamingHandler.finishStreaming();
            finishStreaming();
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * 发送Agent消息
     * SDK调用：agentClient.prompt(question).option(modelParams).stream() 返回 Flux<String>
     */
    private CompletableFuture<Void> sendAgentMessage(String question, ModelOptions modelOptions) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Agent模式：重置消息状态
        streamingHandler.prepareForNewMessage();

        final StringBuilder fullResponse = new StringBuilder();
        final String[] lastContent = { "" };

        try {
            // ========== SDK调用：Agent模式（Reactive） ==========
            // 使用 Flux 流式处理
            agentClient
                    .prompt(question)
                    .option(modelOptions)
                    .stream()
                    .doOnNext(chunk -> {
                        // 处理流式响应片段
                        if (chunk != null) {
                            fullResponse.append(chunk);
                            String currentContent = fullResponse.toString();
                            if (!currentContent.equals(lastContent[0])) {
                                // 通过UiAdapter更新UI
                                updateAssistantMessage(currentContent, true);
                                lastContent[0] = currentContent;
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // 完成流式输出
                        updateAssistantMessage(fullResponse.toString(), false);
                        finishStreaming();
                        notifySystemMessage("回复完成");
                    })
                    .doOnError(error -> {
                        log.error("发送Agent消息失败", error);
                        handleAgentMessageError(error instanceof Exception ? (Exception) error : new Exception(error));
                    })
                    .blockLast();
            // ======================================

            future.complete(null);
        } catch (Exception e) {
            log.error("发送Agent消息失败", e);
            handleAgentMessageError(e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 更新助手消息内容
     */
    private void updateAssistantMessage(String content, boolean isStreaming) {
        if (uiAdapter != null) {
            // 通过StreamingMessageHandler获取当前气泡并更新
            streamingHandler.updateMessage(content, isStreaming);
        }
    }

    /**
     * 处理Agent消息错误
     */
    private void handleAgentMessageError(Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (uiAdapter != null) {
            uiAdapter.addMessage("assistant", "错误: " + errorMsg, false);
            uiAdapter.setStreaming(false);
        }
        notifySystemMessage("发送消息失败: " + errorMsg);
        finishStreaming();
    }

    /**
     * 通知系统消息
     */
    private void notifySystemMessage(String message) {
        if (onSystemMessageCallback != null) {
            onSystemMessageCallback.accept(message);
        }
    }

    /**
     * 完成流式输出
     */
    private void finishStreaming() {
        if (onStreamingFinishedCallback != null) {
            onStreamingFinishedCallback.run();
        }
    }

    /**
     * 获取流式消息处理器
     *
     * @return StreamingMessageHandler实例
     */
    public StreamingMessageHandler getStreamingHandler() {
        return streamingHandler;
    }
}
