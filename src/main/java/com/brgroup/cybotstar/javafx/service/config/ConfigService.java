package com.brgroup.cybotstar.javafx.service.config;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.flow.config.FlowConfig;
import com.brgroup.cybotstar.javafx.util.YamlConfigManager;

import java.util.List;
import java.util.Map;

/**
 * 配置服务接口
 * 统一管理配置的加载、保存和profile管理
 *
 * @author zhiyuan.xi
 */
public interface ConfigService {

    /**
     * 加载指定profile的Agent配置
     *
     * @param profileName 配置profile名称
     * @return AgentConfig对象
     */
    AgentConfig loadAgentConfig(String profileName);

    /**
     * 加载指定profile的Flow配置
     *
     * @param profileName 配置profile名称
     * @return FlowConfig对象，如果不存在则返回null
     */
    FlowConfig loadFlowConfig(String profileName);

    /**
     * 判断指定profile是否为Flow模式
     *
     * @param profileName 配置profile名称
     * @return true表示Flow模式，false表示Agent模式
     */
    boolean isFlowMode(String profileName);

    /**
     * 获取所有配置profile列表
     *
     * @return profile名称列表
     */
    List<String> getConfigProfileList();

    /**
     * 获取当前配置profile名称
     *
     * @return 当前profile名称
     */
    String getCurrentConfigProfile();

    /**
     * 设置当前配置profile
     *
     * @param profileName profile名称
     */
    void setCurrentConfigProfile(String profileName);

    /**
     * 保存配置到文件
     *
     * @param profileName 配置profile名称
     * @param url WebSocket URL
     * @param robotKey Robot Key
     * @param robotToken Robot Token
     * @param username Username
     * @param isNew 是否为新配置文件
     */
    void saveConfig(String profileName, String url, String robotKey, String robotToken, String username, boolean isNew);

    /**
     * 加载指定配置文件
     *
     * @param profileName profile名称
     */
    void loadConfigProfile(String profileName);

    /**
     * 初始化默认配置文件
     */
    void initializeDefaultConfigs();

    /**
     * 获取YAML profile信息
     *
     * @param profileName profile名称
     * @return profile配置Map
     */
    Map<String, Object> getYamlProfile(String profileName);

    /**
     * 获取所有YAML profile名称
     *
     * @return profile名称列表
     */
    List<String> getAllYamlProfileNames();

    /**
     * 添加新的YAML profile
     *
     * @param profileName profile名称
     * @param robotKey Robot Key
     * @param robotToken Robot Token
     * @param username Username
     * @param webSocketUrl WebSocket URL
     * @param httpUrl HTTP URL
     * @param isAgent 是否为Agent类型
     * @return 是否成功
     */
    boolean addYamlProfile(String profileName, String robotKey, String robotToken, String username,
                          String webSocketUrl, String httpUrl, boolean isAgent);

    /**
     * 更新YAML profile
     *
     * @param profileName profile名称
     * @param robotKey Robot Key
     * @param robotToken Robot Token
     * @param username Username
     * @param webSocketUrl WebSocket URL
     * @param httpUrl HTTP URL
     * @return 是否成功
     */
    boolean updateYamlProfile(String profileName, String robotKey, String robotToken, String username,
                             String webSocketUrl, String httpUrl);

    /**
     * 删除YAML profile
     *
     * @param profileName profile名称
     * @return 是否成功
     */
    boolean deleteYamlProfile(String profileName);

    /**
     * 获取YAML配置管理器
     *
     * @return YamlConfigManager实例
     */
    YamlConfigManager getYamlConfigManager();
}

