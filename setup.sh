#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 打印脚本标题
print_info "================================================"
print_info "        项目初始化脚本 v1.1.0"
print_info "================================================"
echo ""

# 1. 解析命令行参数
FORCE_MODE=false
PROJECT_CODE=""

# 解析所有参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --force|-f)
            FORCE_MODE=true
            print_info "启用强制覆盖模式"
            shift
            ;;
        --help|-h)
            echo "用法: $0 [选项] [项目编码]"
            echo "选项:"
            echo "  --force, -f    强制覆盖已存在的文件和目录"
            echo "  --help, -h     显示帮助信息"
            echo "示例:"
            echo "  $0 stp              # 创建stp项目"
            echo "  $0 --force stp      # 强制覆盖已存在的stp项目"
            exit 0
            ;;
        *)
            if [ -z "$PROJECT_CODE" ]; then
                PROJECT_CODE="$1"
            fi
            shift
            ;;
    esac
done

# 如果没有通过参数提供项目编码，则交互式输入
if [ -z "$PROJECT_CODE" ]; then
    read -p "请输入项目编码(英文,如:mcp): " PROJECT_CODE
else
    print_info "使用项目编码: $PROJECT_CODE"
fi

if [ -z "$PROJECT_CODE" ]; then
    print_error "项目编码不能为空"
    exit 1
fi

# 自动生成其他信息
PROJECT_NAME="${PROJECT_CODE}项目"
BASE_PACKAGE="com.bidr.${PROJECT_CODE}"

print_info "项目编码: $PROJECT_CODE"
print_info "项目名称: $PROJECT_NAME (自动生成)"
print_info "基础包名: $BASE_PACKAGE (自动生成)"
echo ""

# 2. 判断当前目录结构
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$SCRIPT_DIR"
PARENT_DIR="$(dirname "$CORE_DIR")"
PARENT_NAME="$(basename "$PARENT_DIR")"

print_info "当前脚本目录: $SCRIPT_DIR"
print_info "Core目录: $CORE_DIR"
print_info "上层目录: $PARENT_DIR"
print_info "上层目录名: $PARENT_NAME"

# 判断是framework还是项目根目录
if [ "$PARENT_NAME" == "framework" ]; then
    print_info "检测到上层是framework目录"
    IS_FRAMEWORK=true
    ROOT_DIR="$(dirname "$PARENT_DIR")"
    DEP_MODULE="framework"
else
    print_info "检测到上层是项目根目录"
    IS_FRAMEWORK=false
    ROOT_DIR="$PARENT_DIR"
    DEP_MODULE="core"
fi

print_info "项目根目录: $ROOT_DIR"
echo ""

# 3. 创建项目目录
PROJECT_DIR="$ROOT_DIR/${PROJECT_CODE}-common"
print_info "开始创建项目目录: $PROJECT_DIR"

if [ -d "$PROJECT_DIR" ]; then
    if [ "$FORCE_MODE" = true ]; then
        print_warn "项目目录已存在，强制覆盖模式已启用"
    else
        print_error "项目目录已存在: $PROJECT_DIR"
        print_error "使用 --force 参数强制覆盖"
        exit 1
    fi
fi

mkdir -p "$PROJECT_DIR"

# 转换包名为路径
PACKAGE_PATH=$(echo "$BASE_PACKAGE" | tr '.' '/')

# 创建基础目录结构
print_info "创建目录结构..."
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/config"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/constant"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/controller"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/service"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/vo"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/dao/entity"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/dao/mapper"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/dao/repository"
mkdir -p "$PROJECT_DIR/src/main/java/$PACKAGE_PATH/dao/schema"
mkdir -p "$PROJECT_DIR/src/main/resources/config"
mkdir -p "$PROJECT_DIR/src/test/java/$PACKAGE_PATH"

print_info "目录结构创建完成"

