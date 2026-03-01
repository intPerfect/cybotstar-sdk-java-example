# CybotStar SDK Java Example

CybotStar SDK Java 示例应用 - JavaFX 演示程序

这是一个基于 JavaFX 的图形界面示例应用，展示了如何使用 CybotStar SDK 进行 AI 对话。

## 🚀 快速开始

### 运行应用（最简单，推荐）

项目包含 `ChatAppLauncher` 类，它会**自动检测并配置 JavaFX 模块路径**！

1. 在 IntelliJ IDEA 中打开项目
2. 找到 `ChatAppLauncher.java` 文件
3. 点击类名旁边的绿色运行按钮 ▶️
4. 选择 **"Run 'ChatAppLauncher.main()'"**

### 前置要求

- Java 17 或更高版本
- Maven 3.6 或更高版本
- 已安装并配置好 `cybotstar-spring-boot3-starter` 项目（作为依赖）

### 构建项目

1. **安装依赖项目**

   首先需要将 `cybotstar-spring-boot3-starter` 安装到本地 Maven 仓库：

   ```bash
   cd cybotstar-spring-boot3-starter
   mvn clean install
   ```

2. **构建示例项目**

   ```bash
   cd cybotstar-sdk-java-example
   mvn clean compile
   ```

---

## 📋 项目说明

本项目是 CybotStar SDK 的示例应用，提供了一个完整的 JavaFX 图形界面，用于演示 SDK 的核心功能：

- **Agent 模式对话** - 基于 Agent 的智能对话
- **Flow 模式对话** - 基于 Flow 流程的对话
- **流式消息接收** - 实时接收和显示 AI 回复
- **多配置文件管理** - 支持多个 Agent/Flow 配置切换
- **实时连接状态显示** - 显示 WebSocket 连接状态

## 🏗️ 架构设计

### 整体架构

应用采用**分层架构**设计，清晰分离 UI 层和业务逻辑层：

```
┌─────────────────────────────────────────┐
│           UI 层 (JavaFX)                │
│  ChatController (FXML Controller)       │
│  - 处理用户交互                          │
│  - 更新界面状态                          │
└─────────────────┬───────────────────────┘
                  │ UiAdapter 接口
┌─────────────────▼───────────────────────┐
│         业务逻辑层 (Service)             │
│  - ChatApp (应用主类)                    │
│  - UnifiedClientService (统一客户端)     │
│  - MessageService (消息服务)             │
│  - UnifiedEventHandler (事件处理)       │
│  - ConfigService (配置服务)             │
└─────────────────┬───────────────────────┘
                  │ SDK 调用
┌─────────────────▼───────────────────────┐
│      CybotStar SDK                      │
│  - AgentClient                          │
│  - FlowClient                           │
└─────────────────────────────────────────┘
```

### 核心组件

#### 1. ChatApp（应用主类）

- **职责**：应用入口，负责服务层的初始化和协调
- **功能**：
  - 初始化所有服务（配置服务、客户端服务、消息服务、事件处理器）
  - 加载 FXML 界面并设置控制器
  - 管理应用生命周期（启动、关闭）

#### 2. UnifiedClientService（统一客户端服务）

- **职责**：统一管理 `AgentClient` 和 `FlowClient` 的创建、连接和生命周期
- **设计模式**：工厂模式 + 策略模式
- **核心功能**：
  - 根据配置自动创建 Agent 或 Flow 客户端
  - 统一连接/断开接口，内部根据模式调用不同的实现
  - 管理连接状态并通知 UI 层

**Agent 模式特点**：
- 连接在首次 `send()` 或 `stream()` 调用时自动建立
- 支持流式和非流式消息发送
- 连接状态由 SDK 内部管理

**Flow 模式特点**：
- 需要显式调用 `start()` 建立连接
- 支持复杂的流程控制（节点进入、等待输入、流程结束等）
- 连接状态通过事件回调通知

#### 3. MessageService（消息服务）

- **职责**：统一处理 Agent 和 Flow 模式的消息发送
- **核心功能**：
  - 封装 SDK 调用逻辑（`agentClient.stream()` 或 `flowClient.send()`）
  - 通过 `UiAdapter` 接口更新 UI，不直接依赖 UI 实现
  - 处理流式消息的接收和显示

#### 4. UnifiedEventHandler（统一事件处理器）

- **职责**：统一管理 Agent 和 Flow 模式的各种事件订阅
- **核心功能**：
  - 根据客户端模式动态设置不同的事件处理器
  - Agent 模式：处理原始请求/响应事件
  - Flow 模式：处理连接、消息、等待、结束、错误等多种事件

