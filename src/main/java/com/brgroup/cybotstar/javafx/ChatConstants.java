package com.brgroup.cybotstar.javafx;

/**
 * Chat应用常量类
 * 集中管理所有魔法字符串和数字
 *
 * @author zhiyuan.xi
 */
public final class ChatConstants {

    private ChatConstants() {
        // 工具类，禁止实例化
    }

    // ========== 应用信息 ==========
    public static final String APP_VERSION = "1.0.0";
    public static final String APP_TITLE = "CybotStar";

    // ========== 默认配置 ==========
    public static final String DEFAULT_CONFIG_PROFILE = "finance-agent";
    public static final String DEFAULT_WS_URL = "wss://www.cybotstar.cn/openapi/v2/ws/dialog/";
    public static final String DEFAULT_HTTP_URL = "https://www.cybotstar.cn/openapi/v2/";
    public static final String DEFAULT_ROBOT_KEY = "5Gi6hxiS%2BY2sdhddieQJ60ejigw%3D";
    public static final String DEFAULT_ROBOT_TOKEN = "MTc2NjM5MDE5MDgxNQo4ZWtxSTg5RE85ODNpbTRrYlNKY2cwVUYwMzg9";
    public static final String DEFAULT_USERNAME = "zhiyuan.xi";

    // ========== 配置键名 ==========
    public static final String CONFIG_KEY_URL = "CYBOTSTAR_URL";
    public static final String CONFIG_KEY_HTTP_URL = "CYBOTSTAR_HTTP_URL";
    public static final String CONFIG_KEY_ROBOT_KEY = "CYBOTSTAR_ROBOT_KEY";
    public static final String CONFIG_KEY_ROBOT_TOKEN = "CYBOTSTAR_ROBOT_TOKEN";
    public static final String CONFIG_KEY_USERNAME = "CYBOTSTAR_USERNAME";

    // ========== 配置类型 ==========
    public static final String CONFIG_TYPE_AGENT = "agent";
    public static final String CONFIG_TYPE_FLOW = "flow";

    // ========== 配置文件 ==========
    public static final String CONFIG_DIR_NAME = ".cybotstar";
    public static final String CONFIG_FILE_EXTENSION = ".properties";

    // ========== WebSocket配置默认值 ==========
    public static final int WS_TIMEOUT = 10000;
    public static final int WS_MAX_RETRIES = 3;
    public static final int WS_RETRY_INTERVAL = 1000;
    public static final int WS_HEARTBEAT_INTERVAL = 30000;
    public static final boolean WS_AUTO_RECONNECT = true;

    // ========== 模型参数默认值 ==========
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 5000;
    public static final String[] TEMPERATURE_OPTIONS = {"0.1", "0.3", "0.5", "0.7", "1.0"};
    public static final String[] MAX_TOKENS_OPTIONS = {"5000", "8000", "10000"};

    // ========== UI尺寸 ==========
    public static final int WINDOW_WIDTH = 900;
    public static final int WINDOW_HEIGHT = 700;
    public static final int SCROLL_DELAY_MS = 50;
    public static final int MESSAGE_MAX_WIDTH = 700;
    public static final int MESSAGE_BUBBLE_MAX_WIDTH = 1200;
    public static final double MESSAGE_BUBBLE_WIDTH_RATIO = 0.80;

    // ========== 消息角色 ==========
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    // ========== Session ID前缀 ==========
    public static final String SESSION_ID_FLOW_PREFIX = "flow-";
}

