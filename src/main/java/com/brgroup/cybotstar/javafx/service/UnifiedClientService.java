package com.brgroup.cybotstar.javafx.service;

import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.flow.config.FlowConfig;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.javafx.service.config.YamlConfigService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 统一客户端服务
 * 统一管理AgentClient和FlowClient的创建、连接和生命周期
 * 使用内部枚举区分Agent和Flow模式
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class UnifiedClientService {

    /**
     * 客户端模式枚举
     */
    public enum ClientMode {
        AGENT,
        FLOW
    }

    private final ClientMode mode;
    private final AgentClient agentClient;
    private final FlowClient flowClient;
    private boolean connected = false;
    private Consumer<Boolean> connectionStateCallback;

    // ========== 私有构造函数 ==========

    /**
     * Agent模式构造函数
     */
    private UnifiedClientService(AgentConfig agentConfig) {
        this.mode = ClientMode.AGENT;
        this.agentClient = new AgentClient(agentConfig);
        this.flowClient = null;
    }

    /**
     * Flow模式构造函数
     */
    private UnifiedClientService(FlowConfig flowConfig) {
        this.mode = ClientMode.FLOW;
        this.agentClient = null;
        this.flowClient = new FlowClient(flowConfig);
    }

    // ========== 工厂方法 ==========

    /**
     * 创建客户端（AgentClient或FlowClient）
     *
     * @param configService 配置服务
     * @param profileName   配置profile名称
     * @return UnifiedClientService实例
     */
    public static UnifiedClientService createClient(YamlConfigService configService, String profileName) {
        boolean isFlowMode = configService.isFlowMode(profileName);

        if (isFlowMode) {
            FlowConfig flowConfig = configService.loadFlowConfig(profileName);
            if (flowConfig == null) {
                log.error("无法加载 Flow 配置: {}", profileName);
                // 回退到 Agent 模式
                AgentConfig agentConfig = configService.loadAgentConfig(profileName);
                return new UnifiedClientService(agentConfig);
            }
            return new UnifiedClientService(flowConfig);
        } else {
            AgentConfig agentConfig = configService.loadAgentConfig(profileName);
            return new UnifiedClientService(agentConfig);
        }
    }

    // ========== 公共方法 ==========

    /**
     * 初始化客户端（根据配置创建AgentClient或FlowClient）
     *
     * @param profileName 配置profile名称
     */
    public void initializeClient(String profileName) {
        // 客户端在构造时已初始化，无需额外操作
    }

    /**
     * 连接服务器
     *
     * @return CompletableFuture，连接完成后完成
     */
    public CompletableFuture<Void> connect() {
        if (mode == ClientMode.AGENT) {
            return connectAgent();
        } else {
            return connectFlow();
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (mode == ClientMode.AGENT) {
            disconnectAgent();
        } else {
            disconnectFlow();
        }
    }

    /**
     * 关闭客户端
     */
    public void close() {
        if (mode == ClientMode.AGENT) {
            closeAgent();
        } else {
            closeFlow();
        }
    }

    /**
     * 是否已连接
     *
     * @return true表示已连接
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 是否为Flow模式
     *
     * @return true表示Flow模式
     */
    public boolean isFlowMode() {
        return mode == ClientMode.FLOW;
    }

    /**
     * 获取AgentClient（如果存在）
     *
     * @return AgentClient实例，如果不存在则返回null
     */
    public AgentClient getAgentClient() {
        return agentClient;
    }

    /**
     * 获取FlowClient（如果存在）
     *
     * @return FlowClient实例，如果不存在则返回null
     */
    public FlowClient getFlowClient() {
        return flowClient;
    }

    /**
     * 设置连接状态变更回调
     *
     * @param callback 回调函数，参数为连接状态（true=已连接，false=未连接）
     */
    public void setConnectionStateCallback(Consumer<Boolean> callback) {
        this.connectionStateCallback = callback;
    }

    /**
     * 设置连接状态（由事件处理器调用，主要用于Flow模式）
     *
     * @param connected 连接状态
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
        notifyConnectionState(connected);
    }

    // ========== Agent模式方法 ==========

    /**
     * Agent模式：连接服务器
     */
    private CompletableFuture<Void> connectAgent() {
        // Agent 模式：连接会在首次 send() 或 stream() 调用时自动建立
        // 这里直接返回已完成的 future，UI状态会在首次操作成功时更新
        CompletableFuture<Void> future = new CompletableFuture<>();
        connected = true;
        notifyConnectionState(true);
        future.complete(null);
        return future;
    }

    /**
     * Agent模式：断开连接
     */
    private void disconnectAgent() {
        if (agentClient != null) {
            // AgentClient 的 disconnect 方法需要 sessionId 参数
            // 这里断开所有连接，可以调用 close() 方法
            agentClient.close();
        }
        connected = false;
        notifyConnectionState(false);
    }

    /**
     * Agent模式：关闭客户端
     */
    private void closeAgent() {
        if (agentClient != null) {
            agentClient.close();
        }
        connected = false;
        notifyConnectionState(false);
    }

    // ========== Flow模式方法 ==========

    /**
     * Flow模式：连接服务器
     */
    private CompletableFuture<Void> connectFlow() {
        // Flow 模式：启动 Flow 并等待连接建立
        // 注意：事件处理器需要在外部设置
        // 连接状态会在 onConnected 事件中更新
        return CompletableFuture.runAsync(() -> {
            try {
                // Reactive API: start() 返回 Mono<String>，需要调用 block() 阻塞等待
                String sessionId = flowClient.start("").block();
                log.debug("Flow 连接已建立，Session ID: {}", sessionId);
            } catch (Exception e) {
                log.error("Flow 连接失败", e);
                connected = false;
                notifyConnectionState(false);
                throw new RuntimeException(e);
            }
        }).exceptionally(e -> {
            log.error("Flow 连接失败", e);
            connected = false;
            notifyConnectionState(false);
            return null;
        });
    }

    /**
     * Flow模式：断开连接
     */
    private void disconnectFlow() {
        if (flowClient != null) {
            flowClient.close();
        }
        connected = false;
        // 注意：不在这里调用 notifyConnectionState，因为 FlowClient 的 onDisconnected 事件处理器会调用
        // 这样可以避免重复触发断开连接事件
    }

    /**
     * Flow模式：关闭客户端
     */
    private void closeFlow() {
        if (flowClient != null) {
            // 先清除连接状态回调，避免在关闭时触发回调
            Consumer<Boolean> oldCallback = this.connectionStateCallback;
            this.connectionStateCallback = null;

            // 清除所有事件处理器
            flowClient.offMessage();
            flowClient.offWaiting();
            flowClient.offEnd();
            flowClient.offError();

            // 关闭连接
            flowClient.close();

            // 恢复回调（如果需要）
            this.connectionStateCallback = oldCallback;
        }
        connected = false;
        // 注意：清除回调后，不会触发 onDisconnected 回调
    }

    // ========== 私有辅助方法 ==========

    /**
     * 通知连接状态变更
     */
    private void notifyConnectionState(boolean connected) {
        if (connectionStateCallback != null) {
            connectionStateCallback.accept(connected);
        }
    }
}
