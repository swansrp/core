# 项目架构文档

> 本文档描述项目的技术架构、开发规范和最佳实践，适用于所有基于此架构的项目。

## 目录

- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [后端架构](#后端架构)
- [前端架构](#前端架构)
- [开发规范](#开发规范)
- [常见问题](#常见问题)

---

## 项目结构

### 整体结构

```
project-root/
├── project-api/           # 后端服务（Spring Boot）
│   ├── core/             # 核心模块
│   ├── project-common/   # 公共模块（实体类、VO）
│   ├── project-enterprise/ # 企业端模块
│   ├── project-manage/   # 管理端模块
│   └── project-portal/   # 入口模块
└── project-view/         # 前端项目（Vue 3 + TypeScript）
    ├── src/
    │   ├── apis/        # API 接口（自动生成）
    │   ├── views/       # 页面组件
    │   ├── framework/   # 框架组件
    │   └── main.ts
    └── package.json
```

### 模块职责

- **core**: 核心功能模块（认证、权限、数据库、缓存等）
- **common**: 公共实体类、枚举、工具类
- **enterprise**: 企业端业务模块
- **manage**: 管理端业务模块
- **portal**: 应用启动入口

---

## 技术栈

### 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.x | Web 框架 |
| MyBatis-Plus | 3.x | ORM 框架 |
| Swagger | 2.x | API 文档生成 |
| Redis | - | 缓存 |
| MySQL | 8.x | 关系型数据库 |

### 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.x | 渐进式框架 |
| TypeScript | 4.x | 类型安全 |
| Ant Design Vue | 3.x | UI 组件库 |
| Vite | - | 构建工具 |

---

## 后端架构

### 1. 分层架构

```
Controller (控制层)
    ↓
Service (服务层)
    ↓
Repository (数据访问层)
    ↓
Database (数据库)
```

### 2. Controller 层规范

#### 基本结构

```java
@Api(tags = "模块名称")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/module"})
public class ModuleController {
    
    private final ModuleService moduleService;
    
    @ApiOperation("操作描述")
    @PostMapping("/action")
    public void actionMethod(@RequestParam Long id) {
        moduleService.doAction(id);
        Resp.notice("操作成功");  // ✅ 无返回值时使用 Resp.notice()
    }
}
```

#### 返回值规范

- **有数据返回**: 直接返回对象或列表
- **无数据返回**: 使用 `Resp.notice("提示消息")`，返回类型为 `void`

```java
// ✅ 正确：有数据返回
@GetMapping("/detail")
public UserVO getDetail(Long id) {
    return userService.getDetail(id);
}

// ✅ 正确：无数据返回
@PostMapping("/delete")
public void delete(Long id) {
    userService.delete(id);
    Resp.notice("删除成功");
}

// ❌ 错误：不要使用泛型返回值
@PostMapping("/delete")
public Resp<String> delete(Long id) {  // ❌ Resp 不是泛型类
    // ...
}
```

### 3. Service 层规范

#### 数据校验

使用 `Validator` 工具类进行数据校验，统一错误处理：

```java
@Service
@RequiredArgsConstructor
public class ModuleService {
    
    private final ModuleRepository moduleRepository;
    
    public void updateData(Long id, UpdateReq req) {
        // ✅ 使用 Validator 断言
        Module entity = moduleRepository.selectById(id);
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "数据");
        
        // 业务逻辑
        entity.setName(req.getName());
        moduleRepository.updateById(entity);
    }
}
```

#### 常用 Validator 方法

```java
// 非空校验
Validator.assertNotNull(obj, ErrCodeSys.PA_DATA_NOT_EXIST, "对象名");
Validator.assertNotBlank(str, ErrCodeSys.PA_PARAM_NULL, "参数名");
Validator.assertNotEmpty(list, ErrCodeSys.PA_DATA_NOT_EXIST, "列表名");

// 条件校验
Validator.assertTrue(condition, ErrCodeSys.SYS_ERR_MSG, "错误提示");
Validator.assertFalse(condition, ErrCodeSys.SYS_ERR_MSG, "错误提示");

// 相等校验
Validator.assertEquals(obj1, obj2, ErrCodeSys.PA_DATA_DIFF, "数据名");
Validator.assertNotEquals(obj1, obj2, ErrCodeSys.SYS_ERR_MSG, "数据名");
```

#### 常用错误码

| 错误码 | 说明 | 使用场景 |
|--------|------|----------|
| `ErrCodeSys.PA_DATA_NOT_EXIST` | 数据不存在 | 数据库查询结果为空 |
| `ErrCodeSys.PA_DATA_HAS_EXIST` | 数据已存在 | 重复数据校验 |
| `ErrCodeSys.PA_PARAM_NULL` | 参数为空 | 必填参数校验 |
| `ErrCodeSys.SYS_ERR_MSG` | 系统错误 | 通用业务逻辑错误 |

### 4. 用户上下文

获取当前登录用户：

```java
String currentUser = AccountContext.getOperator();
```

### 5. 对象转换

使用 `ReflectionUtil` 和 `Resp.convert()` 进行对象转换：

```java
// Entity -> VO
EntityVO vo = ReflectionUtil.copy(entity, EntityVO.class);

// List<Entity> -> List<VO>
List<EntityVO> voList = Resp.convert(entityList, EntityVO.class);
```

### 6. 枚举字典

使用枚举管理状态字典：

```java
@Getter
@RequiredArgsConstructor
@MetaDict(value = "APPROVAL_DICT", remark = "审批状态字典")
public enum ApprovalDict implements Dict {
    UNKNOWN("0", "未提交"),
    APPLY("1", "待审核"),
    REJECT("2", "未通过"),
    APPROVAL("3", "已通过");
    
    private final String value;
    private final String label;
}
```

使用方式：

```java
entity.setStatus(ApprovalDict.APPROVAL.getValue());
```

---

## 前端架构

### 1. 目录结构

```
src/
├── apis/                 # API 接口（自动生成）
│   ├── moduleController.ts
│   └── types/
│       └── moduleControllerTypes.ts
├── views/               # 页面组件
│   └── module/
│       ├── index.vue
│       └── components/
├── framework/           # 框架组件
│   ├── components/
│   └── apis/
└── main.ts
```

### 2. API 自动生成

#### 生成流程

1. 后端启动，Swagger 生成 API 文档
2. 前端运行命令生成 API：

```bash
npm run generate-api
```

3. 自动生成的 API 文件：
   - `src/apis/moduleController.ts` - API 方法
   - `src/apis/types/moduleControllerTypes.ts` - 类型定义

#### API 调用示例

```typescript
import { getMyEnterprises } from '@/apis/enterpriseController'
import type { EnterpriseVO } from '@/apis/types'

const loadData = async () => {
  try {
    // 参数说明：showSuccess, showLoading, showErr
    const res = await getMyEnterprises(false, false, true)
    const data: EnterpriseVO[] = res.payload || []
  } catch (error) {
    console.error(error)
  }
}
```

#### API 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `showSuccess` | `true` | 成功时是否显示提示 |
| `showLoading` | `false` | 是否显示 loading |
| `showErr` | `true` | 失败时是否显示错误 |

**常用场景**：
- 查询数据：`(false, false, true)` - 不显示成功，显示错误
- 提交表单：`(true, false, true)` - 显示成功和错误
- 长时间操作：`(true, true, true)` - 全部显示

#### API 使用注意事项

⚠️ **重要** **API 调用最佳实践**
   - ✅ 阅读接口注释，了解返回值结构
   - ✅ 根据实际需求选择合适的查询接口补充数据
   - ✅ 使用唯一标识（如 ID、编码、批号等）进行精确查询
   - ❌ 不要假设所有接口都返回完整的业务数据
   - ❌ 不要忽略接口注释中的特殊说明

### 3. Portal 组件使用

#### Portal 表格刷新

在管理端使用 `<portal>` 组件时，执行增删改操作后需要刷新表格数据：

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { someApi } from '@/apis/someController'

const portalRef = ref()

// 操作后刷新表格
const handleDelete = async (id: number) => {
  await someApi({ id })
  // ✅ 使用 portalRef 刷新表格数据
  portalRef.value.queryData()
  
  // ❌ 不要使用 window.location.reload()
  // window.location.reload()  // 全局刷新，体验不好
}
</script>

<template>
  <portal 
    ref="portalRef"
    table-id="module"
  >
    <template #action="{ record }">
      <a-button @click="handleDelete(record.id)">删除</a-button>
    </template>
  </portal>
</template>
```

**重要提示**：
- ✅ **使用** `portalRef.value.queryData()` - 局部刷新，保持用户状态
- ❌ **不要使用** `window.location.reload()` - 全局刷新，体验差

### 4. Vue 组件规范

```vue
<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import type { SomeType } from '@/apis/types'

// 响应式数据
const data = ref<SomeType[]>([])
const loading = ref(false)

// 方法
const loadData = async () => {
  loading.value = true
  // ... 逻辑
  loading.value = false
}

// 生命周期
onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="container">
    <!-- 模板内容 -->
  </div>
</template>

<style scoped lang="less">
.container {
  /* 样式 */
}
</style>
```

#### 类型安全

```typescript
// ✅ 正确：使用类型注解
const data = ref<EnterpriseVO[]>([])
const currentItem = ref<EnterpriseVO | null>(null)

// ❌ 错误：使用 any
const data = ref<any[]>([])
```

#### Props 类型传递

```vue
<script setup lang="ts">
import type { EnterpriseVO } from '@/apis/types'

const props = defineProps<{
  enterprise?: EnterpriseVO  // 可选
}>()

// ✅ 传递时避免 null
<SomeComponent :enterprise="currentItem ?? undefined" />

// ❌ 不要传递 null（可能导致 TS 类型错误）
<SomeComponent :enterprise="currentItem" />  // 当 currentItem 为 null 时
</script>
```

### 4. Ant Design Vue 使用

#### 图标使用

```vue
<script setup lang="ts">
import { h } from 'vue'  // ✅ 必须导入 h 函数
import { PlusOutlined, EditOutlined } from '@ant-design/icons-vue'

// 在模板中使用
<a-button :icon="h(PlusOutlined)">添加</a-button>
</script>
```

#### 响应式网格

```vue
<a-row :gutter="[16, 16]">
  <a-col
    :xs="24"   <!-- 手机：1列 -->
    :sm="12"   <!-- 小屏：2列 -->
    :md="12"   <!-- 中屏：2列 -->
    :lg="8"    <!-- 大屏：3列 -->
    :xl="6"    <!-- 超大屏：4列 -->
  >
    <!-- 内容 -->
  </a-col>
</a-row>
```

### 5. 状态码映射

前端状态码必须与后端枚举一致：

```vue
<script setup lang="ts">
// 后端枚举：
// UNKNOWN("0", "未提交")
// APPLY("1", "待审核")
// REJECT("2", "未通过")
// APPROVAL("3", "已通过")

const getStatusTag = (status: string) => {
  const statusMap = {
    '0': { text: '未提交', color: 'default' },
    '1': { text: '待审核', color: 'processing' },
    '2': { text: '未通过', color: 'error' },
    '3': { text: '已通过', color: 'success' }
  }
  return statusMap[status] || statusMap['0']
}
</script>
```

### 6. 页面与组件使用约定

1. 左右分栏页面尽量使用 `ContentLayout` 组件实现布局，统一页面结构和自适应行为。
2. 表格类数据展示尽量使用 `Portal` 组件，复用统一的查询、筛选、导出等能力。
3. 当通过 `advanceCondition` / 查询条件控制 Portal 数据时，不需要在业务页面中额外 `watch` 条件变化，Portal 组件内部会自动监听并触发刷新。

---

## 开发规范

### 1. 代码风格

#### 命名规范

- **Java 类名**: PascalCase（`UserService`）
- **Java 方法**: camelCase（`getUserById`）
- **Vue 组件**: PascalCase（`EnterpriseList.vue`）
- **Vue 方法**: camelCase（`handleSubmit`）

#### 注释规范

```java
/**
 * 方法功能描述
 *
 * @param id 参数说明
 * @return 返回值说明
 */
public UserVO getUserById(Long id) {
    // 实现
}
```

### 2. Git 提交规范

```bash
# 功能开发
feat: 添加企业审批功能

# Bug 修复
fix: 修复企业列表加载失败问题

# 样式调整
style: 调整企业卡片布局

# 文档更新
docs: 更新架构文档
```

### 3. 错误处理

#### 后端

```java
// ✅ 使用 Validator
Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "企业");

// ❌ 不要使用 RuntimeException
if (entity == null) {
    throw new RuntimeException("企业不存在");  // ❌
}
```

#### 前端

```typescript
// ✅ 使用 try-catch
try {
  await someApi()
} catch (error) {
  console.error('操作失败:', error)
  // API 框架会自动显示错误提示
}
```

---

## 常见问题

### 1. Flex 布局溢出

#### 问题

父容器设置 `width: 100%` 导致子元素溢出。

#### 解决方案

```css
.parent {
  display: flex;
  min-width: 0;           /* ✅ 允许 flex 子元素收缩 */
  overflow: hidden;       /* ✅ 防止溢出 */
}

.child {
  flex: 1;
  min-width: 0;           /* ✅ 允许收缩 */
  overflow-x: hidden;     /* ✅ 隐藏横向滚动 */
  overflow-y: auto;       /* ✅ 允许纵向滚动 */
}
```

### 2. 页面宽度自适应

#### 框架插槽布局

```css
/* 父容器（框架） */
.content {
  display: flex;
  min-width: 0;
  overflow: hidden;
}

/* 子页面 */
.page-container {
  /* 不要设置固定宽度，自动填充 */
  padding: 24px;
}
```

### 3. Vue 组件 TS 类型错误

#### 问题

传递 `null` 值导致类型错误。

#### 解决方案

```typescript
// ✅ 使用 ?? undefined
<Component :data="nullableValue ?? undefined" />

// ❌ 直接传递 null
<Component :data="nullableValue" />  // 可能为 null
```

### 4. API 生成失败

#### 排查步骤

1. 确认后端服务已启动
2. 访问 Swagger 文档：`http://localhost:8080/swagger-ui.html`
3. 检查 `package.json` 中的 `generate-api` 命令配置
4. 查看生成日志是否有错误

---

## 附录

### A. 常用命令

#### 前端

```bash
# 安装依赖
npm install

# 开发运行
npm run dev

# 生成 API
npm run generate-api

# 构建生产
npm run build
```

#### 后端

```bash
# Maven 编译
mvn clean install

# 启动服务
mvn spring-boot:run

# 跳过测试
mvn clean install -DskipTests
```

### B. 环境配置

#### 前端环境变量

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080

# .env.production
VITE_API_BASE_URL=https://api.production.com
```

#### 后端配置文件

```yaml
# application.yml
spring:
  profiles:
    active: dev  # dev / test / prod

# application-dev.yml
server:
  port: 8080
```

---

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-01-09 | 1.0.0 | 初始版本 |

---

**注意**：本文档持续更新，如有问题请及时反馈。
