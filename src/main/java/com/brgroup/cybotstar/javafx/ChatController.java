package com.brgroup.cybotstar.javafx;

import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.javafx.ui.UiAdapter;
import com.brgroup.cybotstar.javafx.ui.YamlConfigDialog;
import com.brgroup.cybotstar.javafx.ui.MessageBubbleFactory;
import com.brgroup.cybotstar.javafx.util.UIUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Chat Controller
 * 处理 JavaFX 界面的交互逻辑
 * 
 * 通过 ChatApp 访问服务层（ConfigService、UnifiedClientService、MessageService等）
 * 实现 UiAdapter 接口，将UI更新逻辑封装在接口实现中
 * 保持 UI 层与业务逻辑层的分离
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ChatController implements UiAdapter {

    private ChatApp app;
    private YamlConfigDialog yamlConfigDialog;

    @FXML
    private Button btnConnect;

    @FXML
    private Button btnDisconnect;

    @FXML
    private Button btnSend;

    @FXML
    private Button btnStop;

    @FXML
    private ComboBox<String> cmbTemperature;

    @FXML
    private ComboBox<String> cmbMaxTokens;

    @FXML
    private ComboBox<String> cmbConfigProfile;

    @FXML
    private TextArea txtInput;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private VBox messagesContainer;

    @FXML
    private TextArea debugWebSocket;

    @FXML
    private TextArea debugRobotKey;

    @FXML
    private TextArea debugRobotToken;

    @FXML
    private TextArea debugUsername;

    @FXML
    private Label connectionIndicator;

    @FXML
    private Label connectionStatus;

    // 是否正在流式输出（通过 setStreaming() 方法被外部使用）
    private boolean streaming = false;

    /**
     * 初始化方法，在 FXML 加载后自动调用
     */
    @FXML
    public void initialize() {
        // 初始化温度 ComboBox
        cmbTemperature.setItems(FXCollections.observableArrayList("0.1", "0.3", "0.5", "0.7", "1.0"));
        cmbTemperature.setValue("0.7");

        // 初始化 Max Tokens ComboBox
        cmbMaxTokens.setItems(FXCollections.observableArrayList(ChatConstants.MAX_TOKENS_OPTIONS));
        cmbMaxTokens.setValue("5000");

        // 初始化连接状态
        updateConnectionStatus(false);

        // 设置输入框的键盘事件：Enter发送，Ctrl+Enter换行
        setupInputKeyboardEvents();

        // 设置滚动条滚动增量，增大鼠标滚轮一次性滚动的行数
        setupScrollPaneIncrement();
    }

    /**
     * 设置滚动条的滚动增量（全局配置）
     * 通过 ScrollEvent 处理器直接控制滚动速度
     */
    private void setupScrollPaneIncrement() {
        // 使用Platform.runLater确保Scene已完全初始化
        Platform.runLater(() -> {
            Scene scene = txtInput != null ? txtInput.getScene() : null;
            if (scene == null || scene.getRoot() == null) {
                return;
            }

            // 设置所有 ScrollPane 的滚动事件处理器
            scene.getRoot().lookupAll(".scroll-pane").forEach(node -> {
                if (node instanceof ScrollPane scrollPane) {
                    // 捕获滚动事件，放大滚动幅度
                    scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
                        if (event.getDeltaY() != 0) {
                            // 放大滚动幅度 4 倍
                            double deltaY = event.getDeltaY() * 4;
                            scrollPane.setVvalue(scrollPane.getVvalue() - deltaY / scrollPane.getContent().getBoundsInLocal().getHeight());
                            event.consume();
                        }
                    });
                }
            });

            // 同时设置 ScrollBar 的增量作为备用
            scene.getRoot().lookupAll(".scroll-bar:vertical").forEach(node -> {
                if (node instanceof ScrollBar scrollBar) {
                    scrollBar.setUnitIncrement(50);
                    scrollBar.setBlockIncrement(200);
                }
            });
        });
    }

    /**
     * 设置输入框的键盘事件
     * Enter: 发送消息
     * Ctrl+Enter (或 Cmd+Enter on Mac): 换行
     */
    private void setupInputKeyboardEvents() {
        if (txtInput != null) {
            txtInput.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    boolean isCtrlDown = event.isControlDown() || event.isMetaDown(); // 支持Mac的Command键

                    if (!isCtrlDown) {
                        // Enter键（未按Ctrl）: 发送消息
                        event.consume(); // 阻止默认的换行行为
                        if (!btnSend.isDisable() && !txtInput.getText().trim().isEmpty()) {
                            onSendClick();
                        }
                    }
                    // Ctrl+Enter: 允许默认行为（换行），不处理
                }
            });
        }
    }

    /**
     * 配置文件选择改变事件
     */
    @FXML
    private void onConfigProfileChanged() {
        String selectedProfile = cmbConfigProfile.getValue();
        if (selectedProfile != null && app != null) {
            app.loadConfigProfile(selectedProfile);
        }
    }

    /**
     * 设置配置文件列表
     */
    public void setConfigProfiles(List<String> profiles, String defaultProfile) {
        cmbConfigProfile.setItems(FXCollections.observableArrayList(profiles));
        if (defaultProfile != null && profiles.contains(defaultProfile)) {
            cmbConfigProfile.setValue(defaultProfile);
        } else if (!profiles.isEmpty()) {
            cmbConfigProfile.setValue(profiles.get(0));
        }
    }

    /**
     * 获取当前选择的配置文件
     */
    public String getSelectedConfigProfile() {
        return cmbConfigProfile.getValue();
    }

    /**
     * 更新连接状态显示
     */
    public void updateConnectionStatus(boolean connected) {
        if (connected) {
            connectionIndicator.getStyleClass().add("connected");
            connectionStatus.getStyleClass().add("connected");
            connectionStatus.setText("已连接");
            // 更新按钮状态：连接按钮禁用，断开按钮启用
            btnConnect.setText("已连接");
            btnConnect.setDisable(true);
            btnDisconnect.setDisable(false);
            btnSend.setDisable(false);
        } else {
            connectionIndicator.getStyleClass().removeAll("connected");
            connectionStatus.getStyleClass().removeAll("connected");
            connectionStatus.setText("未连接");
            // 更新按钮状态：连接按钮启用，断开按钮禁用
            btnConnect.setText("连接");
            btnConnect.setDisable(false);
            btnDisconnect.setDisable(true);
            btnSend.setDisable(true);
        }
    }

    /**
     * 填充调试信息
     */
    public void setDebugInfo(String webSocket, String robotKey, String robotToken, String username) {
        debugWebSocket.setText(webSocket);
        debugRobotKey.setText(robotKey);
        debugRobotToken.setText(robotToken);
        debugUsername.setText(username);
    }

    public void setApp(ChatApp app) {
        this.app = app;
        this.yamlConfigDialog = new YamlConfigDialog(app);
    }

    /**
     * 连接服务器
     */
    @FXML
    private void onConnectClick() {
        btnConnect.setDisable(true);
        btnConnect.setText("连接中...");

        app.connect()
                .thenAccept(v -> Platform.runLater(() -> {
                    btnConnect.setText("已连接");
                    btnConnect.setDisable(true);
                    btnDisconnect.setDisable(false);
                    btnSend.setDisable(false);
                    updateConnectionStatus(true);
                    log.debug("连接成功");
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        btnConnect.setText("连接");
                        btnConnect.setDisable(false);
                        updateConnectionStatus(false);
                        app.showError("连接失败", e.getMessage());
                        log.error("连接失败", e);
                    });
                    return null;
                });
    }

    /**
     * 断开连接
     */
    @FXML
    private void onDisconnectClick() {
        app.disconnect();
        // 注意：UI状态和系统消息会由 onConnectionStateChanged 回调自动更新
        // 这里只需要更新按钮状态
        btnConnect.setText("连接");
        btnConnect.setDisable(false);
        btnDisconnect.setDisable(true);
        btnSend.setDisable(true);
        updateConnectionStatus(false);
    }

    /**
     * 发送消息
     */
    @FXML
    private void onSendClick() {
        String question = txtInput.getText().trim();
        if (question.isEmpty()) {
            app.addSystemMessage("请输入问题");
            return;
        }

        // 清空输入框
        txtInput.clear();
        // 重置TextArea高度
        txtInput.setPrefRowCount(1);

        // 获取模型参数（使用ChatApp的统一方法）
        ModelOptions modelOptions = app.buildModelParamsFromUI();

        // 标记为流式输出中（会更新按钮状态）
        setStreaming(true);

        // 发送消息（流完成时会在 ChatApp.sendMessage() 中自动重置状态）
        app.sendMessage(question, modelOptions);
    }

    /**
     * 停止流式输出
     */
    @FXML
    private void onStopClick() {
        setStreaming(false);
        app.addSystemMessage("已停止流式输出");
    }

    /**
     * 设置流式输出状态（线程安全）
     */
    @Override
    public void setStreaming(boolean streaming) {
        Platform.runLater(() -> {
            this.streaming = streaming;
            // 更新按钮状态
            btnSend.setDisable(streaming);
            btnStop.setDisable(!streaming);
        });
    }

    /**
     * 构建模型参数（从UI获取）
     */
    public ModelOptions buildModelParams() {
        Double temperature = parseDouble(cmbTemperature != null ? cmbTemperature.getValue() : null, 
                ChatConstants.DEFAULT_TEMPERATURE);
        Integer maxTokens = parseInt(cmbMaxTokens != null ? cmbMaxTokens.getValue() : null, 
                ChatConstants.DEFAULT_MAX_TOKENS);
        return ModelOptions.builder()
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * 解析Double值，失败时返回默认值
     */
    private Double parseDouble(String value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析Integer值，失败时返回默认值
     */
    private Integer parseInt(String value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 清空消息
     */
    public void clearMessages() {
        if (messagesContainer != null) {
            messagesContainer.getChildren().clear();
        }
    }

    /**
     * 管理 YAML 配置
     */
    @FXML
    private void onManageYamlClick() {
        if (yamlConfigDialog != null) {
            yamlConfigDialog.showManageDialog();
            // 更新下拉框
            List<String> configProfiles = app.getConfigProfileList();
            setConfigProfiles(configProfiles, app.getCurrentConfigProfile());
        }
    }

    /**
     * 保存配置
     */
    @FXML
    private void onSaveConfigClick() {
        String currentProfile = cmbConfigProfile.getValue();
        if (currentProfile == null) {
            app.showError("错误", "请先选择配置文件");
            return;
        }

        // 弹出对话框询问是覆盖还是新增
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("保存配置");
        alert.setHeaderText("选择保存方式");
        alert.setContentText("覆盖当前配置 '" + currentProfile + "' 还是创建新配置？");

        ButtonType overwriteButton = new ButtonType("覆盖");
        ButtonType newButton = new ButtonType("新增");
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(overwriteButton, newButton, cancelButton);

        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == overwriteButton) {
                // 覆盖当前配置
                app.saveConfig(
                        currentProfile,
                        debugWebSocket.getText(),
                        debugRobotKey.getText(),
                        debugRobotToken.getText(),
                        debugUsername.getText(),
                        false);
            } else if (buttonType == newButton) {
                // 弹出输入框让用户输入新配置名称
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("新建配置");
                dialog.setHeaderText("请输入新配置名称");
                dialog.setContentText("配置名称:");

                dialog.showAndWait().ifPresent(newProfileName -> {
                    if (!newProfileName.trim().isEmpty()) {
                        app.saveConfig(
                                newProfileName.trim(),
                                debugWebSocket.getText(),
                                debugRobotKey.getText(),
                                debugRobotToken.getText(),
                                debugUsername.getText(),
                                true);
                    }
                });
            }
        });
    }

    /**
     * 清空对话
     */
    @FXML
    private void onClearClick() {
        app.clearMessages();
        // 重新添加欢迎消息
        addWelcomeMessage();
    }

    /**
     * 添加欢迎消息
     */
    private void addWelcomeMessage() {
        VBox welcome = new VBox(12);
        welcome.setAlignment(Pos.CENTER);
        welcome.getStyleClass().add("welcome-container");

        Label title = new Label("有什么可以帮您的？");
        title.getStyleClass().add("welcome-title");

        Label subtitle = new Label("输入问题后按 Enter 发送");
        subtitle.getStyleClass().add("welcome-subtitle");

        welcome.getChildren().addAll(title, subtitle);
        messagesContainer.getChildren().add(welcome);
    }

    /**
     * 添加消息到界面（异步版本，可在任何线程调用）
     */
    @Override
    public void addMessage(String role, String content, boolean isStreaming) {
        Platform.runLater(() -> addMessageSync(role, content, isStreaming));
    }

    /**
     * 添加消息到界面（同步版本，必须在JavaFX线程中调用）
     */
    public void addMessageSync(String role, String content, boolean isStreaming) {
        addMessageBubble(role, content, isStreaming);
    }

    /**
     * 添加AI消息占位（用于流式输出）
     * 注意：此方法必须在JavaFX线程中调用
     * 
     * @return 返回消息气泡的引用，用于后续更新内容
     */
    public VBox addAssistantMessagePlaceholder() {
        return addMessageBubble("assistant", "", true);
    }

    /**
     * 添加消息气泡（内部辅助方法）
     */
    private VBox addMessageBubble(String role, String content, boolean isStreaming) {
        VBox bubble = MessageBubbleFactory.createMessageBubble(role, content, isStreaming, messagesContainer);
        HBox wrapper = MessageBubbleFactory.createMessageWrapper(role, bubble);
        messagesContainer.getChildren().add(wrapper);
        UIUtils.scrollToBottom(scrollPane);
        return bubble;
    }

    /**
     * 更新消息气泡的内容（异步版本）
     */
    public void updateMessageContent(VBox bubble, String content, boolean isStreaming) {
        Platform.runLater(() -> updateMessageContentSync(bubble, content, isStreaming));
    }

    /**
     * 更新消息气泡的内容（同步版本，必须在 JavaFX 线程中调用）
     */
    public void updateMessageContentSync(VBox bubble, String content, boolean isStreaming) {
        if (bubble == null) {
            // 如果气泡为null，直接添加新消息
            addMessageSync("assistant", content, isStreaming);
            return;
        }
        MessageBubbleFactory.updateMessageContent(bubble, content, isStreaming, messagesContainer);
        UIUtils.scrollToBottom(scrollPane);
    }


    // ========== Getter 方法 ==========

    public VBox getMessagesContainer() {
        return messagesContainer;
    }

    public ScrollPane getScrollPane() {
        return scrollPane;
    }

    public Button getBtnConnect() {
        return btnConnect;
    }

    public Button getBtnDisconnect() {
        return btnDisconnect;
    }

    public Button getBtnSend() {
        return btnSend;
    }

    public Button getBtnStop() {
        return btnStop;
    }

    public ComboBox<String> getCmbTemperature() {
        return cmbTemperature;
    }

    public ComboBox<String> getCmbMaxTokens() {
        return cmbMaxTokens;
    }
}