**Flow 模式事件类型**：
- `onConnected` - 连接建立
- `onDisconnected` - 连接断开
- `onStart` - Flow 启动
- `onNodeEnter` - 节点进入
- `onMessage` - 接收消息（流式）
- `onWaiting` - 等待用户输入
- `onEnd` - Flow 结束
- `onError` - 错误处理
- `onDebug` - 调试信息（需配置开启）
- `onJump` - 流程跳转

#### 5. ConfigService（配置服务）

- **职责**：管理配置文件的加载和切换
- **核心功能**：
  - 从 YAML 文件加载 Agent/Flow 配置
  - 支持多个配置 Profile 切换
  - 提供配置验证和错误处理

#### 6. ChatController（UI 控制器）

- **职责**：处理 JavaFX 界面的交互逻辑
- **核心功能**：
  - 实现 `UiAdapter` 接口，提供 UI 更新方法
  - 处理用户操作（发送消息、切换配置、连接/断开）
  - 通过 `ChatApp` 访问服务层，保持 UI 层与业务逻辑层的分离

## 💬 消息处理流程

### Agent 模式消息流程

```
用户输入消息
    ↓
ChatController.sendMessage()
    ↓
MessageService.sendMessage()
    ↓
AgentClient.stream() [SDK 调用]
    ↓
WebSocket 发送请求
    ↓
接收流式响应
    ↓
StreamingMessageHandler.appendMessage()
    ↓
UiAdapter.appendAssistantMessage() [更新 UI]
    ↓
界面显示消息气泡
```

### Flow 模式消息流程

```
用户输入消息
    ↓
ChatController.sendMessage()
    ↓
MessageService.sendMessage()
    ↓
FlowClient.send() [SDK 调用]
    ↓
WebSocket 发送请求
    ↓
接收 Flow 事件：
  - onMessage (流式消息)
  - onWaiting (等待输入)
  - onEnd (流程结束)
  - onError (错误)
    ↓
UnifiedEventHandler 处理事件
    ↓
StreamingMessageHandler 更新消息
    ↓
UiAdapter 更新 UI
    ↓
界面显示消息气泡
```

### 流式消息处理机制

应用使用 `StreamingMessageHandler` 处理流式消息：

1. **消息初始化**：收到第一条消息时创建消息气泡
2. **增量更新**：后续消息片段追加到气泡中
3. **流式指示器**：显示"正在输入..."动画
4. **完成处理**：收到 `isFinished=true` 或 `onWaiting`/`onEnd` 事件时移除指示器

## ⚙️ 配置说明

### 配置文件格式

应用使用 `src/main/resources/application.yml` 和 `config-profiles.yml` 进行配置。

**Agent 配置示例**：

```yaml
cybotstar:
  agents:
    finance-agent:
      credentials:
        robot-key: your-robot-key
        robot-token: your-robot-token
        username: your-username
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
      http:
        url: https://www.cybotstar.cn/openapi/v2/
```

**Flow 配置示例**：

```yaml
cybotstar:
  flows:
    ir-flow:
      credentials:
        robot-key: your-robot-key
        robot-token: your-robot-token
        username: your-username
      flow:
        open-flow-trigger: direct
        open-flow-uuid: your-flow-uuid
        open-flow-debug: false  # 是否开启调试信息
      websocket:
        url: wss://www.cybotstar.cn/openapi/v2/ws/dialog/
      http:
        url: https://www.cybotstar.cn/openapi/v2/
```

### 配置文件管理

- **多配置支持**：应用支持多个配置文件，可以通过界面切换不同的配置
- **配置文件列表**：配置文件保存在 `config-profiles.yml` 中
- **动态切换**：切换配置时会自动断开当前连接，重新初始化客户端

## 📁 项目结构

```
cybotstar-sdk-java-example/
├── pom.xml                          # Maven 项目配置
├── README.md                        # 项目说明文档
├── run.bat                          # Windows 运行脚本
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── brgroup/
        │           └── cybotstar/
        │               └── javafx/
        │                   ├── ChatApp.java              # 主应用类
        │                   ├── ChatAppLauncher.java      # 自动启动器（自动配置 JavaFX）
        │                   ├── ChatController.java      # UI 控制器
        │                   ├── ChatConstants.java        # 常量定义
        │                   ├── prompt/                  # Prompt 模板
        │                   │   ├── finance-agent-prompt.txt
        │                   │   └── ir_flow_prompt.txt
        │                   ├── service/                  # 服务层
        │                   │   ├── UnifiedClientService.java    # 统一客户端服务
        │                   │   ├── UnifiedEventHandler.java     # 统一事件处理器
        │                   │   ├── config/                      # 配置服务
        │                   │   │   ├── ConfigService.java
        │                   │   │   └── YamlConfigService.java
        │                   │   └── message/                     # 消息服务
        │                   │       ├── MessageService.java
        │                   │       └── StreamingMessageHandler.java
        │                   ├── ui/                       # UI 组件
        │                   │   ├── UiAdapter.java        # UI 适配器接口
        │                   │   ├── MessageBubbleFactory.java
        │                   │   └── YamlConfigDialog.java
        │                   └── util/                     # 工具类
        │                       ├── UIUtils.java
        │                       └── YamlConfigManager.java
        └── resources/
            ├── javafx/
            │   ├── chat-view.fxml    # FXML 界面定义
            │   └── chat-style.css    # 样式文件
            ├── application.yml       # Spring Boot 配置
            └── config-profiles.yml   # 配置文件列表
```

