package com.brgroup.cybotstar.javafx.util;

import com.brgroup.cybotstar.javafx.ChatConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

/**
 * YAML 配置文件管理工具类
 * 使用 Spring Boot 多配置格式：cybotstar.agents 和 cybotstar.flows
 *
 * @author zhiyuan.xi
 */
@Getter
@Slf4j
public class YamlConfigManager {

    private static final String CONFIG_FILE_NAME = "config-profiles.yml";
    private static final String RESOURCES_PATH = "src" + File.separator + "test" + File.separator + "resources";
    /**
     * -- GETTER --
     *  获取配置文件路径
     */
    private final File configFile;

    public YamlConfigManager() {
        configFile = findConfigFile();
    }

    /**
     * 查找配置文件
     * 优先级：1. 当前目录的resources 2. classpath（复制到resources）
     */
    private File findConfigFile() {
        String userDir = System.getProperty("user.dir");
        File resourcesDir = new File(userDir, RESOURCES_PATH);
        
        // 1. 尝试从resources目录查找
        File configFile = new File(resourcesDir, CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            log.debug("找到配置文件: {}", configFile.getAbsolutePath());
            return configFile;
        }

        // 2. 尝试从classpath读取并复制到resources目录
        try (InputStream inputStream = YamlConfigManager.class.getResourceAsStream("/" + CONFIG_FILE_NAME)) {
            if (inputStream != null) {
                // 确保resources目录存在
                if (!resourcesDir.exists()) {
                    resourcesDir.mkdirs();
                }
                
                // 如果文件不存在，从classpath复制
                if (!configFile.exists()) {
                    try (FileOutputStream fos = new FileOutputStream(configFile)) {
                        inputStream.transferTo(fos);
                    }
                    log.debug("从classpath复制配置文件到: {}", configFile.getAbsolutePath());
                }
                return configFile;
            }
        } catch (IOException e) {
            log.warn("无法从classpath复制配置文件: {}", e.getMessage());
        }

        // 3. 如果都找不到，创建新文件
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs();
        }
        log.warn("配置文件不存在，将使用: {}", configFile.getAbsolutePath());
        return configFile;
    }

    /**
     * 加载所有 profiles
     * 使用 Spring Boot 多配置格式：cybotstar.agents 和 cybotstar.flows
     */
    public Map<String, Map<String, Object>> loadAllProfiles() {
        try {
            if (!configFile.exists()) {
                log.warn("配置文件不存在: {}", configFile.getAbsolutePath());
                return new LinkedHashMap<>();
            }

            Yaml yaml = new Yaml();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                Map<String, Object> data = yaml.load(fis);
                if (data == null) {
                    return new LinkedHashMap<>();
                }

                // 从 Spring Boot 多配置格式加载：cybotstar.agents 和 cybotstar.flows
                Map<String, Object> cybotstar = getMapValue(data, "cybotstar");
                if (cybotstar == null) {
                    log.warn("配置文件中没有找到 cybotstar 配置");
                    return new LinkedHashMap<>();
                }

                Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
                
                // 加载 agents
                Map<String, Object> agents = getMapValue(cybotstar, "agents");
                if (agents != null) {
                    for (Map.Entry<String, Object> entry : agents.entrySet()) {
                        String name = entry.getKey();
                        Object configObj = entry.getValue();
                        if (configObj instanceof Map) {
                            Map<String, Object> profile = new LinkedHashMap<>();
                            profile.put("agent", configObj);
                            profiles.put(name, profile);
                        }
                    }
                }
                
                // 加载 flows
                Map<String, Object> flows = getMapValue(cybotstar, "flows");
                if (flows != null) {
                    for (Map.Entry<String, Object> entry : flows.entrySet()) {
                        String name = entry.getKey();
                        Object configObj = entry.getValue();
                        if (configObj instanceof Map) {
                            Map<String, Object> profile = new LinkedHashMap<>();
                            profile.put("flow", configObj);
                            profiles.put(name, profile);
                        }
                    }
                }
                
                return profiles;
            }
        } catch (Exception e) {
            log.error("加载配置文件失败", e);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 从Map中获取嵌套的Map值
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
     * 获取指定 profile 的配置
     */
    public Map<String, Object> getProfile(String profileName) {
        Map<String, Map<String, Object>> profiles = loadAllProfiles();
        return profiles.get(profileName);
    }

    /**
     * 添加或更新 profile
     */
    public boolean saveProfile(String profileName, Map<String, Object> profileConfig) {
        try {
            Map<String, Map<String, Object>> profiles = loadAllProfiles();
            profiles.put(profileName, profileConfig);

            return saveAllProfiles(profiles);
        } catch (Exception e) {
            log.error("保存 profile 失败: {}", profileName, e);
            return false;
        }
    }

    /**
     * 删除 profile
     */
    public boolean deleteProfile(String profileName) {
        try {
            Map<String, Map<String, Object>> profiles = loadAllProfiles();
            if (profiles.remove(profileName) != null) {
                return saveAllProfiles(profiles);
            }
            return false;
        } catch (Exception e) {
            log.error("删除 profile 失败: {}", profileName, e);
            return false;
        }
    }

    /**
     * 保存所有 profiles 到文件
     * 使用新格式：cybotstar.agents 和 cybotstar.flows
     */
    private boolean saveAllProfiles(Map<String, Map<String, Object>> profiles) {
        try {
            // 确保父目录存在
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 转换为新格式：cybotstar.agents 和 cybotstar.flows
            Map<String, Object> cybotstar = new LinkedHashMap<>();
            Map<String, Object> agents = new LinkedHashMap<>();
            Map<String, Object> flows = new LinkedHashMap<>();

            for (Map.Entry<String, Map<String, Object>> entry : profiles.entrySet()) {
                String profileName = entry.getKey();
                Map<String, Object> profile = entry.getValue();
                
                // 判断是 agent 还是 flow
                if (profile.containsKey("agent")) {
                    agents.put(profileName, profile.get("agent"));
                } else if (profile.containsKey("flow")) {
                    flows.put(profileName, profile.get("flow"));
                }
            }

            if (!agents.isEmpty()) {
                cybotstar.put("agents", agents);
            }
            if (!flows.isEmpty()) {
                cybotstar.put("flows", flows);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cybotstar", cybotstar);

            // 配置 YAML 输出格式
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

            Yaml yaml = new Yaml(options);
            try (FileWriter writer = new FileWriter(configFile)) {
                yaml.dump(data, writer);
            }

            log.debug("配置文件已保存: {}", configFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("保存配置文件失败", e);
            return false;
        }
    }

    /**
     * 创建新的 profile 配置（Agent 类型）
     */
    public Map<String, Object> createAgentProfile(String robotKey, String robotToken, String username,
            String webSocketUrl, String httpUrl) {
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> agent = createBaseConfig(robotKey, robotToken, username, webSocketUrl, httpUrl);
        profile.put(ChatConstants.CONFIG_TYPE_AGENT, agent);
        return profile;
    }

    /**
     * 创建新的 profile 配置（Flow 类型）
     */
    public Map<String, Object> createFlowProfile(String robotKey, String robotToken, String username,
            String webSocketUrl, String httpUrl, String flowUuid) {
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> flowConfig = createBaseConfig(robotKey, robotToken, username, webSocketUrl, httpUrl);
        
        // 添加 flow 配置
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("open-flow-trigger", "direct");
        flow.put("open-flow-uuid", flowUuid);
        flow.put("open-flow-debug", false);
        flowConfig.put("flow", flow);
        
        profile.put(ChatConstants.CONFIG_TYPE_FLOW, flowConfig);
        return profile;
    }

    /**
     * 创建基础配置（Agent和Flow共用）
     */
    private Map<String, Object> createBaseConfig(String robotKey, String robotToken, String username,
            String webSocketUrl, String httpUrl) {
        Map<String, Object> config = new LinkedHashMap<>();

        // credentials
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("robot-key", robotKey);
        credentials.put("robot-token", robotToken);
        credentials.put("username", username);
        config.put("credentials", credentials);

        // websocket
        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("url", webSocketUrl);
        websocket.put("timeout", ChatConstants.WS_TIMEOUT);
        websocket.put("max-retries", ChatConstants.WS_MAX_RETRIES);
        websocket.put("retry-interval", 1000);
        websocket.put("auto-reconnect", ChatConstants.WS_AUTO_RECONNECT);
        websocket.put("heartbeat-interval", 30000);
        config.put("websocket", websocket);

        // http
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("url", httpUrl);
        config.put("http", http);

        // log
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("log-level", "info");
        config.put("log", log);

        return config;
    }

    /**
     * 检查 profile 是否存在
     */
    public boolean profileExists(String profileName) {
        Map<String, Map<String, Object>> profiles = loadAllProfiles();
        return profiles.containsKey(profileName);
    }

    /**
     * 获取所有 profile 名称列表
     */
    public List<String> getAllProfileNames() {
        Map<String, Map<String, Object>> profiles = loadAllProfiles();
        return new ArrayList<>(profiles.keySet());
    }
}

