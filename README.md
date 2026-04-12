# AI Code Generator

一个基于 `Spring Boot + Vue 3 + LangChain4j` 的 AI 应用生成平台。用户通过一句提示词即可创建应用，随后在对话页面中持续迭代需求，系统会流式生成代码、自动落盘、即时预览，并支持一键部署为可访问链接。

## 项目简介

本项目采用前后端分离架构：

- 后端负责用户体系、应用管理、对话历史、AI 代码生成、代码解析与保存、预览资源分发、部署链接生成
- 前端负责应用创建、对话式生成界面、实时预览、作品展示、后台管理
- AI 能力基于 `LangChain4j` 接入 OpenAI 兼容模型，并结合 Redis 保存对话记忆

项目适合作为以下场景的实践样板：

- AI 代码生成平台
- AI 网站/页面生成器
- 基于大模型的对话式应用构建系统
- LangChain4j + Spring Boot 的工程化落地示例

## 核心功能

### 1. 用户与权限

- 用户注册、登录、注销
- 基于 Session + Redis 的登录态管理
- 普通用户只能操作自己的应用
- 管理员可管理用户、应用与对话记录

### 2. 应用生成

- 支持通过提示词创建应用
- 支持两种主要生成模式：
  - `html`：单页 HTML 页面
  - `multi_file`：多文件网站/应用
- 后端已预留 `vue_project` 生成模式，便于后续扩展为更完整的工程生成

### 3. 对话式迭代生成

- 用户进入应用对话页后，可持续补充需求
- 使用 SSE 流式返回 AI 输出
- 前端实时展示生成内容
- 生成完成后自动解析代码并保存到本地目录

### 4. 代码预览与部署

- 自动将生成结果保存到 `tmp/code_output`
- 通过静态资源接口直接预览生成结果
- 支持将当前代码复制到部署目录 `tmp/code_deploy`
- 生成固定访问链接，便于作品展示与分享

### 5. 应用广场与后台管理

- 首页展示“我的应用”和“精选应用”
- 支持应用编辑、删除、查看详情
- 管理员后台支持：
  - 用户管理
  - 应用管理
  - 对话记录管理

## 主要流程

```text
用户输入提示词
  -> 创建应用
  -> 进入对话页
  -> SSE 流式调用大模型生成代码
  -> 后端解析 AI 返回结果
  -> 保存到 tmp/code_output/{codeGenType}_{appId}
  -> 前端 iframe 实时预览
  -> 用户可继续对话迭代
  -> 一键部署到 tmp/code_deploy/{deployKey}
  -> 通过固定 URL 访问作品
```

## 技术栈

### 后端

- Java 21
- Spring Boot 3
- Spring Web
- Spring AOP
- Spring Session Data Redis
- MyBatis-Flex
- MySQL
- Redis
- LangChain4j
- Knife4j / OpenAPI
- Hutool
- Caffeine Cache

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Ant Design Vue
- Axios
- Markdown-it
- Highlight.js

## 目录结构

```text
.
├─ src/main/java/com/bvz/aicodegenerator
│  ├─ ai                 # AI 服务、模型工厂、工具定义
│  ├─ controller         # 接口层
│  ├─ core               # 代码生成门面、解析器、文件保存器
│  ├─ service            # 业务服务
│  ├─ mapper             # MyBatis-Flex Mapper
│  ├─ model              # DTO / Entity / VO / Enum
│  ├─ config             # Redis / CORS / 模型等配置
│  └─ exception          # 全局异常处理
├─ src/main/resources
│  ├─ prompt             # AI 系统提示词模板
│  ├─ mapper             # XML 映射文件
│  └─ application.yml    # 后端配置
├─ ai-code-generator-frontend
│  ├─ src/pages          # 页面
│  ├─ src/components     # 组件
│  ├─ src/api            # 接口请求
│  └─ src/router         # 前端路由
├─ sql
│  └─ create_table.sql   # 初始化数据库脚本
└─ tmp
   ├─ code_output        # 生成代码目录
   └─ code_deploy        # 部署目录
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- MySQL 8.x
- Redis 6.x 或更高版本
- 一个 OpenAI 兼容的大模型接口

## 配置说明

后端默认配置位于 [src/main/resources/application.yml](src/main/resources/application.yml)。

当前仓库中的默认配置包含以下本地开发约定：

- MySQL：`jdbc:mysql://localhost:3306/ai_code_generator`
- Redis：`localhost:6379`
- 后端端口：`8123`
- 接口前缀：`/api`
- Session 存储：Redis

### AI 模型接入

本项目通过 `langchain4j-open-ai-spring-boot-starter` 接入 OpenAI 兼容接口，并额外提供了一个推理流式模型配置用于更复杂的工程生成。

你至少需要补齐以下配置项：

```yml
langchain4j:
  open-ai:
    chat-model:
      api-key: your_api_key
      base-url: your_base_url
```

说明：