# 4. 生成模块 pom.xml
print_info "生成模块 pom.xml..."
cat > "$PROJECT_DIR/pom.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.bidr</groupId>
        <artifactId>${PROJECT_CODE}</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>${PROJECT_CODE}-common</artifactId>

    <profiles>
        <profile>
            <id>development</id>
            <properties>
                <maven.compiler.source>8</maven.compiler.source>
                <maven.compiler.target>8</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                <core.version>0.0.1-SNAPSHOT</core.version>
            </properties>
            <dependencies>
                <dependency>
                    <groupId>com.bidr</groupId>
                    <artifactId>kernel</artifactId>
                    <version>\${core.version}</version>
                </dependency>
                <dependency>
                    <groupId>com.bidr</groupId>
                    <artifactId>platform</artifactId>
                    <version>\${core.version}</version>
                </dependency>
                <dependency>
                    <groupId>com.bidr</groupId>
                    <artifactId>authorization</artifactId>
                    <version>\${core.version}</version>
                </dependency>
                <dependency>
                    <groupId>com.bidr</groupId>
                    <artifactId>admin</artifactId>
                    <version>\${core.version}</version>
                </dependency>
                <dependency>
                    <groupId>com.bidr</groupId>
                    <artifactId>forge</artifactId>
                    <version>\${core.version}</version>
                </dependency>
                <dependency>
                    <groupId>com.bidr</groupId>
                    <artifactId>oss</artifactId>
                    <version>\${core.version}</version>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>public-snapshots</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <maven.compiler.source>8</maven.compiler.source>
                <maven.compiler.target>8</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                <core.version>base_0.0.1</core.version>
                <kernel.version>\${core.version}</kernel.version>
                <platform.version>\${core.version}</platform.version>
                <authorization.version>\${core.version}</authorization.version>
                <redis.version>\${core.version}</redis.version>
                <email.version>\${core.version}</email.version>
                <oss.version>\${core.version}</oss.version>
                <admin.version>\${core.version}</admin.version>
                <forge.version>\${core.version}</forge.version>
            </properties>
            <dependencies>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>kernel</artifactId>
                    <version>\${kernel.version}</version>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>platform</artifactId>
                    <version>\${platform.version}</version>
                    <exclusions>
                        <!-- 排除 platform 内部的 kernel 依赖,避免版本冲突 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>authorization</artifactId>
                    <version>\${authorization.version}</version>
                    <exclusions>
                        <!-- 排除传递依赖的 kernel,统一使用项目指定版本 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 platform -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>platform</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 redis -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>redis</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 email -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>email</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>redis</artifactId>
                    <version>\${redis.version}</version>
                    <exclusions>
                        <!-- 排除传递依赖的 kernel 和 platform,统一使用项目指定版本 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>platform</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>email</artifactId>
                    <version>\${email.version}</version>
                    <exclusions>
                        <!-- 排除传递依赖的 kernel,统一使用项目指定版本 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>oss</artifactId>
                    <version>\${oss.version}</version>
                    <exclusions>
                        <!-- 排除传递依赖的 kernel,统一使用项目指定版本 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 platform -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>platform</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 redis -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>redis</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 email -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>email</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 authorization -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>authorization</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>admin</artifactId>
                    <version>\${admin.version}</version>
                    <exclusions>
                        <!-- 排除传递依赖的 kernel,统一使用项目指定版本 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 platform -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>platform</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 redis -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>redis</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 authorization -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>authorization</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 email -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>email</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
                <dependency>
                    <groupId>com.github.swansrp.core</groupId>
                    <artifactId>forge</artifactId>
                    <version>\${forge.version}</version>
                    <exclusions>
                        <!-- 排除传递依赖的 kernel,统一使用项目指定版本 -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>kernel</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 platform -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>platform</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 redis -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>redis</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 authorization -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>authorization</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 email -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>email</artifactId>
                        </exclusion>
                        <!-- 排除传递依赖的 admin -->
                        <exclusion>
                            <groupId>com.bidr</groupId>
                            <artifactId>admin</artifactId>
                        </exclusion>
                    </exclusions>
                </dependency>
            </dependencies>
        </profile>
    </profiles>

</project>
EOF

print_info "模块 pom.xml 生成完成"

# 5. 生成或更新根目录 pom.xml
ROOT_POM="$ROOT_DIR/pom.xml"
print_info "处理根目录 pom.xml..."

if [ -f "$ROOT_POM" ]; then
    if [ "$FORCE_MODE" = true ]; then
        print_warn "根目录 pom.xml 已存在，强制覆盖模式下仍需手动添加模块: <module>${PROJECT_CODE}-common</module>"
    else
        print_warn "根目录 pom.xml 已存在，需要手动添加模块: <module>${PROJECT_CODE}-common</module>"
    fi
else
    print_info "生成根目录 pom.xml..."
    cat > "$ROOT_POM" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.bidr</groupId>
    <artifactId>${PROJECT_CODE}</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>${PROJECT_CODE}</name>
    <description>${PROJECT_NAME}</description>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.3</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <profiles>
        <profile>
            <id>development</id>
            <modules>
