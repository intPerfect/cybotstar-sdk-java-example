package com.brgroup.cybotstar.javafx.ui;

import com.brgroup.cybotstar.javafx.ChatApp;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML配置对话框
 * 提供YAML配置的添加、编辑、删除等对话框功能
 *
 * @author zhiyuan.xi
 */
public class YamlConfigDialog {

    private final ChatApp app;

    public YamlConfigDialog(ChatApp app) {
        this.app = app;
    }

    /**
     * 显示管理YAML配置对话框
     */
    public void showManageDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("管理 YAML 配置");
        dialog.setHeaderText("配置文件管理");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // 配置列表
        Label listLabel = new Label("配置列表:");
        ListView<String> configList = new ListView<>();
        List<String> profiles = app.getAllYamlProfileNames();
        configList.getItems().addAll(profiles);
        configList.setPrefHeight(200);

        // 按钮区域
        HBox buttonBox = new HBox(10);
        Button btnAdd = new Button("添加");
        Button btnEdit = new Button("编辑");
        Button btnDelete = new Button("删除");
        Button btnRefresh = new Button("刷新");

        buttonBox.getChildren().addAll(btnAdd, btnEdit, btnDelete, btnRefresh);

        content.getChildren().addAll(listLabel, configList, buttonBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        // 添加按钮事件
        btnAdd.setOnAction(e -> {
            showAddDialog();
            refreshList(configList);
        });

        btnEdit.setOnAction(e -> {
            String selected = configList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditDialog(selected);
                refreshList(configList);
            } else {
                app.showError("错误", "请先选择一个配置");
            }
        });

