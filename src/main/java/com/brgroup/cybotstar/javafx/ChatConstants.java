package com.brgroup.cybotstar.javafx;

public final class ChatConstants {

    private ChatConstants() {}

    public static final String APP_TITLE = "CybotStar";
    public static final String DEFAULT_CONFIG_PROFILE = "finance-agent";
    public static final String DEFAULT_WS_URL = "wss://www.cybotstar.cn/openapi/v2/ws/dialog/";
    public static final String DEFAULT_HTTP_URL = "https://www.cybotstar.cn/openapi/v2/";
    public static final String DEFAULT_ROBOT_KEY = "";
    public static final String DEFAULT_ROBOT_TOKEN = "";
    public static final String DEFAULT_USERNAME = "";

    public static final String CONFIG_KEY_URL = "CYBOTSTAR_URL";
    public static final String CONFIG_KEY_HTTP_URL = "CYBOTSTAR_HTTP_URL";
    public static final String CONFIG_KEY_ROBOT_KEY = "CYBOTSTAR_ROBOT_KEY";
    public static final String CONFIG_KEY_ROBOT_TOKEN = "CYBOTSTAR_ROBOT_TOKEN";
    public static final String CONFIG_KEY_USERNAME = "CYBOTSTAR_USERNAME";

    public static final String CONFIG_TYPE_AGENT = "agent";
    public static final String CONFIG_TYPE_FLOW = "flow";

    public static final String CONFIG_DIR_NAME = ".cybotstar";
    public static final String CONFIG_FILE_EXTENSION = ".properties";

    public static final int WS_TIMEOUT = 10000;
    public static final int WS_MAX_RETRIES = 3;
    public static final boolean WS_AUTO_RECONNECT = true;

    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 5000;
    public static final String[] MAX_TOKENS_OPTIONS = {"5000", "8000", "10000"};

    public static final int WINDOW_WIDTH = 900;
    public static final int WINDOW_HEIGHT = 700;
    public static final int SCROLL_DELAY_MS = 50;
    public static final int MESSAGE_BUBBLE_MAX_WIDTH = 1200;
    public static final double MESSAGE_BUBBLE_WIDTH_RATIO = 0.80;
}

