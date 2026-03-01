package com.brgroup.cybotstar.javafx.service.config;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.core.config.CredentialProperties;
import com.brgroup.cybotstar.core.config.HttpProperties;
import com.brgroup.cybotstar.core.config.WebSocketProperties;
import com.brgroup.cybotstar.flow.config.FlowConfig;
import com.brgroup.cybotstar.javafx.ChatConstants;
import com.brgroup.cybotstar.javafx.util.YamlConfigManager;
import com.brgroup.cybotstar.tool.FlowConfigLoader;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

@Slf4j
public class YamlConfigService {

    private final YamlConfigManager yamlConfigManager;
    private String currentConfigProfile = ChatConstants.DEFAULT_CONFIG_PROFILE;
    private final Runnable onConfigChangedCallback;

    public YamlConfigService(Runnable onConfigChangedCallback) {
        this.yamlConfigManager = new YamlConfigManager();
        this.onConfigChangedCallback = onConfigChangedCallback;
    }

    public AgentConfig loadAgentConfig(String profileName) {
        Properties props = loadConfigFromFile(profileName);

        String url = getConfigValue(props, ChatConstants.CONFIG_KEY_URL, ChatConstants.DEFAULT_WS_URL);
        String httpUrl = getConfigValue(props, ChatConstants.CONFIG_KEY_HTTP_URL, ChatConstants.DEFAULT_HTTP_URL);
        String robotKey = getConfigValue(props, ChatConstants.CONFIG_KEY_ROBOT_KEY, ChatConstants.DEFAULT_ROBOT_KEY);
        String robotToken = getConfigValue(props, ChatConstants.CONFIG_KEY_ROBOT_TOKEN, ChatConstants.DEFAULT_ROBOT_TOKEN);
        String username = getConfigValue(props, ChatConstants.CONFIG_KEY_USERNAME, ChatConstants.DEFAULT_USERNAME);

        return AgentConfig.builder()
                .credentials(CredentialProperties.builder()
                        .robotKey(robotKey)
                        .robotToken(robotToken)
                        .username(username)
                        .build())
                .http(HttpProperties.builder()
                        .url(httpUrl)
                        .build())
                .websocket(WebSocketProperties.builder()
                        .url(url)
                        .timeout(ChatConstants.WS_TIMEOUT)
                        .maxRetries(ChatConstants.WS_MAX_RETRIES)
                        .autoReconnect(ChatConstants.WS_AUTO_RECONNECT)
                        .build())
                .build();
    }

    public FlowConfig loadFlowConfig(String profileName) {
        return FlowConfigLoader.loadFromYaml(profileName);
    }

    public boolean isFlowMode(String profileName) {
        Map<String, Map<String, Object>> profiles = loadProfilesFromYaml();
        if (profiles != null && profiles.containsKey(profileName)) {
            Map<String, Object> profileConfig = profiles.get(profileName);
            return profileConfig.containsKey("flow") && !profileConfig.containsKey("agent");
        }
        return false;
    }