        btnDelete.setOnAction(e -> {
            String selected = configList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showDeleteConfirmDialog(selected, configList);
            } else {
                app.showError("错误", "请先选择一个配置");
            }
        });

        btnRefresh.setOnAction(e -> refreshList(configList));

        dialog.showAndWait();
    }

    /**
     * 显示添加配置对话框
     */
    private void showAddDialog() {
        Dialog<Map<String, String>> dialog = createProfileDialog("添加配置", null);
        dialog.showAndWait().ifPresent(result -> {
            String profileName = result.get("profileName");
            if (profileName != null && !profileName.trim().isEmpty()) {
                app.addYamlProfile(
                        profileName.trim(),
                        result.get("robotKey"),
                        result.get("robotToken"),
                        result.get("username"),
                        result.get("webSocketUrl"),
                        result.get("httpUrl"),
                        "agent".equals(result.get("type"))
                );
            }
        });
    }

    /**
     * 显示编辑配置对话框
     */
    private void showEditDialog(String profileName) {
        Map<String, Object> profile = app.getYamlProfile(profileName);
        if (profile == null) {
            app.showError("错误", "配置不存在");
            return;
        }

        // 提取配置信息
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) (profile.containsKey("agent") 
                ? profile.get("agent") : profile.get("flow"));
        if (config == null) {
            app.showError("错误", "配置格式错误");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = (Map<String, Object>) config.get("credentials");
        @SuppressWarnings("unchecked")
        Map<String, Object> websocket = (Map<String, Object>) config.get("websocket");
        @SuppressWarnings("unchecked")
        Map<String, Object> http = (Map<String, Object>) config.get("http");

        String robotKey = credentials != null ? (String) credentials.get("robot-key") : "";
        String robotToken = credentials != null ? (String) credentials.get("robot-token") : "";
        String username = credentials != null ? (String) credentials.get("username") : "";
        String webSocketUrl = websocket != null ? (String) websocket.get("url") : "";
        String httpUrl = http != null ? (String) http.get("url") : "";

        Dialog<Map<String, String>> dialog = createProfileDialog("编辑配置", profileName);
        setDialogValues(dialog, profileName, robotKey, robotToken, username, webSocketUrl, httpUrl);

        dialog.showAndWait().ifPresent(result -> {
            String newProfileName = result.get("profileName");
            if (newProfileName != null && !newProfileName.trim().isEmpty()) {
                if (!newProfileName.equals(profileName)) {
                    // 名称改变，先删除旧的，再添加新的
                    app.deleteYamlProfile(profileName);
                    app.addYamlProfile(
                            newProfileName.trim(),
                            result.get("robotKey"),
                            result.get("robotToken"),
                            result.get("username"),
                            result.get("webSocketUrl"),
                            result.get("httpUrl"),
                            profile.containsKey("agent")
                    );
                } else {
                    // 更新现有配置
                    app.updateYamlProfile(
                            profileName,
                            result.get("robotKey"),
                            result.get("robotToken"),
                            result.get("username"),
                            result.get("webSocketUrl"),
                            result.get("httpUrl")
                    );
                }
            }
        });
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(String profileName, ListView<String> configList) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("删除配置");
        confirm.setContentText("确定要删除配置 '" + profileName + "' 吗？");
        confirm.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                app.deleteYamlProfile(profileName);
                refreshList(configList);
                // 更新下拉框
                app.loadConfigProfile(app.getCurrentConfigProfile());
            }
        });
    }

    /**
     * 创建配置对话框
     */
    private Dialog<Map<String, String>> createProfileDialog(String title, String defaultProfileName) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("配置信息");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Profile 名称
        Label profileNameLabel = new Label("配置名称:");
        TextField profileNameField = new TextField(defaultProfileName);
        profileNameField.setId("profileName");
        profileNameField.setDisable(defaultProfileName != null);

        // 类型选择
        Label typeLabel = new Label("配置类型:");
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("agent", "flow");
        typeCombo.setValue("agent");

        // Robot Key
        Label robotKeyLabel = new Label("Robot Key:");
        TextArea robotKeyField = new TextArea();
        robotKeyField.setId("robotKey");
        robotKeyField.setPrefRowCount(2);

        // Robot Token
        Label robotTokenLabel = new Label("Robot Token:");
        TextArea robotTokenField = new TextArea();
        robotTokenField.setId("robotToken");
        robotTokenField.setPrefRowCount(2);

        // Username
        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setId("username");

        // WebSocket URL
        Label webSocketUrlLabel = new Label("WebSocket URL:");
        TextField webSocketUrlField = new TextField();
        webSocketUrlField.setId("webSocketUrl");

        // HTTP URL
        Label httpUrlLabel = new Label("HTTP URL:");
        TextField httpUrlField = new TextField();
        httpUrlField.setId("httpUrl");

        content.getChildren().addAll(
                profileNameLabel, profileNameField,
                typeLabel, typeCombo,
                robotKeyLabel, robotKeyField,
                robotTokenLabel, robotTokenField,
                usernameLabel, usernameField,
                webSocketUrlLabel, webSocketUrlField,
                httpUrlLabel, httpUrlField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 设置结果转换器
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                Map<String, String> result = new HashMap<>();
                result.put("profileName", profileNameField.getText());
                result.put("robotKey", robotKeyField.getText());
                result.put("robotToken", robotTokenField.getText());
                result.put("username", usernameField.getText());
                result.put("webSocketUrl", webSocketUrlField.getText());
                result.put("httpUrl", httpUrlField.getText());
                result.put("type", typeCombo.getValue());
                return result;
            }
            return null;
        });

        return dialog;
    }

    /**
     * 设置对话框的值
     */
    private void setDialogValues(Dialog<Map<String, String>> dialog, String profileName,
                                 String robotKey, String robotToken, String username,
                                 String webSocketUrl, String httpUrl) {
        TextField profileNameField = (TextField) dialog.getDialogPane().lookup("#profileName");
        TextArea robotKeyField = (TextArea) dialog.getDialogPane().lookup("#robotKey");
        TextArea robotTokenField = (TextArea) dialog.getDialogPane().lookup("#robotToken");
        TextField usernameField = (TextField) dialog.getDialogPane().lookup("#username");
        TextField webSocketUrlField = (TextField) dialog.getDialogPane().lookup("#webSocketUrl");
        TextField httpUrlField = (TextField) dialog.getDialogPane().lookup("#httpUrl");

        if (profileNameField != null) profileNameField.setText(profileName);
        if (robotKeyField != null) robotKeyField.setText(robotKey != null ? robotKey : "");
        if (robotTokenField != null) robotTokenField.setText(robotToken != null ? robotToken : "");
        if (usernameField != null) usernameField.setText(username != null ? username : "");
        if (webSocketUrlField != null) webSocketUrlField.setText(webSocketUrl != null ? webSocketUrl : "");
        if (httpUrlField != null) httpUrlField.setText(httpUrl != null ? httpUrl : "");
    }

    /**
     * 刷新配置列表
     */
    private void refreshList(ListView<String> configList) {
        List<String> updatedProfiles = app.getAllYamlProfileNames();
        configList.getItems().clear();
        configList.getItems().addAll(updatedProfiles);
    }
}