## 💡 使用说明

### 基本使用流程

1. **启动应用**：运行 `ChatAppLauncher` 主类
2. **选择配置**：在左侧边栏选择要使用的配置文件（Agent 或 Flow）
3. **连接服务器**：点击"连接"按钮建立 WebSocket 连接
   - Agent 模式：连接会在首次发送消息时自动建立
   - Flow 模式：需要先点击"连接"按钮建立连接
4. **发送消息**：在底部输入框输入消息，按 Enter 或点击发送按钮
5. **查看回复**：AI 回复会以流式方式显示在聊天区域

### Agent 模式 vs Flow 模式

**Agent 模式**：
- 适合简单的问答场景
- 连接自动管理，无需手动连接
- 支持流式和非流式消息
- 配置简单，只需提供凭据和 URL

**Flow 模式**：
- 适合复杂的流程化对话
- 需要显式建立连接
- 支持流程节点控制、等待输入、流程跳转等高级功能
- 需要配置 Flow UUID 和触发方式

## 🛠️ 开发说明

### 添加新功能

- **UI 相关代码**：`src/main/java/com/brgroup/cybotstar/javafx/ui/`
- **业务逻辑**：`src/main/java/com/brgroup/cybotstar/javafx/service/`
- **界面样式**：`src/main/resources/javafx/chat-style.css`
- **界面布局**：`src/main/resources/javafx/chat-view.fxml`

### 扩展点

1. **添加新的消息类型**：修改 `MessageBubbleFactory` 和 `StreamingMessageHandler`
2. **添加新的配置项**：修改 `ConfigService` 和配置文件格式
3. **自定义事件处理**：在 `UnifiedEventHandler` 中添加新的事件处理器
4. **UI 定制**：修改 FXML 文件和 CSS 样式

### 调试

应用会在控制台输出详细的日志信息，包括：

- **连接状态变化**：连接建立、断开等事件
- **消息发送和接收**：原始请求/响应（Agent 模式）
- **Flow 事件**：节点进入、流程结束、错误等（Flow 模式）
- **错误信息**：详细的错误堆栈和提示

### ChatAppLauncher 工作原理

`ChatAppLauncher` 类实现了自动配置 JavaFX 模块路径的功能：

1. **检测 classpath**：检查是否包含 JavaFX jar（IDEA 运行时会将所有依赖放在 classpath）
2. **自动配置**：如果检测到 JavaFX 在 classpath 中，使用 `ProcessBuilder` 启动新进程
3. **模块路径配置**：在新进程中，JavaFX jar 仅通过 `module-path` 加载，从 classpath 中排除
4. **平台检测**：自动检测平台（Windows/macOS/Linux）并使用对应的 JavaFX jar
5. **Maven 仓库查找**：自动查找 Maven 仓库路径（支持多种查找方式）

这样设计的好处是：**无需任何手动配置，直接在 IDEA 中运行即可**！

## 📝 注意事项

1. **JavaFX 模块路径**：如果使用 Java 11+，需要配置 JavaFX 模块路径（`ChatAppLauncher` 已自动处理）
2. **配置文件**：确保配置文件中的凭据信息正确
3. **网络连接**：确保能够访问 CybotStar 服务器地址
4. **依赖安装**：首次运行前需要先安装 `cybotstar-spring-boot3-starter` 到本地 Maven 仓库

## 🔗 依赖关系

本项目依赖于：

- `cybotstar-spring-boot3-starter` - CybotStar SDK 核心库
- `spring-boot-starter` - Spring Boot 框架
- `javafx-controls` - JavaFX 控件库
- `javafx-fxml` - JavaFX FXML 支持
- `lombok` - 代码生成工具
- `fastjson2` - JSON 处理库

## 📖 更多资源

- [CybotStar SDK 文档](../cybotstar-spring-boot3-starter/README.md)
- [JavaFX 官方文档](https://openjfx.io/)

---

**祝您使用愉快！** 🎉
