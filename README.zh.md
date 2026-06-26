# Bidr Framework

> 基于 Spring Boot 的企业级微服务基础框架
>
> Enterprise-grade Microservice Foundation Framework based on Spring Boot

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-8+-orange)](https://www.java.com/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.2-blue)](https://baomidou.com/)

---

## 📋 Project Overview / 项目概述

**Bidr Framework** is a multi-module enterprise microservice foundation framework built on Spring Boot 2.7.3. It provides a comprehensive set of reusable infrastructure capabilities including unified authentication & authorization, caching, messaging, persistence, file storage, search engine integration, workflow automation, and more. Designed with modularity and extensibility in mind, it serves as the technical foundation for building complex business applications.

**Bidr Framework** 是基于 Spring Boot 2.7.3 构建的多模块企业级微服务基础框架。它提供了一套完整的、可复用的基础设施能力，包括统一认证授权、缓存、消息队列、持久化、文件存储、搜索引擎集成、工作流自动化等。框架以模块化和可扩展性为核心设计理念，作为构建复杂业务应用的技术底座。

---

## 🏗 Architecture / 整体架构

The framework follows a two-layer modular architecture:

```
Bidr Framework
├── Core Layer (bidr-core)              — 基础核心能力
│   ├── kernel                          — 核心基础设施 (统一响应、MyBatis-Plus、Swagger、JSON序列化、缓存、日志)
│   ├── platform                        — 平台服务 (系统字典、业务词典、Excel、配置管理、日志存储)
│   ├── authorization                   — 认证授权
│   ├── admin                           — 系统管理
│   ├── redis                           — Redis 缓存
│   ├── kafka                           — 消息队列
│   ├── email                           — 邮件服务
│   ├── sms                             — 短信服务
│   ├── oss                             — 对象存储
│   ├── elasticsearch                   — 搜索引擎
│   ├── mongo                           — MongoDB 数据库
│   ├── neo4j                           — Neo4j 图数据库
│   ├── mqtt                            — MQTT 物联网协议
│   ├── socket-io                       — WebSocket 实时通信
│   ├── wechat                          — 微信集成
│   ├── ocr                             — OCR 识别
│   ├── qichacha                        — 企查查集成
│   ├── mcp                             — Model Context Protocol
│   ├── xxl-job                         — 分布式任务调度
│   ├── td                              — 时序数据
│   ├── forge                           — 开发辅助工具
│   └── db                              — 数据库组件
│
└── Service Layer (bidr-services)       — 基础服务能力
    ├── bidr-framework                  — 框架基础 (登录、Token)
    ├── bidr-om                         — 经营数据服务
    ├── bidr-dc                         — 数据中心服务
    ├── bidr-mdm                        — 主数据管理服务
    ├── bidr-oa                         — OA办公服务
    ├── bidr-notice                     — 消息通知服务
    ├── bidr-ai                         — AI集成服务
    ├── bidr-sequence                   — 序列号生成服务
    └── bidr-elasticsearch              — ES搜索封装服务
```

---

## ✨ Features / 核心功能

| Category / 分类 | Capabilities / 能力 |
| --- | --- |
| **Web Framework / Web框架** | Spring Boot 2.7.3, RESTful API, 统一异常处理, 统一响应封装 |
| **ORM & Database / 数据库** | MyBatis-Plus 3.5.2, 多数据源, MyBatis-Plus-Join, 动态表名, Code First |
| **Authentication / 认证授权** | 统一Token鉴权, 接口权限控制, 租户隔离, MDM集成 |
| **Cache / 缓存** | Redis, 动态内存缓存, 分布式锁 |
| **Messaging / 消息队列** | Kafka, MQTT |
| **Search Engine / 搜索引擎** | Elasticsearch 8.17.3 |
| **File Storage / 文件存储** | OSS对象存储 |
| **Real-time Communication / 实时通信** | Socket.IO (WebSocket) |
| **Scheduling / 任务调度** | XXL-JOB 分布式调度 |
| **Notification / 通知** | 邮件, 短信 |
| **Third-party Integration / 三方集成** | 微信, 企查查, OCR识别 |
| **AI / 人工智能** | Model Context Protocol (MCP), AI服务集成 |
| **Office / 办公** | Excel导入导出 (EasyExcel, JXLS), 二维码生成 |
| **System Management / 系统管理** | 动态词典, 业务配置, 操作日志, 管理后台 |
| **API Documentation / 接口文档** | Swagger 3.0 (OpenAPI) |
| **Time-series / 时序数据** | TDengine 集成 |

---

## 🛠 Tech Stack / 技术栈

### Core / 核心

| Technology | Version |
| --- | --- |
| Java | 8+ |
| Spring Boot | 2.7.3 |
| MyBatis-Plus | 3.5.2 |
| MyBatis-Plus-Join | 1.5.2 |
| Dynamic Datasource | 3.5.2 |
| diboot-core | 2.11.0 |
| JSQLParser | 4.6 |
| Swagger (Springfox) | 3.0.0 |
| Lombok | - |
| Hutool | 5.8.8 |
| Reflections | 0.9.11 |

### Data & Storage / 数据与存储

| Technology | Version |
| --- | --- |
| MySQL | - |
| Redis | - |
| MongoDB | - |
| Neo4j | - |
| Elasticsearch | 8.17.3 |
| TDengine | - |

### Messaging & Communication / 消息与通信

| Technology | Version |
| --- | --- |
| Kafka | - |
| MQTT | - |
| Socket.IO | - |

### Infrastructure / 基础设施

| Technology | Version |
| --- | --- |
| Maven | 3+ |
| XXL-JOB | - |
| Nacos / Apollo (Config) | - |
| JUnit 4 | 4.13.2 |
| TestNG | 6.14.3 |

---

## 🚀 Quick Start / 快速开始

### Prerequisites / 环境要求

- **JDK 8+**
- **Maven 3.6+**
- **MySQL 5.7+**
- **Redis**
- **Optional**: Kafka, Elasticsearch, MongoDB, Neo4j, TDengine (可按需启用)

### 使用脚手架搭建新项目 / Scaffold a New Project

框架提供了 `setup.sh` 脚手架脚本，可快速搭建一个完整的项目模块。

The framework provides a `setup.sh` scaffolding script to quickly bootstrap a complete project module.

```bash
# 进入脚本目录 / Navigate to the script directory
cd framework/core/script

# 创建新项目（交互式，会提示输入项目编码和端口号）
# Create a new project (interactive, prompts for project code and port)
bash setup.sh

# 或直接指定项目编码，脚本会自动生成项目名和包名
# Or specify project code directly, name and package are auto-generated
bash setup.sh mcp

# 强制覆盖已存在的项目目录 / Force overwrite existing project directory
bash setup.sh --force mcp

# 统一修改已生成项目的端口号 / Change port of an existing project
bash setup.sh --change-port 8080
```

#### 脚本会自动生成以下内容 / The script generates:

| 产物 / Artifact | 说明 / Description |
| --- | --- |
| `{code}-common` 模块 | 公共模块：实体、Mapper、Service、Controller、VO、配置等 |
| `{code}-server` 模块 | 服务启动模块：Application 启动类、Spring Boot 打包配置 |
| `application*.yml` | 多环境配置（dev / pre / prod） |
| `Dockerfile` | 容器化构建文件 |
| `docker-compose.yml` | Docker Compose 部署编排 |
| `docker-start.sh` | Docker 部署管理脚本 |
| `start.sh` | JAR 包部署管理脚本 |
| `deploy.sh` | 多环境部署脚本 |
| `nginx/{code}.conf` | Nginx 路由配置 |
| `agent.md` | AI 开发助手指南 |

#### 生成的模块结构 / Generated Module Structure

```
{code}-common/
└── src/main/java/com/bidr/{code}/
    ├── config/              # 配置类
    ├── constant/            # 常量、枚举字典
    ├── controller/          # REST API 控制器
    ├── service/             # 业务服务层
    ├── vo/                  # 值对象（前端交互）
    └── dao/
        ├── entity/          # 数据库实体
        ├── mapper/          # Mapper 接口和 XML
        ├── repository/      # Repository Service（业务逻辑）
        └── schema/          # Schema Service（DDL 定义和升级脚本）
```

#### 新项目起步 / Start Developing

```bash
# 1. 配置数据库和 Redis 连接信息（在 application-*.yml 中设置）
#    Configure database and Redis in application-*.yml

# 2. 编译项目（使用 development profile）
#    Build the project
mvn clean install -P development,!public-snapshots -DskipTests

# 3. 启动服务 / Start the service
cd {code}-server
mvn spring-boot:run -P development,!public-snapshots

# 4. 开始开发业务功能 / Start building your business features
```

> **提示**: 编译时必须使用 `-P development,!public-snapshots` 参数，否则会使用 Nexus 远程仓库中的 SNAPSHOT 版本，导致本地代码变更不生效。
>
> **Tip**: Always use `-P development,!public-snapshots` when building locally, otherwise Maven will resolve SNAPSHOT dependencies from the remote Nexus repository instead of your local changes.

---

## 🧭 Roadmap / 后续规划

- [ ] MCP 服务增强 (工具注册、资源发现)
- [ ] 响应式编程支持 (Spring WebFlux)
- [ ] 升级 Spring Boot 3.x / JDK 17
- [ ] 集成 GraalVM Native Image 支持
- [ ] 完善可观测性 (Metrics, Tracing)
- [ ] 引入 API 网关层
- [ ] 统一配置中心深度集成
- [ ] 多活/容灾架构支持
- [ ] 服务网格 (Service Mesh) 适配

---

## 📄 License / 许可证

Copyright © Bidr. All rights reserved.
