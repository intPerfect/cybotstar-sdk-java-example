package com.brgroup.cybotstar.javafx.service;

import com.alibaba.fastjson2.JSON;
import com.brgroup.cybotstar.flow.model.FlowDebugVO;
import com.brgroup.cybotstar.flow.model.FlowEndVO;
import com.brgroup.cybotstar.flow.model.FlowErrorVO;
import com.brgroup.cybotstar.flow.model.FlowJumpVO;
import com.brgroup.cybotstar.flow.model.FlowNodeEnterVO;
import com.brgroup.cybotstar.flow.model.FlowStartVO;
import com.brgroup.cybotstar.flow.model.FlowWaitingVO;
import com.brgroup.cybotstar.javafx.service.message.StreamingMessageHandler;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * 统一事件处理器
 * 统一管理AgentClient和FlowClient的各种事件订阅
 * 根据客户端模式动态设置不同的事件处理器
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class UnifiedEventHandler {

    private final UnifiedClientService clientService;
    private final StreamingMessageHandler messageHandler;
    private final Runnable onWaitingCallback;
    private final Runnable onEndCallback;
    private final Consumer<String> onErrorCallback;
    private final Runnable onStreamingFinishedCallback;
    private final Consumer<String> onJumpCallback;

    /**
     * Agent模式构造函数
     *
     * @param clientService 统一客户端服务
     */
    public UnifiedEventHandler(UnifiedClientService clientService) {
        this.clientService = clientService;
        this.messageHandler = null;
        this.onWaitingCallback = null;
        this.onEndCallback = null;
        this.onErrorCallback = null;
        this.onStreamingFinishedCallback = null;
        this.onJumpCallback = null;
    }

    /**
     * Flow模式构造函数
     *
     * @param clientService               统一客户端服务
     * @param messageHandler              流式消息处理器
     * @param onWaitingCallback           等待输入回调
     * @param onEndCallback               结束回调
     * @param onErrorCallback             错误回调
     * @param onStreamingFinishedCallback 流式输出完成回调（用于重置按钮状态）
     * @param onJumpCallback              节点跳转回调
     */
    public UnifiedEventHandler(UnifiedClientService clientService,
            StreamingMessageHandler messageHandler,
            Runnable onWaitingCallback,
            Runnable onEndCallback,
            Consumer<String> onErrorCallback,
            Runnable onStreamingFinishedCallback,
            Consumer<String> onJumpCallback) {
        this.clientService = clientService;
        this.messageHandler = messageHandler;
        this.onWaitingCallback = onWaitingCallback;
        this.onEndCallback = onEndCallback;
        this.onErrorCallback = onErrorCallback;
        this.onStreamingFinishedCallback = onStreamingFinishedCallback;
        this.onJumpCallback = onJumpCallback;
    }

    /**
     * 设置所有事件处理器
     */
    public void setupEventHandlers() {
        if (clientService.isFlowMode()) {
            setupFlowEventHandlers();
        } else {
            setupAgentEventHandlers();
        }
    }

    /**
     * 清除所有事件处理器
     */
    public void clearEventHandlers() {
        if (clientService.isFlowMode()) {
            clearFlowEventHandlers();
        } else {
            clearAgentEventHandlers();
        }
    }

    // ========== Agent模式事件处理器 ==========

    /**
     * 设置所有Agent事件处理器
     */
    private void setupAgentEventHandlers() {
        if (clientService.getAgentClient() == null) {
            return;
        }
        setupAgentRawRequestHandler();
        setupAgentRawResponseHandler();
    }

    /**
     * 设置Agent原始请求事件处理器
     */
    private void setupAgentRawRequestHandler() {
        clientService.getAgentClient().onRawRequest((request) -> {
            log.debug("[Agent Raw Request]: {}", JSON.toJSONString(request));
        });
    }

    /**
     * 设置Agent原始响应事件处理器
     */
    private void setupAgentRawResponseHandler() {
        clientService.getAgentClient().onRawResponse((response) -> {
            log.debug("[Agent Raw Response]: {}", JSON.toJSONString(response));
        });
    }

    /**
     * 清除所有Agent事件处理器
     * 注意：AgentClient 没有 offRawRequest 和 offRawResponse 方法
     * 这些回调会在 AgentClient.close() 时自动清除
     */
    private void clearAgentEventHandlers() {
        // AgentClient 的回调会在 close() 时自动清除，无需手动清除
    }

    // ========== Flow模式事件处理器 ==========

    /**
     * 设置所有Flow事件处理器
     */
    private void setupFlowEventHandlers() {
        if (clientService.getFlowClient() == null) {
            return;
        }
        setupFlowConnectedHandler();
        setupFlowDisconnectedHandler();
        setupFlowStartHandler();
        setupFlowNodeEnterHandler();
        setupFlowMessageHandler();
        setupFlowWaitingHandler();
        setupFlowEndHandler();
        setupFlowErrorHandler();
        setupFlowDebugHandler();
        setupFlowJumpHandler();
        setupFlowRawRequestHandler();
        setupFlowRawResponseHandler();
    }

    /**
     * 设置Flow连接建立事件处理器
     */
    private void setupFlowConnectedHandler() {
        clientService.getFlowClient().onConnected(() -> {
            Platform.runLater(() -> {
                // 更新连接状态（内部会触发connectionStateCallback）
                clientService.setConnected(true);
                // Flow 连接后重置消息状态，但不预先创建气泡
                // 气泡会在收到第一条消息时自动创建
                if (messageHandler != null) {
                    messageHandler.prepareForNewMessage();
                }
            });
        });
    }

    /**
     * 设置Flow连接断开事件处理器
     */
    private void setupFlowDisconnectedHandler() {
        clientService.getFlowClient().onDisconnected(() -> {
            Platform.runLater(() -> {
                // 更新连接状态（内部会触发connectionStateCallback）
                clientService.setConnected(false);
            });
        });
    }

    /**
     * 设置Flow启动事件处理器
     */
    private void setupFlowStartHandler() {
        clientService.getFlowClient().onStart((FlowStartVO vo) -> {
            Platform.runLater(() -> {
                log.debug("[START] FlowStartVO: {}", JSON.toJSONString(vo));
            });
        });
    }

    /**
     * 设置Flow节点进入事件处理器
     */
    private void setupFlowNodeEnterHandler() {
        clientService.getFlowClient().onNodeEnter((FlowNodeEnterVO vo) -> {
            Platform.runLater(() -> {
                log.debug("[NODE_ENTER] FlowNodeEnterVO: {}", JSON.toJSONString(vo));
                if (onJumpCallback != null && vo.getNodeTitle() != null) {
                    onJumpCallback.accept(vo.getNodeTitle());
                }
            });
        });
    }

    /**
     * 设置Flow消息事件处理器
     */
    private void setupFlowMessageHandler() {
        // 使用简化的 MessageHandler（接收 String msg, boolean isFinished）
        clientService.getFlowClient().onMessage((String msg, boolean isFinished) -> {
            if (msg != null && !msg.isEmpty()) {
                if (messageHandler != null) {
                    // 确保在 JavaFX 应用线程中执行，避免第一次流式输出卡住
                    Platform.runLater(() -> {
                        messageHandler.appendMessage(msg, isFinished);
                    });
                }
            }
        });
    }

    /**
     * 设置Flow等待输入事件处理器
     */
    private void setupFlowWaitingHandler() {
        clientService.getFlowClient().onWaiting((FlowWaitingVO vo) -> {
            Platform.runLater(() -> {
                if (messageHandler != null) {
                    messageHandler.finishStreaming();
                }
                // 重置按钮状态，允许用户发送下一次消息
                if (onStreamingFinishedCallback != null) {
                    onStreamingFinishedCallback.run();
                }
                if (onWaitingCallback != null) {
                    onWaitingCallback.run();
                }
            });
        });
    }

    /**
     * 设置Flow结束事件处理器
     */
    private void setupFlowEndHandler() {
        clientService.getFlowClient().onEnd((FlowEndVO vo) -> {
            Platform.runLater(() -> {
                // 先完成流式输出（移除流式指示器）
                // 使用同步方式确保立即执行
                if (messageHandler != null) {
                    messageHandler.finishStreamingSync();
                    // 清除消息状态
                    messageHandler.clearMessageState();
                }

                if (onEndCallback != null) {
                    onEndCallback.run();
                }
                // vo.getFinalText() 可用于获取最终文本
            });
        });
    }

    /**
     * 设置Flow错误事件处理器
     */
    private void setupFlowErrorHandler() {
        clientService.getFlowClient().onError((FlowErrorVO vo) -> {
            Platform.runLater(() -> {
                if (messageHandler != null) {
                    messageHandler.finishStreaming();
                }
                // 重置按钮状态
                if (onStreamingFinishedCallback != null) {
                    onStreamingFinishedCallback.run();
                }
                if (onErrorCallback != null) {
                    onErrorCallback.accept(vo.getErrorMessage() != null ? vo.getErrorMessage() : "未知错误");
                }
            });
        });
    }

    /**
     * 设置Flow调试信息事件处理器
     */
    private void setupFlowDebugHandler() {
        clientService.getFlowClient().onDebug((FlowDebugVO vo) -> {
            Platform.runLater(() -> {
                log.debug("[DEBUG] FlowDebugVO: {}", JSON.toJSONString(vo));
            });
        });
    }

    /**
     * 设置Flow跳转事件处理器
     */
    private void setupFlowJumpHandler() {
        clientService.getFlowClient().onJump((FlowJumpVO vo) -> {
            Platform.runLater(() -> {
                log.debug("[JUMP] FlowJumpVO: {}", JSON.toJSONString(vo));
                if (onJumpCallback != null) {
                    String title = vo.getNodeTitle() != null ? vo.getNodeTitle() : "节点跳转";
                    onJumpCallback.accept(title);
                }
            });
        });
    }

    /**
     * 设置Flow原始请求事件处理器
     */
    private void setupFlowRawRequestHandler() {
        clientService.getFlowClient().onRawRequest((request) -> {
            log.debug("[Flow Raw Request]: {}", JSON.toJSONString(request));
        });
    }

    /**
     * 设置Flow原始响应事件处理器
     */
    private void setupFlowRawResponseHandler() {
        clientService.getFlowClient().onRawResponse((response) -> {
            log.debug("[Flow Raw Response]: {}", JSON.toJSONString(response));
        });
    }

    /**
     * 清除所有Flow事件处理器
     */
    private void clearFlowEventHandlers() {
        if (clientService.getFlowClient() != null) {
            clientService.getFlowClient().offMessage();
            clientService.getFlowClient().offWaiting();
            clientService.getFlowClient().offEnd();
            clientService.getFlowClient().offError();
            // 注意：FlowClient 没有
            // offStart、offNodeEnter、offDebug、offJump、offRawRequest、offRawResponse、offDisconnected
            // 和 offConnected 方法
            // 这些事件处理器会在 UnifiedClientService.close() 中通过清除回调来避免触发
        }
    }
}
