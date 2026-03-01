package com.brgroup.cybotstar.javafx;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.flow.config.FlowConfig;
import com.brgroup.cybotstar.javafx.service.UnifiedClientService;
import com.brgroup.cybotstar.javafx.service.UnifiedEventHandler;
import com.brgroup.cybotstar.javafx.service.config.YamlConfigService;
import com.brgroup.cybotstar.javafx.service.config.YamlConfigService;
import com.brgroup.cybotstar.javafx.service.message.MessageService;
import com.brgroup.cybotstar.javafx.util.YamlConfigManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CybotStar JavaFX 演示应用
 * 展示 Agent SDK 的流式对话功能
 * 
 * 专注于服务层的初始化和协调，通过UiAdapter接口与UI层交互
 * SDK的使用逻辑清晰可见
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ChatApp extends Application {

    // ========== 服务层 ==========
    private YamlConfigService configService;
    private UnifiedClientService clientService;
    private MessageService messageService;
    private UnifiedEventHandler eventHandler;

    // ========== UI层引用 ==========
    private ChatController controller;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void start(Stage stage) {
        try {
            // ========== 初始化服务层 ==========
            initializeServices();

            // ========== 初始化UI层 ==========
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/javafx/chat-view.fxml"));
            BorderPane root = loader.load();
            controller = loader.getController();
            controller.setApp(this);

            // 加载配置文件列表
            List<String> configProfiles = configService.getConfigProfileList();
            controller.setConfigProfiles(configProfiles, configService.getCurrentConfigProfile());

            // ========== 初始化消息服务和事件处理器 ==========
            initializeMessageServices();

            // 设置调试信息
            updateDebugInfo();

            // ========== 设置UI ==========
            Scene scene = new Scene(root, ChatConstants.WINDOW_WIDTH, ChatConstants.WINDOW_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/javafx/chat-style.css").toExternalForm());
            stage.setTitle(ChatConstants.APP_TITLE);
            stage.setScene(scene);
            stage.setOnCloseRequest(e -> {
                e.consume();
                shutdown();
                Platform.exit();
                System.exit(0);
            });
            stage.setMaximized(true);
            stage.show();

            log.debug("CybotStar JavaFX 演示应用已启动");

            // 启动后自动连接
            Platform.runLater(() -> {
                connect().exceptionally(e -> {
                    log.error("自动连接失败", e);
                    Platform.runLater(() -> showError("自动连接失败", e.getMessage()));
                    return null;
                });
            });

        } catch (Exception e) {
            log.error("启动应用失败", e);
            showError("启动失败", e.getMessage());
            Platform.exit();
        }
    }

    // ========== 服务层初始化 ==========

    /**
     * 初始化服务层
     */
    private void initializeServices() {
        // 初始化配置服务
        configService = new YamlConfigService(this::onConfigChanged);
        configService.initializeDefaultConfigs();

        // ========== SDK初始化：创建客户端服务 ==========
        String currentProfile = configService.getCurrentConfigProfile();
        clientService = UnifiedClientService.createClient(configService, currentProfile);
        clientService.setConnectionStateCallback(this::onConnectionStateChanged);
    }

    /**
     * 初始化消息服务和事件处理器
     */
    private void initializeMessageServices() {
        // ========== SDK使用：创建消息服务 ==========
        messageService = new MessageService(
                clientService.getAgentClient(),
                clientService.getFlowClient(),
                clientService.isFlowMode(),
                controller, // 通过UiAdapter接口与UI交互
                this::addSystemMessage,
                this::onStreamingFinished
        );

        // ========== SDK使用：设置事件处理器 ==========
        setupEventHandlers();
    }

    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        if (clientService.isFlowMode() && clientService.getFlowClient() != null) {
            // Flow模式：需要流式消息处理器和回调
            eventHandler = new UnifiedEventHandler(
                    clientService,
                    messageService.getStreamingHandler(),
                    () -> addSystemMessage("Flow 等待用户输入..."),
                    () -> addSystemMessage("✅ Flow 已完成"),
                    error -> addSystemMessage("❌ Flow 错误: " + error),
                    this::onStreamingFinished,  // 添加流式输出完成回调，用于重置按钮状态
                    nodeTitle -> addSystemMessage("Flow 进入节点: " + nodeTitle)  // 节点跳转回调
            );
        } else if (!clientService.isFlowMode() && clientService.getAgentClient() != null) {
            // Agent模式：只需要客户端服务
            eventHandler = new UnifiedEventHandler(clientService);
        } else {
            log.warn("无法设置事件处理器：客户端未初始化");
            return;
        }
        eventHandler.setupEventHandlers();
    }

    // ========== SDK使用：连接管理 ==========

    /**
     * 连接服务器
     */
    public CompletableFuture<Void> connect() {
        if (clientService == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("客户端未初始化"));
            return future;
        }
        // SDK调用：clientService.connect()
        return clientService.connect();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (clientService != null) {
            // SDK调用：clientService.disconnect()
            clientService.disconnect();
        }
    }

    // ========== SDK使用：消息发送 ==========

    /**
     * 发送消息
     */
    public void sendMessage(String question, ModelOptions modelOptions) {
        if (!clientService.isConnected()) {
            addSystemMessage("错误：请先连接服务器");
            return;
        }

        if (question == null || question.trim().isEmpty()) {
            addSystemMessage("错误：请输入消息内容");
            return;
        }

        // 更新UI状态（通过controller）
        if (controller != null) {
            controller.setStreaming(true);
        }

        // ========== SDK调用：在后台线程中发送消息 ==========
        executor.submit(() -> {
            try {
                // SDK调用：messageService.sendMessage()
                // 实际的SDK调用逻辑在MessageService中，清晰可见
                messageService.sendMessage(question, modelOptions).join();
            } catch (Exception e) {
                log.error("发送消息失败", e);
                Platform.runLater(() -> {
                    addSystemMessage("错误：" + e.getMessage());
                    onStreamingFinished();
                });
            }
        });
    }

    /**
     * 从UI获取模型参数
     */
    public ModelOptions buildModelParamsFromUI() {
        // 通过controller获取UI参数
        if (controller == null) {
            return ModelOptions.builder()
                    .temperature(ChatConstants.DEFAULT_TEMPERATURE)
                    .maxTokens(ChatConstants.DEFAULT_MAX_TOKENS)
                    .build();
        }
        return controller.buildModelParams();
    }

    // ========== 回调方法 ==========

    /**
     * 配置变更回调
     */
    private void onConfigChanged() {
        Platform.runLater(() -> {
            List<String> configProfiles = configService.getConfigProfileList();
            if (controller != null) {
                controller.setConfigProfiles(configProfiles, configService.getCurrentConfigProfile());
            }
            updateDebugInfo();
        });
    }

    /**
     * 连接状态变更回调
     */
    private void onConnectionStateChanged(boolean connected) {
        Platform.runLater(() -> {
            // 通过controller更新UI状态
            if (controller != null) {
                controller.updateConnectionStatus(connected);
            }
            if (connected) {
                addSystemMessage("已连接到 " + configService.getCurrentConfigProfile() + 
                        (clientService.isFlowMode() ? " (Flow)" : " (Agent)"));
            } else {
                addSystemMessage("已断开连接");
            }
        });
    }

    /**
     * 流式输出完成回调
     */
    private void onStreamingFinished() {
        Platform.runLater(() -> {
            // 通过controller更新UI状态
            if (controller != null) {
                controller.setStreaming(false);
            }
        });
    }

    // ========== 配置管理 ==========

    /**
     * 加载配置并初始化客户端
     */
    public void loadConfigAndInitClient(String profileName) {
        // 关闭旧客户端
        closeClientService();

        // ========== SDK初始化：创建新客户端 ==========
        clientService = UnifiedClientService.createClient(configService, profileName);
        clientService.setConnectionStateCallback(this::onConnectionStateChanged);

        // 重新初始化消息服务和事件处理器
        if (controller != null) {
            initializeMessageServices();
        }

        // 更新UI
        updateDebugInfo();
    }

    /**
     * 关闭客户端服务
     */
    private void closeClientService() {
        if (clientService == null) {
            return;
        }
        // 清除事件处理器
        if (eventHandler != null) {
            eventHandler.clearEventHandlers();
        }
        // SDK调用：关闭客户端
        clientService.setConnectionStateCallback(null);
        clientService.close();
    }

    /**
     * 加载指定配置文件
     */
    public void loadConfigProfile(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            return;
        }
        configService.setCurrentConfigProfile(profileName);
        boolean wasConnected = clientService != null && clientService.isConnected();

        // 加载新配置并初始化客户端
        loadConfigAndInitClient(profileName);

        // 如果之前已连接，则自动重新连接
        if (wasConnected) {
            Platform.runLater(() -> {
                connect().exceptionally(e -> {
                    log.error("重新连接失败", e);
                    Platform.runLater(() -> showError("重新连接失败", e.getMessage()));
                    return null;
                });
            });
        }
    }

    /**
     * 更新调试信息显示
     */
    private void updateDebugInfo() {
        if (controller == null || clientService == null) {
            return;
        }

        String profileName = configService.getCurrentConfigProfile();
        FlowConfig flowConfig = null;
        AgentConfig agentConfig = null;

        if (clientService.isFlowMode()) {
            flowConfig = configService.loadFlowConfig(profileName);
        } else {
            agentConfig = configService.loadAgentConfig(profileName);
        }

        if (flowConfig != null && flowConfig.getCredentials() != null) {
            controller.setDebugInfo(
                    flowConfig.getWebsocket().getUrl(),
                    flowConfig.getCredentials().getRobotKey(),
                    flowConfig.getCredentials().getRobotToken(),
                    flowConfig.getCredentials().getUsername());
        } else if (agentConfig != null && agentConfig.getCredentials() != null) {
            controller.setDebugInfo(
                    agentConfig.getWebsocket().getUrl(),
                    agentConfig.getCredentials().getRobotKey(),
                    agentConfig.getCredentials().getRobotToken(),
                    agentConfig.getCredentials().getUsername());
        }
    }

    // ========== UI辅助方法 ==========

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        if (controller != null) {
            controller.addMessage("system", content, false);
        }
    }

    /**
     * 清空消息
     */
    public void clearMessages() {
        if (controller != null) {
            controller.clearMessages();
        }
    }

    /**
     * 显示错误对话框
     */
    public void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    // ========== 配置管理委托方法 ==========

    public String getCurrentConfigProfile() {
        return configService.getCurrentConfigProfile();
    }

    public List<String> getConfigProfileList() {
        return configService.getConfigProfileList();
    }

    public void saveConfig(String profileName, String url, String robotKey, String robotToken, String username, boolean isNew) {
        configService.saveConfig(profileName, url, robotKey, robotToken, username, isNew);
        if (isNew) {
            addSystemMessage("新配置 '" + profileName + "' 已保存");
            List<String> configProfiles = configService.getConfigProfileList();
            if (controller != null) {
                controller.setConfigProfiles(configProfiles, profileName);
            }
            configService.setCurrentConfigProfile(profileName);
        } else {
            addSystemMessage("配置 '" + profileName + "' 已保存");
        }

        if (profileName.equals(configService.getCurrentConfigProfile())) {
            boolean wasConnected = clientService != null && clientService.isConnected();
            if (clientService != null) {
                clientService.disconnect();
            }
            loadConfigAndInitClient(profileName);
            if (wasConnected) {
                connect().exceptionally(e -> {
                    log.error("重新连接失败", e);
                    Platform.runLater(() -> showError("重新连接失败", e.getMessage()));
                    return null;
                });
            }
            updateDebugInfo();
        }
    }

    public YamlConfigManager getYamlConfigManager() {
        return configService.getYamlConfigManager();
    }

    public boolean addYamlProfile(String profileName, String robotKey, String robotToken, String username,
                                 String webSocketUrl, String httpUrl, boolean isAgent) {
        boolean success = configService.addYamlProfile(profileName, robotKey, robotToken, username, webSocketUrl, httpUrl, isAgent);
        if (success) {
            addSystemMessage("配置 '" + profileName + "' 已添加到 YAML 文件");
        }
        return success;
    }

    public boolean updateYamlProfile(String profileName, String robotKey, String robotToken, String username,
                                    String webSocketUrl, String httpUrl) {
        boolean success = configService.updateYamlProfile(profileName, robotKey, robotToken, username, webSocketUrl, httpUrl);
        if (success) {
            addSystemMessage("配置 '" + profileName + "' 已更新到 YAML 文件");
            if (profileName.equals(configService.getCurrentConfigProfile())) {
                loadConfigProfile(profileName);
            }
        }
        return success;
    }

    public boolean deleteYamlProfile(String profileName) {
        if (profileName.equals(configService.getCurrentConfigProfile())) {
            showError("删除失败", "不能删除当前正在使用的配置");
            return false;
        }
        boolean success = configService.deleteYamlProfile(profileName);
        if (success) {
            addSystemMessage("配置 '" + profileName + "' 已从 YAML 文件删除");
        }
        return success;
    }

    public Map<String, Object> getYamlProfile(String profileName) {
        return configService.getYamlProfile(profileName);
    }

    public List<String> getAllYamlProfileNames() {
        return configService.getAllYamlProfileNames();
    }

    // ========== 应用生命周期 ==========

    /**
     * 关闭应用
     */
    public void shutdown() {
        log.debug("开始关闭应用...");
        
        // 清除事件处理器
        if (eventHandler != null) {
            eventHandler.clearEventHandlers();
        }
        
        // SDK调用：关闭客户端服务
        if (clientService != null) {
            try {
                clientService.close();
            } catch (Exception e) {
                log.warn("关闭客户端服务时出错", e);
            }
        }
        
        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("线程池未在5秒内关闭，强制关闭");
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    log.error("线程池无法关闭");
                }
            }
        } catch (InterruptedException e) {
            log.warn("等待线程池关闭时被中断", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.debug("应用已关闭");
    }

    @Override
    public void stop() {
        shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
