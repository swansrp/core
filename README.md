# Bidr Framework

> Enterprise-grade Microservice Foundation Framework based on Spring Boot

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-8+-orange)](https://www.java.com/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.2-blue)](https://baomidou.com/)

---

## Project Overview

**Bidr Framework** is a multi-module enterprise microservice foundation framework built on Spring Boot 2.7.3. It provides a comprehensive set of reusable infrastructure capabilities including unified authentication & authorization, caching, messaging, persistence, file storage, search engine integration, workflow automation, and more. Designed with modularity and extensibility in mind, it serves as the technical foundation for building complex business applications.

---

## Architecture

The framework follows a two-layer modular architecture:

```
Bidr Framework
├── Core Layer (bidr-core)
│   ├── kernel                          — Core infrastructure (unified response, MyBatis-Plus, Swagger, JSON, cache, logging)
│   ├── platform                        — Platform services (system dictionary, Excel, config management, audit log)
│   ├── authorization                   — Authentication & authorization
│   ├── admin                           — System administration
│   ├── redis                           — Redis cache
│   ├── kafka                           — Message queue
│   ├── email                           — Email service
│   ├── sms                             — SMS service
│   ├── oss                             — Object storage
│   ├── elasticsearch                   — Search engine
│   ├── mongo                           — MongoDB
│   ├── neo4j                           — Neo4j graph database
│   ├── mqtt                            — MQTT IoT protocol
│   ├── socket-io                       — WebSocket real-time communication
│   ├── wechat                          — WeChat integration
│   ├── ocr                             — OCR recognition
│   ├── qichacha                        — Qichacha (business data) integration
│   ├── mcp                             — Model Context Protocol
│   ├── xxl-job                         — Distributed task scheduling
│   ├── td                              — Time-series data (TDengine)
│   ├── forge                           — Developer tooling
│   └── db                              — Database components
│
└── Service Layer (bidr-services)
    ├── bidr-framework                  — Framework foundation (login, token)
    ├── bidr-om                         — Operations management services
    ├── bidr-dc                         — Data center services
    ├── bidr-mdm                        — Master data management services
    ├── bidr-oa                         — Office automation services
    ├── bidr-notice                     — Notification services
    ├── bidr-ai                         — AI integration services
    ├── bidr-sequence                   — Sequence number generation
    └── bidr-elasticsearch              — Elasticsearch wrapper
```

---

## Features

| Category | Capabilities |
| --- | --- |
| **Web Framework** | Spring Boot 2.7.3, RESTful API, unified exception handling, unified response |
| **ORM & Database** | MyBatis-Plus 3.5.2, multi-datasource, MyBatis-Plus-Join, dynamic table name, Code First |
| **Authentication** | Unified token auth, API permission control, tenant isolation, MDM integration |
| **Cache** | Redis, dynamic memory cache, distributed lock |
| **Messaging** | Kafka, MQTT |
| **Search Engine** | Elasticsearch 8.17.3 |
| **File Storage** | OSS object storage |
| **Real-time Communication** | Socket.IO (WebSocket) |
| **Scheduling** | XXL-JOB distributed scheduler |
| **Notification** | Email, SMS |
| **Third-party Integration** | WeChat, Qichacha, OCR |
| **AI** | Model Context Protocol (MCP), AI service integration |
| **Office** | Excel import/export (EasyExcel, JXLS), QR code generation |
| **System Management** | Dynamic dictionary, business config, audit log, admin panel |
| **API Documentation** | Swagger 3.0 (OpenAPI) |
| **Time-series Data** | TDengine integration |

---

## Tech Stack

### Core

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

### Data & Storage

| Technology | Version |
| --- | --- |
| MySQL | - |
| Redis | - |
| MongoDB | - |
| Neo4j | - |
| Elasticsearch | 8.17.3 |
| TDengine | - |

### Messaging & Communication

| Technology | Version |
| --- | --- |
| Kafka | - |
| MQTT | - |
| Socket.IO | - |

### Infrastructure

| Technology | Version |
| --- | --- |
| Maven | 3+ |
| XXL-JOB | - |
| Nacos / Apollo (Config) | - |
| JUnit 4 | 4.13.2 |
| TestNG | 6.14.3 |

---

## Quick Start

### Prerequisites

- **JDK 8+**
- **Maven 3.6+**
- **MySQL 5.7+**
- **Redis**
- **Optional**: Kafka, Elasticsearch, MongoDB, Neo4j, TDengine

### Scaffold a New Project

The framework provides a `setup.sh` scaffolding script to quickly bootstrap a complete project module.

```bash
# Navigate to the script directory
cd framework/core/script

# Create a new project (interactive mode, prompts for project code and port)
bash setup.sh

# Or specify project code directly (name and package are auto-generated)
bash setup.sh mcp

# Force overwrite existing project directory
bash setup.sh --force mcp

# Change port of an existing project
bash setup.sh --change-port 8080
```

### Generated Artifacts

| Artifact | Description |
| --- | --- |
| `{code}-common` module | Common module: entities, mappers, services, controllers, VOs, config |
| `{code}-server` module | Boot module: Application class, Spring Boot packaging config |
| `application*.yml` | Multi-environment config (dev / pre / prod) |
| `Dockerfile` | Docker image build file |
| `docker-compose.yml` | Docker Compose deployment |
| `docker-start.sh` | Docker deployment management script |
| `start.sh` | JAR deployment management script |
| `deploy.sh` | Multi-environment deployment script |
| `nginx/{code}.conf` | Nginx routing configuration |
| `agent.md` | AI assistant development guide |

### Generated Module Structure

```
{code}-common/
└── src/main/java/com/bidr/{code}/
    ├── config/              # Configuration classes
    ├── constant/            # Constants & enum dictionaries
    ├── controller/          # REST API controllers
    ├── service/             # Business service layer
    ├── vo/                  # Value objects (frontend interaction)
    └── dao/
        ├── entity/          # Database entities
        ├── mapper/          # Mapper interfaces and XML
        ├── repository/      # Repository services (business logic)
        └── schema/          # Schema services (DDL definition & migration)
```

### Start Developing

```bash
# 1. Configure database and Redis connection in application-*.yml

# 2. Build the project (use development profile)
mvn clean install -P development,!public-snapshots -DskipTests

# 3. Start the service
cd {code}-server
mvn spring-boot:run -P development,!public-snapshots

# 4. Start building your business features
```

> **Tip**: Always use `-P development,!public-snapshots` when building locally. Without this flag, Maven will resolve SNAPSHOT dependencies from the remote Nexus repository instead of your local changes, causing your modifications to be ignored.

---

## Roadmap

- [ ] MCP service enhancement (tool registration, resource discovery)
- [ ] Reactive programming support (Spring WebFlux)
- [ ] Upgrade to Spring Boot 3.x / JDK 17
- [ ] GraalVM Native Image support
- [ ] Improved observability (Metrics, Tracing)
- [ ] API Gateway layer
- [ ] Deep integration with configuration center
- [ ] Multi-region / disaster recovery architecture
- [ ] Service Mesh adaptation

---

## License

Copyright © Bidr. All rights reserved.