- `chat-model` 配置同时会被 `ReasoningStreamingChatModelConfig` 读取
- 当前代码中推理模型默认写死为 `deepseek-chat`，如需生产使用，可自行调整为更合适的模型
- 若你的模型服务商是 OpenAI 兼容接口，一般都可以直接接入

## 数据库初始化

执行 [sql/create_table.sql](sql/create_table.sql) 初始化数据库：

```sql
create database if not exists ai_code_generator;
use ai_code_generator;
```

然后创建以下核心表：

- `user`
- `app`
- `chat_history`

## 本地启动

### 1. 启动后端

```bash
mvn spring-boot:run
```

或：

```bash
./mvnw spring-boot:run
```

后端启动后默认地址：

- API 根地址：`http://localhost:8123/api`
- 健康检查：`http://localhost:8123/api/health/`
- Swagger / Knife4j：`http://localhost:8123/api/swagger-ui.html`

### 2. 启动前端

```bash
cd ai-code-generator-frontend
npm install
npm run dev
```

前端默认通过环境变量读取后端地址，未配置时使用：

- `VITE_API_BASE_URL=http://localhost:8123/api`
- `VITE_DEPLOY_DOMAIN=http://localhost`
- `VITE_STATIC_BASE_URL=http://localhost:8123/api`

## 关键接口

### 用户相关

- `POST /api/user/register`
- `POST /api/user/login`
- `GET /api/user/get/login`
- `POST /api/user/logout`

### 应用相关

- `POST /api/app/add`
- `POST /api/app/update`
- `POST /api/app/delete`
- `GET /api/app/get/vo`
- `POST /api/app/my/list/page/vo`
- `POST /api/app/good/list/page/vo`

### AI 生成与部署

- `GET /api/app/chat/gen/code`：SSE 流式生成代码
- `POST /api/app/deploy`：部署应用并返回访问链接
- `GET /api/static/{codeGenType}_{appId}/`：预览已生成的应用

### 管理端

- `POST /api/user/admin/list/page/vo`
- `POST /api/app/admin/list/page/vo`
- `POST /api/chatHistory/admin/list/page/vo`

## 生成与存储机制

项目的核心后端链路大致如下：

1. `AppController` 接收用户对话请求
2. `AppServiceImpl` 校验权限并记录用户消息
3. `AiCodeGeneratorFacade` 根据生成类型选择对应 AI 生成策略
4. `AiCodeGeneratorServiceFactory` 构造带聊天记忆的 AI 服务实例
5. AI 返回流式内容后，由 `CodeParserExecutor` 解析结构化结果
6. 由 `CodeFileSaverExecutor` 保存到本地目录
7. `StaticResourceController` 提供预览访问
8. 部署时复制生成目录到 `tmp/code_deploy/{deployKey}`

## 对话记忆与历史记录

- 对话消息会持久化到 `chat_history` 表
- 同时使用 Redis Chat Memory 保留最近上下文
- AI 服务实例通过 Caffeine 做短期缓存，减少重复构建开销

这意味着项目兼顾了：

- 对话上下文连续性
- 历史记录可追溯
- 多次迭代同一应用时的生成稳定性

## 项目亮点

- 支持从提示词到代码生成、预览、部署的完整闭环
- 支持 SSE 流式输出，交互体验更接近真实 AI 产品
- 支持多种代码生成模式，便于后续扩展
- 后端结构清晰，便于替换模型、扩展解析器和保存策略
- 前端已具备作品广场、聊天页、预览页、管理后台等完整产品雏形

## 当前已知约束

- 默认配置中数据库账号密码为本地开发值，生产环境请务必改为环境变量或配置中心
- 部署能力当前本质上是“复制到本地静态目录并生成访问路径”，不是云端正式发布系统
- `CODE_DEPLOY_HOST` 目前默认写为 `http://localhost`，上线时需要改成实际域名
- 推理模型名称当前在代码中有硬编码，建议后续抽为可配置项
- 仓库内部分中文注释存在编码异常，不影响运行，但建议统一文件编码为 UTF-8

## 后续优化建议

- 增加 `.env` / 多环境配置模板
- 引入 Docker / Docker Compose 一键启动
- 补充项目截图与演示 GIF
- 将部署能力接入对象存储、Nginx 或静态站点托管
- 补充限流、审计、用量统计与计费能力
- 为不同生成模式增加更严格的输出格式校验

## 测试

仓库中已包含部分测试类，主要覆盖：

- 代码解析
- AI 生成门面
- 服务层基础能力

可通过以下命令执行：

```bash
mvn test
```

## 适合谁参考

- 想做 AI 网站生成器 / AI 应用搭建平台的开发者
- 想学习 LangChain4j 在 Java 后端中如何工程化落地的同学
- 想把“大模型对话 + 代码生成 + 预览部署”串成完整产品闭环的团队

## License

当前仓库未明确提供 License 文件。如计划开源发布到 GitHub，建议补充 `LICENSE`。