EOF

    if [ "$IS_FRAMEWORK" = true ]; then
        echo "                <module>framework</module>" >> "$ROOT_POM"
    else
        echo "                <module>core</module>" >> "$ROOT_POM"
    fi

    cat >> "$ROOT_POM" << EOF
                <module>${PROJECT_CODE}-common</module>
                <module>${PROJECT_CODE}-server</module>
            </modules>
        </profile>
        <profile>
            <id>public-snapshots</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <modules>
                <module>${PROJECT_CODE}-common</module>
                <module>${PROJECT_CODE}-server</module>
            </modules>
        </profile>
    </profiles>

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

</project>
EOF
    print_info "根目录 pom.xml 生成完成"
fi

# 6. 创建 .gitignore
print_info "创建 .gitignore..."
cat > "$PROJECT_DIR/.gitignore" << EOF
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties

# IDE
.idea/
*.iml
.vscode/
*.sw?

# Log
*.log

# OS
.DS_Store
Thumbs.db
EOF

# 7. 生成项目文档
print_info "生成项目文档..."
DOC_DIR="$ROOT_DIR/doc"
mkdir -p "$DOC_DIR"

# 复制架构文档
if [ -f "$CORE_DIR/doc/PROJECT_ARCHITECTURE.md" ]; then
    cp "$CORE_DIR/doc/PROJECT_ARCHITECTURE.md" "$DOC_DIR/"
    print_info "已复制 PROJECT_ARCHITECTURE.md"
fi

# 复制SQL代码生成指南
if [ -f "$CORE_DIR/doc/SQL代码生成指南.md" ]; then
    cp "$CORE_DIR/doc/SQL代码生成指南.md" "$DOC_DIR/"
    print_info "已复制 SQL代码生成指南.md"
fi

# 创建 agent.md 索引文档
print_info "生成 agent.md 索引文档..."
cat > "$ROOT_DIR/agent.md" << EOF
# ${PROJECT_NAME} - AI Agent 开发指南

> 本文档为 AI 编程助手提供项目开发规范和最佳实践索引

## 项目信息

- **项目编码**: ${PROJECT_CODE}
- **项目名称**: ${PROJECT_NAME}
- **基础包名**: ${BASE_PACKAGE}
- **创建时间**: $(date +"%Y-%m-%d %H:%M:%S")

## 文档索引

### 1. 项目架构文档