    public List<String> getConfigProfileList() {
        Set<String> profilesSet = new LinkedHashSet<>();

        File configDir = getConfigDir();
        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".properties"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".properties")) {
                    profilesSet.add(name.substring(0, name.length() - ".properties".length()));
                }
            }
        }

        try {
            Map<String, Map<String, Object>> yamlProfiles = loadProfilesFromYaml();
            if (yamlProfiles != null) {
                for (String profileName : yamlProfiles.keySet()) {
                    if (!profilesSet.contains(profileName)) {
                        profilesSet.add(profileName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载YAML配置列表失败: {}", e.getMessage());
        }

        if (profilesSet.isEmpty()) {
            initializeDefaultConfigs();
            return getConfigProfileList();
        }

        return new ArrayList<>(profilesSet);
    }

    public String getCurrentConfigProfile() {
        return currentConfigProfile;
    }

    public void setCurrentConfigProfile(String profileName) {
        this.currentConfigProfile = profileName;
    }

    public void saveConfig(String profileName, String url, String robotKey, String robotToken, String username, boolean isNew) {
        Properties props = new Properties();
        props.setProperty(ChatConstants.CONFIG_KEY_URL, url);
        props.setProperty(ChatConstants.CONFIG_KEY_HTTP_URL, extractHttpUrlFromWebSocketUrl(url));
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_KEY, robotKey);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_TOKEN, robotToken);
        props.setProperty(ChatConstants.CONFIG_KEY_USERNAME, username);

        File configFile = getConfigFile(profileName);
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "CybotStar Configuration - " + profileName);

            if (onConfigChangedCallback != null) {
                Platform.runLater(() -> {
                    if (isNew) {
                        currentConfigProfile = profileName;
                    }
                    if (profileName.equals(currentConfigProfile)) {
                        onConfigChangedCallback.run();
                    }
                });
            }
        } catch (IOException e) {
            log.error("保存配置失败", e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }

    public void loadConfigProfile(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            return;
        }
        currentConfigProfile = profileName.trim();
        if (onConfigChangedCallback != null) {
            onConfigChangedCallback.run();
        }
    }

    public void initializeDefaultConfigs() {
        try {
            Map<String, Map<String, Object>> profiles = loadProfilesFromYaml();

            if (profiles == null || profiles.isEmpty()) {
                log.warn("无法从YAML文件加载配置，使用默认值");
                return;
            }

            for (Map.Entry<String, Map<String, Object>> entry : profiles.entrySet()) {
                String profileName = entry.getKey();
                Map<String, Object> profileConfig = entry.getValue();

                File configFile = getConfigFile(profileName);
                if (configFile.exists()) {
                    continue;
                }

                String configKey = profileConfig.containsKey("agent") ? "agent" : 
                                   profileConfig.containsKey("flow") ? "flow" : null;
                
                if (configKey == null) {
                    continue;
                }
                
                Map<String, Object> config = getMapValue(profileConfig, configKey);

                String url = ChatConstants.DEFAULT_WS_URL;
                String httpUrl = ChatConstants.DEFAULT_HTTP_URL;
                String robotKey = "";
                String robotToken = "";
                String username = ChatConstants.DEFAULT_USERNAME;

                if (config != null) {
                    Map<String, Object> websocket = getMapValue(config, "websocket");
                    if (websocket != null) {
                        url = getStringValue(websocket, "url", url);
                    }
                    Map<String, Object> credentials = getMapValue(config, "credentials");
                    if (credentials != null) {
                        robotKey = getStringValue(credentials, "robot-key", "");
                        robotToken = getStringValue(credentials, "robot-token", "");
                        username = getStringValue(credentials, "username", username);
                    }
                    Map<String, Object> http = getMapValue(config, "http");
                    if (http != null) {
                        httpUrl = getStringValue(http, "url", httpUrl);
                    }
                }

                createConfigFile(profileName, url, httpUrl, robotKey, robotToken, username);
            }
        } catch (Exception e) {
            log.error("初始化默认配置文件失败", e);
        }
    }

    public Map<String, Object> getYamlProfile(String profileName) {
        return yamlConfigManager.getProfile(profileName);
    }

    public List<String> getAllYamlProfileNames() {
        return yamlConfigManager.getAllProfileNames();
    }

    public boolean addYamlProfile(String profileName, String robotKey, String robotToken, String username,
                                 String webSocketUrl, String httpUrl, boolean isAgent) {
        try {
            Map<String, Object> profileConfig;
            if (isAgent) {
                profileConfig = yamlConfigManager.createAgentProfile(robotKey, robotToken, username, webSocketUrl, httpUrl);
            } else {
                profileConfig = yamlConfigManager.createFlowProfile(robotKey, robotToken, username, webSocketUrl, httpUrl, "");
            }

            boolean success = yamlConfigManager.saveProfile(profileName, profileConfig);
            if (success && onConfigChangedCallback != null) {
                Platform.runLater(onConfigChangedCallback);
            }
            return success;
        } catch (Exception e) {
            log.error("添加 YAML profile 失败", e);
            return false;
        }
    }

    public boolean updateYamlProfile(String profileName, String robotKey, String robotToken, String username,
                                    String webSocketUrl, String httpUrl) {
        try {
            Map<String, Object> existingProfile = yamlConfigManager.getProfile(profileName);
            if (existingProfile == null) {
                return false;
            }

            boolean isAgent = existingProfile.containsKey("agent");
            String configKey = isAgent ? "agent" : "flow";

            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) existingProfile.getOrDefault(configKey, new LinkedHashMap<>());
            existingProfile.put(configKey, config);

            @SuppressWarnings("unchecked")
            Map<String, Object> credentials = (Map<String, Object>) config.getOrDefault("credentials", new LinkedHashMap<>());
            credentials.put("robot-key", robotKey);
            credentials.put("robot-token", robotToken);
            credentials.put("username", username);
            config.put("credentials", credentials);

            @SuppressWarnings("unchecked")
            Map<String, Object> websocket = (Map<String, Object>) config.getOrDefault("websocket", new LinkedHashMap<>());
            websocket.put("url", webSocketUrl);
            config.put("websocket", websocket);

            @SuppressWarnings("unchecked")
            Map<String, Object> http = (Map<String, Object>) config.getOrDefault("http", new LinkedHashMap<>());
            http.put("url", httpUrl);
            config.put("http", http);

            boolean success = yamlConfigManager.saveProfile(profileName, existingProfile);
            if (success && onConfigChangedCallback != null) {
                Platform.runLater(onConfigChangedCallback);
            }
            return success;
        } catch (Exception e) {
            log.error("更新 YAML profile 失败", e);
            return false;
        }
    }

    public boolean deleteYamlProfile(String profileName) {
        try {
            boolean success = yamlConfigManager.deleteProfile(profileName);
            if (success && onConfigChangedCallback != null) {
                Platform.runLater(onConfigChangedCallback);
            }
            return success;
        } catch (Exception e) {
            log.error("删除 YAML profile 失败", e);
            return false;
        }
    }

    public YamlConfigManager getYamlConfigManager() {
        return yamlConfigManager;
    }

    private String getConfigValue(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private static void convertYamlConfigToProperties(Map<String, Object> config, Properties props) {
        Map<String, Object> websocket = getMapValue(config, "websocket");
        if (websocket != null) {
            props.setProperty(ChatConstants.CONFIG_KEY_URL, getStringValue(websocket, "url", ""));
        }
        Map<String, Object> credentials = getMapValue(config, "credentials");
        if (credentials != null) {
            props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_KEY, getStringValue(credentials, "robot-key", ""));
            props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_TOKEN, getStringValue(credentials, "robot-token", ""));
            props.setProperty(ChatConstants.CONFIG_KEY_USERNAME, getStringValue(credentials, "username", ""));
        }
        Map<String, Object> http = getMapValue(config, "http");
        if (http != null) {
            props.setProperty(ChatConstants.CONFIG_KEY_HTTP_URL, getStringValue(http, "url", ""));
        }
    }

    private static String extractHttpUrlFromWebSocketUrl(String wsUrl) {
        if (wsUrl == null || wsUrl.isEmpty()) {
            return wsUrl;
        }
        String httpUrl = wsUrl.replace("wss://", "https://").replace("ws://", "http://");
        int wsIndex = httpUrl.indexOf("/ws/");
        return wsIndex > 0 ? httpUrl.substring(0, wsIndex) : httpUrl;
    }

    private static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private Properties loadConfigFromFile(String profileName) {
        Properties props = new Properties();

        File configFile = getConfigFile(profileName);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                return props;
            } catch (IOException e) {
                log.warn("读取用户配置文件失败: {}", e.getMessage());
            }
        }

        try {
            Map<String, Map<String, Object>> profiles = loadProfilesFromYaml();
            if (profiles != null && profiles.containsKey(profileName)) {
                Map<String, Object> profileConfig = profiles.get(profileName);
                String configKey = profileConfig.containsKey("agent") ? "agent" : 
                                   profileConfig.containsKey("flow") ? "flow" : null;
                
                if (configKey != null) {
                    Map<String, Object> config = getMapValue(profileConfig, configKey);
                    if (config != null) {
                        convertYamlConfigToProperties(config, props);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从YAML文件加载配置失败: {}", e.getMessage());
        }

        return props;
    }

    private File getConfigFile(String profileName) {
        return new File(getConfigDir(), profileName + ChatConstants.CONFIG_FILE_EXTENSION);
    }

    private File getConfigDir() {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ChatConstants.CONFIG_DIR_NAME);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return configDir;
    }

    private Map<String, Map<String, Object>> loadProfilesFromYaml() {
        return yamlConfigManager.loadAllProfiles();
    }

    private void createConfigFile(String profileName, String url, String httpUrl, String robotKey, String robotToken, String username) {
        File configFile = getConfigFile(profileName);
        Properties props = new Properties();
        props.setProperty(ChatConstants.CONFIG_KEY_URL, url);
        props.setProperty(ChatConstants.CONFIG_KEY_HTTP_URL, httpUrl);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_KEY, robotKey);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_TOKEN, robotToken);
        props.setProperty(ChatConstants.CONFIG_KEY_USERNAME, username);

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "CybotStar Configuration - " + profileName);
        } catch (IOException e) {
            log.warn("创建默认配置文件失败: {}", e.getMessage());
        }
    }
}
