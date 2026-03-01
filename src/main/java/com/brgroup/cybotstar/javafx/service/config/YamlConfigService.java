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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * YAML配置服务实现
 * 统一管理配置的加载、保存和profile管理
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class YamlConfigService implements ConfigService {

    private final YamlConfigManager yamlConfigManager;
    private String currentConfigProfile = ChatConstants.DEFAULT_CONFIG_PROFILE;
    private final Runnable onConfigChangedCallback;

    public YamlConfigService(Runnable onConfigChangedCallback) {
        this.yamlConfigManager = new YamlConfigManager();
        this.onConfigChangedCallback = onConfigChangedCallback;
    }

    @Override
    public AgentConfig loadAgentConfig(String profileName) {
        Properties props = loadConfigFromFile(profileName);

        String url = getConfigValue(props, ChatConstants.CONFIG_KEY_URL, ChatConstants.DEFAULT_WS_URL);
        String httpUrl = getConfigValue(props, ChatConstants.CONFIG_KEY_HTTP_URL, ChatConstants.DEFAULT_HTTP_URL);
        String robotKey = getConfigValue(props, ChatConstants.CONFIG_KEY_ROBOT_KEY, ChatConstants.DEFAULT_ROBOT_KEY);
        String robotToken = getConfigValue(props, ChatConstants.CONFIG_KEY_ROBOT_TOKEN,
                ChatConstants.DEFAULT_ROBOT_TOKEN);
        String username = getConfigValue(props, ChatConstants.CONFIG_KEY_USERNAME, ChatConstants.DEFAULT_USERNAME);

        // Debug: Log configuration (mask sensitive data)
        log.debug("加载配置 [{}] - URL: {}, HttpUrl: {}, RobotKey: {}..., RobotToken: {}..., Username: {}",
                profileName,
                url,
                httpUrl,
                robotKey != null && robotKey.length() > 10 ? robotKey.substring(0, 10) + "..." : "null",
                robotToken != null && robotToken.length() > 10 ? robotToken.substring(0, 10) + "..." : "null",
                username);

        // 使用新配置格式：agent, websocket, http, log 四个嵌套配置
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

    @Override
    public FlowConfig loadFlowConfig(String profileName) {
        FlowConfig flowConfig = FlowConfigLoader.loadFromYaml(profileName);
        return flowConfig;
    }

    @Override
    public boolean isFlowMode(String profileName) {
        Map<String, Map<String, Object>> profiles = loadProfilesFromYaml();
        if (profiles != null && profiles.containsKey(profileName)) {
            Map<String, Object> profileConfig = profiles.get(profileName);
            // 新格式：profileConfig 包含 "flow" 键表示 Flow 模式，包含 "agent" 键表示 Agent 模式
            return profileConfig.containsKey("flow") && !profileConfig.containsKey("agent");
        }
        return false;
    }

    @Override
    public List<String> getConfigProfileList() {
        Set<String> profilesSet = new LinkedHashSet<>(); // 使用LinkedHashSet保持顺序且去重

        // 1. 先添加用户自定义的properties文件
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

        // 2. 添加YAML中的默认配置（如果不存在于properties文件中）
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

        // 3. 如果列表为空，初始化默认配置
        if (profilesSet.isEmpty()) {
            initializeDefaultConfigs();
            return getConfigProfileList();
        }

        return new ArrayList<>(profilesSet);
    }

    @Override
    public String getCurrentConfigProfile() {
        return currentConfigProfile;
    }

    @Override
    public void setCurrentConfigProfile(String profileName) {
        this.currentConfigProfile = profileName;
    }

    @Override
    public void saveConfig(String profileName, String url, String robotKey, String robotToken, String username,
                          boolean isNew) {
        Properties props = new Properties();
        props.setProperty(ChatConstants.CONFIG_KEY_URL, url);
        // 从WebSocket URL提取HTTP URL
        String httpUrl = extractHttpUrlFromWebSocketUrl(url);
        props.setProperty(ChatConstants.CONFIG_KEY_HTTP_URL, httpUrl);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_KEY, robotKey);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_TOKEN, robotToken);
        props.setProperty(ChatConstants.CONFIG_KEY_USERNAME, username);

        File configFile = getConfigFile(profileName);
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "CybotStar Configuration - " + profileName);
            log.debug("配置已保存到: {}", configFile.getAbsolutePath());

            if (onConfigChangedCallback != null) {
                Platform.runLater(() -> {
                    if (isNew) {
                        // 如果是新配置，更新配置文件列表并切换到新配置
                        currentConfigProfile = profileName;
                    }
                    // 如果保存的是当前配置，触发配置变更回调
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

    @Override
    public void loadConfigProfile(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            return;
        }
        currentConfigProfile = profileName.trim();
        if (onConfigChangedCallback != null) {
            onConfigChangedCallback.run();
        }
    }

    @Override
    public void initializeDefaultConfigs() {
        try {
            // 从YAML文件读取配置
            Map<String, Map<String, Object>> profiles = loadProfilesFromYaml();

            if (profiles == null || profiles.isEmpty()) {
                log.warn("无法从YAML文件加载配置，使用默认值");
                return;
            }

            // 为每个配置profile创建properties文件（仅当文件不存在时）
            for (Map.Entry<String, Map<String, Object>> entry : profiles.entrySet()) {
                String profileName = entry.getKey();
                Map<String, Object> profileConfig = entry.getValue();

                File configFile = getConfigFile(profileName);
                // 如果用户已经自定义了配置，则不覆盖
                if (configFile.exists()) {
                    log.debug("配置文件已存在，跳过初始化: {}", profileName);
                    continue;
                }

                // 判断是 agent 还是 flow
                String configKey = null;
                if (profileConfig.containsKey("agent")) {
                    configKey = "agent";
                } else if (profileConfig.containsKey("flow")) {
                    configKey = "flow";
                }
                
                if (configKey == null) {
                    log.warn("配置 profile {} 中未找到 agent 或 flow 配置，跳过", profileName);
                    continue;
                }
                
                Map<String, Object> config = getMapValue(profileConfig, configKey);

                // 提取配置值，使用默认值
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

    @Override
    public Map<String, Object> getYamlProfile(String profileName) {
        return yamlConfigManager.getProfile(profileName);
    }

    @Override
    public List<String> getAllYamlProfileNames() {
        return yamlConfigManager.getAllProfileNames();
    }

    @Override
    public boolean addYamlProfile(String profileName, String robotKey, String robotToken, String username,
                                 String webSocketUrl, String httpUrl, boolean isAgent) {
        try {
            Map<String, Object> profileConfig;
            if (isAgent) {
                profileConfig = yamlConfigManager.createAgentProfile(robotKey, robotToken, username, webSocketUrl,
                        httpUrl);
            } else {
                // Flow 类型需要 flow-uuid，这里先使用空字符串，用户可以在 UI 中编辑
                profileConfig = yamlConfigManager.createFlowProfile(robotKey, robotToken, username, webSocketUrl,
                        httpUrl, "");
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

    @Override
    public boolean updateYamlProfile(String profileName, String robotKey, String robotToken, String username,
                                    String webSocketUrl, String httpUrl) {
        try {
            Map<String, Object> existingProfile = yamlConfigManager.getProfile(profileName);
            if (existingProfile == null) {
                return false;
            }

            // 判断是 agent 还是 flow 类型
            boolean isAgent = existingProfile.containsKey("agent");
            String configKey = isAgent ? "agent" : "flow";

            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) existingProfile.get(configKey);
            if (config == null) {
                config = new LinkedHashMap<>();
                existingProfile.put(configKey, config);
            }

            // 更新 credentials
            @SuppressWarnings("unchecked")
            Map<String, Object> credentials = (Map<String, Object>) config.getOrDefault("credentials",
                    new LinkedHashMap<>());
            credentials.put("robot-key", robotKey);
            credentials.put("robot-token", robotToken);
            credentials.put("username", username);
            config.put("credentials", credentials);

            // 更新 websocket
            @SuppressWarnings("unchecked")
            Map<String, Object> websocket = (Map<String, Object>) config.getOrDefault("websocket",
                    new LinkedHashMap<>());
            websocket.put("url", webSocketUrl);
            config.put("websocket", websocket);

            // 更新 http
            @SuppressWarnings("unchecked")
            Map<String, Object> http = (Map<String, Object>) config.getOrDefault("http",
                    new LinkedHashMap<>());
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

    @Override
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

    @Override
    public YamlConfigManager getYamlConfigManager() {
        return yamlConfigManager;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取配置值（优先级：Properties > 默认值）
     */
    private String getConfigValue(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return defaultValue;
    }

    /**
     * 将YAML配置转换为Properties
     *
     * @param config YAML配置Map
     * @param props Properties对象，用于存储转换结果
     */
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

    /**
     * 从WebSocket URL提取HTTP URL
     *
     * @param wsUrl WebSocket URL
     * @return HTTP URL
     */
    private static String extractHttpUrlFromWebSocketUrl(String wsUrl) {
        if (wsUrl == null || wsUrl.isEmpty()) {
            return wsUrl;
        }
        String httpUrl = wsUrl.replace("wss://", "https://").replace("ws://", "http://");
        int wsIndex = httpUrl.indexOf("/ws/");
        return wsIndex > 0 ? httpUrl.substring(0, wsIndex) : httpUrl;
    }

    /**
     * 从Map中获取字符串值
     *
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * 从Map中获取嵌套的Map值
     *
     * @param map Map对象
     * @param key 键名
     * @return 嵌套的Map，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * 从指定配置文件加载配置
     * 优先级：用户自定义properties文件 > YAML默认配置 > 硬编码默认值
     */
    private Properties loadConfigFromFile(String profileName) {
        Properties props = new Properties();

        // 1. 优先从用户自定义的properties文件读取（如果存在）
        File configFile = getConfigFile(profileName);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                log.debug("从用户配置文件加载配置: {}", configFile.getAbsolutePath());
                return props; // 用户自定义配置优先，直接返回
            } catch (IOException e) {
                log.warn("读取用户配置文件失败: {}", e.getMessage());
            }
        }

        // 2. 如果用户配置文件不存在，尝试从YAML文件读取默认配置
        try {
            Map<String, Map<String, Object>> profiles = loadProfilesFromYaml();
            if (profiles != null && profiles.containsKey(profileName)) {
                Map<String, Object> profileConfig = profiles.get(profileName);
                // 判断是 agent 还是 flow
                String configKey = null;
                if (profileConfig.containsKey("agent")) {
                    configKey = "agent";
                } else if (profileConfig.containsKey("flow")) {
                    configKey = "flow";
                }
                
                if (configKey == null) {
                    log.warn("配置 profile {} 中未找到 agent 或 flow 配置", profileName);
                    return props;
                }
                
                Map<String, Object> config = getMapValue(profileConfig, configKey);
                if (config != null) {
                    convertYamlConfigToProperties(config, props);
                }
                log.debug("从YAML文件加载默认配置: {}", profileName);
                return props;
            }
        } catch (Exception e) {
            log.warn("从YAML文件加载配置失败: {}", e.getMessage());
        }

        // 3. 如果都不存在，返回空Properties，让调用者使用默认值
        return props;
    }

    /**
     * 获取配置文件路径
     */
    private File getConfigFile(String profileName) {
        File configDir = getConfigDir();
        return new File(configDir, profileName + ChatConstants.CONFIG_FILE_EXTENSION);
    }

    /**
     * 获取配置文件目录
     */
    private File getConfigDir() {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ChatConstants.CONFIG_DIR_NAME);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return configDir;
    }

    /**
     * 从YAML文件加载配置profiles
     */
    private Map<String, Map<String, Object>> loadProfilesFromYaml() {
        return yamlConfigManager.loadAllProfiles();
    }

    /**
     * 创建配置文件
     */
    private void createConfigFile(String profileName, String url, String httpUrl, String robotKey, String robotToken,
                                  String username) {
        File configFile = getConfigFile(profileName);
        // 总是覆盖默认配置文件，确保使用最新的真实配置
        Properties props = new Properties();
        props.setProperty(ChatConstants.CONFIG_KEY_URL, url);
        props.setProperty(ChatConstants.CONFIG_KEY_HTTP_URL, httpUrl);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_KEY, robotKey);
        props.setProperty(ChatConstants.CONFIG_KEY_ROBOT_TOKEN, robotToken);
        props.setProperty(ChatConstants.CONFIG_KEY_USERNAME, username);

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "CybotStar Configuration - " + profileName);
            log.debug("创建/更新默认配置文件: {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("创建默认配置文件失败: {}", e.getMessage());
        }
    }
}