**位置**: [\`doc/PROJECT_ARCHITECTURE.md\`](doc/PROJECT_ARCHITECTURE.md)

**内容概要**:
- 项目整体结构和模块职责
- 技术栈说明（Spring Boot、MyBatis-Plus、Vue3、TypeScript）
- 后端分层架构（Controller、Service、Repository）
- Controller返回值规范（使用 \`Resp.notice()\`）
- Service层数据校验规范（使用 \`Validator\`）
- 前端API自动生成和调用规范
- Portal组件使用规范
- 常见问题解决方案

**关键规范**:
- ✅ 无返回值方法使用 \`Resp.notice("提示消息")\`
- ✅ 使用 \`Validator.assertNotNull()\` 等方法进行数据校验
- ✅ 使用 \`portalRef.value.queryData()\` 刷新表格
- ✅ API调用参数：\`(showSuccess, showLoading, showErr)\`
- ✅ 前端类型安全：使用 \`ref<Type>()\` 明确类型

### 2. SQL代码生成指南

**位置**: [\`doc/SQL代码生成指南.md\`](doc/SQL代码生成指南.md)

**内容概要**:
- 从SQL DDL生成完整Java代码的规范
- 普通表生成8个文件：Entity、Mapper、MapperXML、Repository、Schema、VO、PortalService、PortalController
- 多对多关系表生成6个文件：Entity、Mapper、MapperXML、Repository、Schema、BindController
- Entity类生成规则（主键注解、审计字段、联合主键）
- VO类生成规则（Portal注解使用）
- Controller继承规则（BaseAdminController、BaseAdminOrderController、BaseAdminTreeController）
- 字段类型映射规则
- 命名转换规则（下划线转驼峰）

**关键规范**:
- ✅ Entity使用 \`@AccountContextFill\` 自动填充审计字段
- ✅ VO主键使用 \`@PortalIdField\`，名称字段使用 \`@PortalNameField\`
- ✅ SQL关键字字段需使用反引号：\`@TableField(value = "\\\`valid\\\`")\`
- ✅ Repository Service仅包含业务逻辑
- ✅ Schema Service包含DDL定义和升级脚本
- ✅ 联合主键使用 \`@MppMultiId\` 注解

### 3. 职责分离原则

**Repository Service vs Schema Service**:
- **Repository Service**: 仅包含业务逻辑方法，不包含DDL
- **Schema Service**: 仅包含DDL定义和升级脚本，继承 \`BaseMybatisSchema<Entity>\`

**Portal Service vs Portal Controller**:
- 泛型参数：\`<Entity, EntityVO>\`（第二个必须是VO！）
- VO必须继承 \`BaseVO\` 并使用 \`@EqualsAndHashCode(callSuper = true)\`

## 代码生成流程

### 从SQL生成代码

当收到SQL DDL语句时，按照以下顺序生成：

1. **Entity类** - 数据库实体映射
2. **Mapper接口** - MyBatis数据访问
3. **Mapper XML** - SQL映射配置
4. **Repository Service** - 业务逻辑（无DDL）
5. **Schema Service** - DDL定义和升级脚本
6. **VO类** - 前端交互对象
7. **Portal Service** - 门户业务服务
8. **Portal Controller** - REST API控制器

### 多对多关系表

识别联合主键表，生成6个文件（不生成VO、PortalService、PortalController）：
- 使用 \`@MppMultiId\` 标注联合主键
- 生成 \`BindController\` 继承 \`BaseBindController\`

## 开发规范快速索引

### 后端规范

\`\`\`java
// Controller无返回值
@PostMapping("/delete")
public void delete(Long id) {
    service.delete(id);
    Resp.notice("删除成功");
}

// Service数据校验
Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "数据");

// 获取当前用户
String user = AccountContext.getOperator();

// 对象转换
EntityVO vo = ReflectionUtil.copy(entity, EntityVO.class);
List<EntityVO> list = Resp.convert(entities, EntityVO.class);
\`\`\`

### 前端规范

\`\`\`typescript
// API调用
const res = await getList(false, false, true)  // 查询数据
await saveData(true, false, true)  // 提交表单

// Portal刷新
portalRef.value.queryData()  // ✅ 局部刷新
// window.location.reload()  // ❌ 不要用

// 类型安全
const data = ref<EntityVO[]>([])  // ✅ 明确类型
const data = ref<any[]>([])       // ❌ 不要用any
\`\`\`

## 项目结构

\`\`\`
${ROOT_DIR}/
├── ${PROJECT_CODE}-common/           # 公共模块（已创建）
│   └── src/main/java/${BASE_PACKAGE}/
│       ├── config/              # 配置类
│       ├── constant/            # 常量、枚举
│       ├── controller/          # REST API控制器
│       ├── service/             # 业务服务层
│       ├── vo/                  # 值对象（前端交互）
│       └── dao/
│           ├── entity/          # 数据库实体
│           ├── mapper/          # Mapper接口和XML
│           ├── repository/      # Repository Service（业务逻辑）
│           └── schema/          # Schema Service（DDL定义）
├── doc/                         # 项目文档
│   ├── PROJECT_ARCHITECTURE.md  # 架构文档
│   └── SQL代码生成指南.md        # 代码生成指南
└── agent.md                     # 本文档
\`\`\`

## 常用命令

\`\`\`bash
# Maven编译
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 启动服务
mvn spring-boot:run

# 前端API生成
npm run generate-api
\`\`\`

## AI助手使用提示

### 生成代码时

1. **阅读文档**: 先查看 \`doc/SQL代码生成指南.md\` 了解详细规范
2. **遵循规范**: 严格按照文档中的模板和规则生成代码
3. **注意分离**: Repository Service和Schema Service职责分离
4. **类型正确**: Portal Service/Controller的第二个泛型必须是VO
5. **注解完整**: Entity的审计字段、VO的Portal注解都要正确配置

### 开发功能时

1. **参考架构**: 查看 \`doc/PROJECT_ARCHITECTURE.md\` 了解架构规范
2. **返回值规范**: Controller无返回值时使用 \`Resp.notice()\`
3. **数据校验**: Service层使用 \`Validator\` 进行校验
4. **前端刷新**: Portal组件使用 \`portalRef.value.queryData()\`

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| $(date +"%Y-%m-%d") | 1.0.0 | 项目初始化 |

---

**注意**: 开发过程中请严格遵循本文档索引的规范，确保代码质量和一致性。
EOF

print_info "agent.md 生成完成"

# 8. 生成 README.md
print_info "生成 README.md..."
cat > "$PROJECT_DIR/README.md" << EOF
# ${PROJECT_NAME} - 公共模块

## 模块说明

本模块为 ${PROJECT_NAME} 的公共基础模块，包含：

- 实体类（Entity）
- 数据访问层（Mapper、Repository、Schema）
- 值对象（VO）
- 业务服务（Service）
- REST API控制器（Controller）
- 配置类（Config）
- 常量和枚举（Constant）

## 技术栈

- Spring Boot 2.7.3
- MyBatis-Plus 3.x
- Swagger 2.x
- Lombok

## 目录结构

\`\`\`
src/main/java/${BASE_PACKAGE}/
├── config/              # 配置类
├── constant/            # 常量、枚举字典
├── controller/          # REST API控制器
├── service/             # 业务服务层
├── vo/                  # 值对象（VO）
└── dao/
    ├── entity/          # 数据库实体
    ├── mapper/          # Mapper接口和XML
    ├── repository/      # Repository Service（业务逻辑）
    └── schema/          # Schema Service（DDL定义和升级脚本）
\`\`\`

## 开发规范

请参考项目根目录的文档：

- [\`doc/PROJECT_ARCHITECTURE.md\`](../doc/PROJECT_ARCHITECTURE.md) - 项目架构文档
- [\`doc/SQL代码生成指南.md\`](../doc/SQL代码生成指南.md) - SQL代码生成指南
- [\`agent.md\`](../agent.md) - AI Agent开发指南

## 构建和运行

\`\`\`bash
# 编译
mvn clean install

# 跳过测试
mvn clean install -DskipTests
\`\`\`

## 注意事项

1. **代码生成**: 从SQL生成代码时，请严格遵循 \`SQL代码生成指南.md\`
2. **职责分离**: Repository Service和Schema Service职责分离
3. **命名规范**: 使用驼峰命名，遵循Java命名约定
4. **注释规范**: 所有类和方法必须有JavaDoc注释

## 联系方式

如有问题，请联系项目负责人。
EOF

print_info "README.md 生成完成"

# 9. 生成 common 模块配置文件
print_info "生成 common 模块配置文件..."

# 生成 application-dev.yml
cat > "$PROJECT_DIR/src/main/resources/config/application-dev.yml" << 'EOF'
server: # 服务器的HTTP端口，默认为8080
  servlet: # 应用的访问路径
    context-path: /${spring.application.name}

swagger: # 是否开启swagger
  enabled: true
  # 请求前缀
  pathMapping:

my:
  master-db:
    config:
      db:
      url:
      port: 3306
      username:
      password:
      driver: com.mysql.cj.jdbc.Driver
EOF

# 生成 application-prod.yml
cat > "$PROJECT_DIR/src/main/resources/config/application-prod.yml" << 'EOF'
my:
  master-db:
    config:
      db:
      url:
      port:
      username:
      password:
      driver: com.mysql.cj.jdbc.Driver
EOF

# 生成 application-pre.yml
cat > "$PROJECT_DIR/src/main/resources/config/application-pre.yml" << 'EOF'
my:
  master-db:
    config:
      db:
      url:
      port:
      username:
      password:
      driver: com.mysql.cj.jdbc.Driver
EOF

print_info "配置文件生成完成"

# 10. 创建 server 模块
SERVER_DIR="$ROOT_DIR/${PROJECT_CODE}-server"
print_info "创建 ${PROJECT_CODE}-server 模块..."

if [ -d "$SERVER_DIR" ] && [ "$FORCE_MODE" = false ]; then
    print_warn "Server 模块已存在: $SERVER_DIR (跳过)"
    print_warn "使用 --force 参数可强制重新生成配置文件"
else
    if [ -d "$SERVER_DIR" ] && [ "$FORCE_MODE" = true ]; then
        print_warn "Server 模块已存在，强制覆盖模式下重新生成配置文件"
    fi
    mkdir -p "$SERVER_DIR/src/main/java/$PACKAGE_PATH"
    mkdir -p "$SERVER_DIR/src/main/resources/config"
    mkdir -p "$SERVER_DIR/src/test/java/$PACKAGE_PATH"
    
    # 生成 Application 启动类
    print_info "生成 Application 启动类..."
    # 首字母大写
    CAPITALIZED_CODE="$(echo ${PROJECT_CODE:0:1} | tr '[:lower:]' '[:upper:]')${PROJECT_CODE:1}"
    
    cat > "$SERVER_DIR/src/main/java/$PACKAGE_PATH/${CAPITALIZED_CODE}Application.java" << EOF
package ${BASE_PACKAGE};

import com.bidr.kernel.BaseApplication;
import org.springframework.boot.SpringApplication;

/**
 * ${PROJECT_NAME} 启动类
 * 
 * @author Sharp
 */
public class ${CAPITALIZED_CODE}Application extends BaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(${CAPITALIZED_CODE}Application.class, args);
    }
}
EOF

    # 生成 server 模块的 pom.xml
    print_info "生成 server 模块 pom.xml..."
    cat > "$SERVER_DIR/pom.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.bidr</groupId>
        <artifactId>${PROJECT_CODE}</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>${PROJECT_CODE}-server</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.bidr</groupId>
            <artifactId>${PROJECT_CODE}-common</artifactId>
            <version>\${project.version}</version>
        </dependency>
    </dependencies>

    <!--spring boot打包的话需要指定一个唯一的入门 -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- 指定该Main Class为全局的唯一入口 -->
                    <mainClass>${BASE_PACKAGE}.${CAPITALIZED_CODE}Application</mainClass>
                    <classifier>exec</classifier>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>build-info</goal>
                            <goal>repackage</goal><!--可以把依赖的包都打包到生成的Jar包中 -->
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>pl.project13.maven</groupId>
                <artifactId>git-commit-id-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>revision</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <dotGitDirectory>\${project.basedir}/.git</dotGitDirectory>
                    <offline>true</offline>
                </configuration>
            </plugin>
        </plugins>
    </build>


</project>
EOF

    # 生成 server 模块的 application.yml
    print_info "生成 server 模块 application.yml..."
    cat > "$SERVER_DIR/src/main/resources/config/application.yml" << EOFAPP
my:
  project:
    name: ${PROJECT_CODE}
  chat:
    server:
      port: 29092

swagger: # 是否开启swagger
  enabled: true
  # 请求前缀
  pathMapping:
app:
  projectId: ${PROJECT_CODE}
  moduleId: \${spring.application.name}
  log:
    path: /data/log


spring:
  profiles:
    active: dev
  redis:
    host:
    port: 6379

oss:
  endpoint: http://localhost:\${server.port}
  bucket: \${spring.application.name}
  appKey:
  appSecret:
EOFAPP

    print_info "${PROJECT_CODE}-server 模块创建完成"

fi

# 11. 打印完成信息
echo ""
print_info "================================================"
print_info "        项目初始化完成！"
print_info "================================================"
echo ""
print_info "项目信息:"
print_info "  项目编码: $PROJECT_CODE"
print_info "  项目名称: $PROJECT_NAME"
print_info "  基础包名: $BASE_PACKAGE"
print_info "  项目目录: $PROJECT_DIR"
echo ""
print_info "已生成文件:"
print_info "  ✓ 项目目录结构"
print_info "  ✓ 模块 pom.xml"
print_info "  ✓ .gitignore"
print_info "  ✓ README.md"
print_info "  ✓ agent.md (AI开发指南)"
print_info "  ✓ 项目文档 (doc/)"
print_info "  ✓ 配置文件 (application*.yml)"
print_info "  ✓ ${PROJECT_CODE}-server 模块"
print_info "  ✓ ${CAPITALIZED_CODE}Application.java"
echo ""
print_info "下一步操作:"
print_info "  1. 如果根目录pom.xml已存在，请手动添加模块:"
print_info "     <module>${PROJECT_CODE}-common</module>"
print_info "     <module>${PROJECT_CODE}-server</module>"
print_info "  2. 配置数据库和Redis连接信息 (application-*.yml)"
print_info "  3. 运行 mvn clean install 编译项目"
print_info "  4. 启动应用: cd ${PROJECT_CODE}-server && mvn spring-boot:run"
print_info "  5. 开始开发业务功能"
echo ""
print_info "开发指南:"
print_info "  • 架构文档: doc/PROJECT_ARCHITECTURE.md"
print_info "  • 代码生成: doc/SQL代码生成指南.md"
print_info "  • AI助手: agent.md"
echo ""
print_info "================================================"
