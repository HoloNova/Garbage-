# 图书云柜 — API 接口文档

> 面向后端/前端开发者。描述前端 ↔ 后端 REST API 的全部端点、请求/响应格式与错误码。最后更新：2026-07-04。
>
> 设备 ↔ 后端 TCP 协议见 [device-integration.md](./device-integration.md)。架构总览见 [../ARCHITECTURE.md](../ARCHITECTURE.md)。

## 1. 通用约定

### 1.1 基础信息

| 项 | 值 |
|---|---|
| Base URL | `/api` |
| 协议 | HTTP/1.1 |
| 数据格式 | JSON（`Content-Type: application/json`） |
| 字符编码 | UTF-8 |
| 后端端口 | 9000（开发环境，前端 :9100 通过 vite 代理） |

### 1.2 响应信封 `ApiResponse<T>`

所有端点统一返回此结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { /* 业务数据，可为 null */ },
  "timestamp": 1751500000000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 业务码。`0` = 成功；非零 = 失败（见 [§1.4 错误码表](#14-错误码表)） |
| `message` | string | 描述信息。成功恒为 `"success"` |
| `data` | T \| null | 业务数据。失败时为 `null` |
| `timestamp` | long | 服务器时间戳（毫秒） |

### 1.3 HTTP 状态码

- **业务错误**：HTTP 200 + body `code` 非零（前端通过 `code` 判断结果，不依赖 HTTP 状态）
- **未捕获异常**：HTTP 500 + body `code=1007`（`GlobalExceptionHandler.handleOther` 兜底）
- **参数校验失败**：HTTP 200 + body `code=1001`（`MethodArgumentNotValidException`）
- **业务异常**：HTTP 200 + body `code=BusinessException.code`（`BusinessException` 抛出）

### 1.4 错误码表

与 `common/ResultCode` 枚举一致。

| code | 含义 | 触发场景 |
|---|---|---|
| 0 | 成功 | 所有成功响应 |
| 1001 | 参数错误 | `@Valid` 校验失败 / 动作序列 JSON 解析失败 |
| 1002 | 设备/记录/模板不存在 | `BorrowRecord` 找不到 / `ActionTemplate` 找不到 |
| 1003 | 设备离线 / 无权操作 | TCP 未连接 / `pickup` 用户不匹配 / `heartbeat` 未通过 TCP |
| 1004 | 超时 / 模板不存在 | `ActionTemplate` update/delete 找不到 |
| 1005 | 权限不足 | 预留 |
| 1006 | 库存不足 / 物资不可预约 | `reserve` 时 `Item.status ≠ AVAILABLE` |
| 1007 | 执行失败（含事务回滚） | `returnItem` job FAILED / `GlobalExceptionHandler` 兜底未捕获异常 |
| 1008 | 传感器异常 / 取件进行中 | `pickup` 重复触发同一 RUNNING job |
| 1009 | 柜体异常开启 / 动作序列未配置 | `pickup` 时 `actionSequence` 空 / `simulate-pickup` 未配置 |
| 1010 | 通信失败 | 预留 |
| 2001-2006 | 机械类错误 | 预留（固件侧上报） |
| 3001 | 温度超阈值 | `EnvironmentService` 检测 `temperature > 35.0` |
| 3002 | 湿度超阈值 | `EnvironmentService` 检测 `humidity > 80.0` 或 `< 30.0` |
| 3006 | 光照异常 | `EnvironmentService` 检测 `light > 5000.0` |
| 3007 | 称重超阈值 | `EnvironmentService` 检测 `weight > 50000.0` |
| 3008 | 烟雾超阈值 | `EnvironmentService` 检测 `smoke > 500.0` |

> 3001-3008 由 `EnvironmentService.checkThresholds` 在传感器上报时触发，调 `AlarmService.create` 生成 `PENDING` 告警记录。

---

## 2. 认证 `UserController` (`/api/user`)

### 2.1 POST `/api/user/login`

用户登录。**注意：本仓库未使用 token 鉴权**，登录返回的 `token` 字段前端实际不持久化，后续请求不携带 Authorization 头。前端 `stores/auth.js` 仅持久化用户对象到 `localStorage.bookcabinet_auth`。

**请求体** `LoginRequest`：

```json
{
  "phone": "13800000001",
  "studentId": "S001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | string | 是 | 手机号 |
| `studentId` | string | 是 | 学号/工号 |

**响应** `data: LoginResponse`：

```json
{
  "userId": "U001",
  "name": "管理员",
  "identity": "ADMIN",
  "token": "tk-U001-1751500000000"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` | string | 用户 ID（如 `U001`/`U002`/`U003`） |
| `name` | string | 用户名 |
| `identity` | string | 身份枚举：`ADMIN` / `USER` |
| `token` | string | 简易 token（格式 `tk-{userId}-{ts}`，前端实际不使用） |

**演示账号**（`DataInitializer` 播种）：

| userId | name | phone | studentId | identity |
|---|---|---|---|---|
| U001 | 管理员 | 13800000001 | S001 | ADMIN |
| U002 | 张三 | 13800000002 | S002 | USER |
| U003 | 李四 | 13800000003 | S003 | USER |

---

## 3. 物资查询 `QueryController` (`/api/query`)

### 3.1 GET `/api/query/device`

获取所有设备状态列表。

**响应** `data: List<DeviceStatusView>`：

```json
[
  {
    "deviceId": "esp8266_arm_01",
    "nodeType": "ARM",
    "online": true,
    "motorState": "idle",
    "cabinetDoor": "closed",
    "conveyorState": "stopped",
    "alarmState": "normal",
    "lastHeartbeat": "2026-07-04T10:30:00"
  }
]
```

### 3.2 GET `/api/query/device/{deviceId}`

获取单个设备状态。

**路径参数**：`deviceId` — 设备 ID（如 `esp8266_arm_01`）

**响应** `data: DeviceStatusView`：同 [3.1](#31-get-apidevice)

### 3.3 GET `/api/query/inventory`

搜索物资（图书）列表。

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 否 | 关键字（匹配标题/作者/分类） |
| `type` | ItemType | 否 | 物资类型枚举（当前仅 `BOOK`） |
| `status` | ItemStatus | 否 | 状态枚举：`AVAILABLE` / `RESERVED` / `BORROWED` |

**响应** `data: List<ItemView>`：

```json
[
  {
    "itemId": "BK001",
    "type": "BOOK",
    "title": "深入理解计算机系统",
    "author": "A. S. Tanenbaum",
    "category": "计算机",
    "status": "AVAILABLE",
    "slotId": "S01",
    "cabinetId": "cabinet_01"
  }
]
```

### 3.4 GET `/api/query/inventory/{itemId}`

获取单本图书详情。

**路径参数**：`itemId` — 物资 ID（如 `BK001`）

**响应** `data: ItemView`：同 [3.3](#33-get-apiqueryinventory) 单个元素

### 3.5 GET `/api/query/slots/{cabinetId}`

获取柜体下所有格口列表。

**路径参数**：`cabinetId` — 柜体 ID（如 `cabinet_01`）

**响应** `data: List<SlotView>`：

```json
[
  {
    "slotId": "S01",
    "status": "OCCUPIED",
    "testId": 1,
    "posX": 150,
    "posY": 200,
    "itemId": "BK001",
    "cabinetId": "cabinet_01"
  }
]
```

> `SlotView` 为扁平 DTO，避免 Slot↔Item 双向递归。`testId` 用于映射滑台 4 个位置（`slidePos = (testId - 1) % 4`）。

### 3.6 PUT `/api/query/slots/{slotId}/position`

更新格口坐标（管理员调试用）。

**路径参数**：`slotId` — 格口 ID（如 `S01`）

**请求体** `SlotPositionRequest`：

```json
{
  "testId": 1,
  "posX": 150,
  "posY": 200
}
```

**响应** `data: SlotView`：更新后的格口视图

### 3.7 GET `/api/query/env/latest`

获取设备最新一条环境数据。

**查询参数**：`deviceId` — 设备 ID（如 `esp8266_sensor_01`）

**响应** `data: EnvironmentData`：

```json
{
  "id": 1,
  "deviceId": "esp8266_sensor_01",
  "temperature": 25.6,
  "humidity": 60.2,
  "light": 320.0,
  "weight": 1.5,
  "smoke": 0.0,
  "recordedAt": "2026-07-04T10:30:00"
}
```

### 3.8 GET `/api/query/env/history`

获取设备环境历史数据。

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 设备 ID |
| `start` | ISO datetime | 是 | 起始时间（如 `2026-07-04T00:00:00`） |
| `end` | ISO datetime | 是 | 结束时间 |

> 前端 `Environment.vue` 使用本地 ISO 格式（不带时区 `Z` 后缀），避免后端解析报 400。

**响应** `data: List<EnvironmentData>`：按 `recordedAt` 升序排列

### 3.9 GET `/api/query/alarm`

获取告警列表。

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | AlarmStatus | 否 | `PENDING` / `RESOLVED`；不传则返回全部 |

**响应** `data: List<AlarmRecord>`：

```json
[
  {
    "id": 1,
    "alarmType": "TEMP_THRESHOLD",
    "alarmTime": "2026-07-04T10:30:00",
    "location": "环境监测节点",
    "deviceId": "esp8266_sensor_01",
    "status": "PENDING",
    "description": "温度超阈值: 36.5",
    "handler": null
  }
]
```

### 3.10 GET `/api/query/stats`

获取看板聚合统计。

**响应** `data: Map<String, Object>`：由 `StatisticsService.dashboard()` 聚合，含图书总数/可借数/已借数/告警数等。

---

## 4. 借还流程 `BorrowController` (`/api/borrow`)

### 4.1 POST `/api/borrow/reserve`

预约图书。校验物资可借后生成借阅记录，物资与格口状态置为 `RESERVED`。

**请求体** `ReserveRequest`：

```json
{
  "userId": "U002",
  "itemId": "BK001"
}
```

**响应** `data: ReserveResponse`：

```json
{
  "recordId": 1,
  "slotId": "S01",
  "cabinetId": "cabinet_01",
  "cabinetName": "智能图书柜",
  "cabinetLocation": "演示大厅",
  "expireTime": "2026-07-05T10:30:00"
}
```

> 无 `pickupQrCode` 字段（已移除）。预约后 24 小时内需调 `pickup` 取件。

### 4.2 POST `/api/borrow/pickup`

启动取件（异步 job 模型）。校验记录属于用户且状态 `RESERVED`，解析 `item.actionSequence` 提交 `ActionExecutor`，立即返回 job 摘要。前端轮询 `/pickup/{recordId}/status` 获取进度。

**请求体** `PickupRequest`：

```json
{
  "recordId": 1,
  "userId": "U002"
}
```

**响应** `data: PickupJob`：

```json
{
  "jobId": "PJ-ABC123DEF456",
  "recordId": 1,
  "currentStep": 0,
  "totalSteps": 5,
  "status": "RUNNING",
  "message": "已开始",
  "startedAt": "2026-07-04T10:30:00"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `jobId` | string | 任务 ID（`PJ-{uuid前12位}`） |
| `recordId` | long | 借阅记录 ID |
| `currentStep` | int | 当前步序号（0-based） |
| `totalSteps` | int | 总步数 |
| `status` | PickupJobStatus | `RUNNING` / `SUCCESS` / `FAILED` |
| `message` | string | 进度描述 |
| `startedAt` | datetime | 任务启动时间 |

**业务规则**：
- 同一 `recordId` 已有 `RUNNING` job 时返 1008
- `actionSequence` 为空时返 1009
- 用户不匹配时返 1003

### 4.3 GET `/api/borrow/pickup/{recordId}/status`

查询取件任务进度。前端 `Pickup.vue` 600ms 轮询此接口。

**路径参数**：`recordId` — 借阅记录 ID

**响应** `data: PickupJob`：同 [4.2](#42-post-apiborrowpickup)。若 job 不存在返回 `data: null`。

### 4.4 POST `/api/borrow/return`

归还图书（同步等待）。构造 5 步逆序动作序列提交 `ActionExecutor`，同步轮询 `PickupJobStore` 直到终态（90s 超时）。

> 前端 `Return.vue` 使用 120s 超时（`axios` 配置），后端 90s 超时。

**请求体** `ReturnRequest`：

```json
{
  "userId": "U002",
  "itemId": "BK001",
  "remark": "完好"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 是 | 用户 ID |
| `itemId` | string | 是 | 物资 ID |
| `remark` | string | 否 | 备注 |

**响应** `data: StatusReport`：

```json
{
  "protocol_version": "1.0",
  "msg_type": "status",
  "seq": "1751500000000",
  "timestamp": "2026-07-04T10:30:00",
  "source": "server",
  "target": "app",
  "device_id": "server",
  "status": "success",
  "online": true,
  "result_code": "0000",
  "result_msg": "return success",
  "inventory_state": {
    "item_id": "BK001",
    "slot_id": "S01",
    "state": "returned"
  }
}
```

**业务规则**：
- 未找到 `BORROWED` 状态记录返 1002
- 动作序列配置错误返 1009
- job FAILED 返 1007（含错误消息）
- 成功后 `BorrowRecord.RETURNED`、`Item.AVAILABLE`、`Slot.OCCUPIED`

### 4.5 GET `/api/borrow/history/{userId}`

获取用户借阅历史。

**路径参数**：`userId` — 用户 ID

**响应** `data: List<BorrowRecordView>`：

```json
[
  {
    "recordId": 1,
    "userId": "U002",
    "userName": "张三",
    "itemId": "BK001",
    "itemTitle": "深入理解计算机系统",
    "borrowTime": "2026-07-04T10:30:00",
    "returnTime": null,
    "status": "BORROWED"
  }
]
```

`status` 取值：`RESERVED` / `BORROWED` / `RETURNED`。

---

## 5. 设备控制 `ControlCommandController` (`/api/control`)

### 5.1 POST `/api/control/send`

下发控制指令（fire-and-forget）。设备开始执行即返回成功，不等待执行完成回执。

**请求体** `ControlCommand`：

```json
{
  "protocol_version": "1.0",
  "msg_type": "control",
  "seq": "1751500000000-1",
  "timestamp": "2026-07-04T10:30:00",
  "source": "server",
  "target": "esp8266_arm_01",
  "device_id": "esp8266_arm_01",
  "command": "START_ARM",
  "priority": 0,
  "timeout_ms": 5000,
  "params": {
    "cmd": 1
  }
}
```

**响应** `data: AckResult`：

```json
{
  "success": true,
  "message": "指令已下发"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | 是否发送成功 |
| `message` | string | 描述信息 |

**业务规则**：
- 设备未通过 TCP 连接 → `success=false, message="设备未连接: {deviceId}"`
- 发送失败 → `success=false, message="发送失败"`

### 5.2 POST `/api/control/heartbeat`

设备心跳。仅当设备真实 TCP 连接时才允许标记在线，否则返 1003。

**查询参数**：`deviceId` — 设备 ID

**响应** `data: String`：成功返 `"ok"`

**业务规则**：
- `tcpDeviceServer.isDeviceConnected(deviceId) === false` → 1003 "设备未通过 TCP 连接"

---

## 6. TCP 调试 `TcpCommandController` (`/api/control/tcp`)

供前端设备调试控制台使用。base 路径 `/api/control/tcp`。

### 6.1 GET `/api/control/tcp/devices`

获取所有已 TCP 连接的设备列表。

**响应** `data: Collection<Map<String, Object>>`：

```json
[
  {
    "deviceId": "esp8266_arm_01",
    "nodeType": "ARM",
    "ip": "192.168.1.50",
    "port": 43210,
    "connected": true,
    "identified": true,
    "responseCount": 5
  }
]
```

### 6.2 POST `/api/control/tcp/send-raw`

向指定设备发送原始文本（不经过业务层包装，直接写入 TCP socket）。

**请求体**：

```json
{
  "deviceId": "esp8266_arm_01",
  "content": "{\"msg_type\":\"ping\"}"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 设备 ID |
| `content` | string | 是 | 原始文本内容 |

**响应** `data: Map`：

```json
{
  "deviceId": "esp8266_arm_01",
  "sent": true,
  "length": 22
}
```

**业务规则**：
- `deviceId` 为空 → 1001
- `content` 为空 → 1001
- 设备不在线 → 1003

### 6.3 POST `/api/control/tcp/broadcast`

向所有已连接设备广播原始文本。

**请求体**：

```json
{
  "content": "{\"msg_type\":\"ping\"}"
}
```

**响应** `data: Map`：

```json
{
  "sent": true,
  "deviceCount": 3
}
```

### 6.4 GET `/api/control/tcp/responses`

获取指定设备的响应缓存（最近 N 条 `control_response` / 上行消息）。

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 设备 ID |
| `limit` | int | 否 | 返回最近 N 条，默认 50 |

**响应** `data: List<DeviceResponse>`：设备响应列表

### 6.5 DELETE `/api/control/tcp/responses`

清空指定设备的响应缓存。

**查询参数**：`deviceId` — 设备 ID

**响应** `data: String`：`"ok"`

### 6.6 POST `/api/control/tcp/test`

测试设备 TCP 连接状态。**仅检查连接状态，不向设备发送任何数据**（避免触发设备端误动作）。

**查询参数**：`deviceId` — 设备 ID

**响应** `data: Map`：

```json
{
  "deviceId": "esp8266_arm_01",
  "connected": true,
  "message": "TCP 连接正常"
}
```

`connected=false` 时 `message="设备未连接"`。

### 6.7 POST `/api/control/tcp/simulate-pickup`

模拟取货：根据图书的 `actionSequence` 直接派发动作序列到 `ActionExecutor`，不创建借阅记录、不更新库存。`recordId=0` 表示模拟任务。

**查询参数**：`itemId` — 物资 ID（如 `BK001`）

**响应** `data: Map`：

```json
{
  "jobId": "SIM-ABC123DEF456",
  "steps": 5,
  "message": "取件已启动"
}
```

**业务规则**：
- `actionSequence` 为空 → 1009
- 派发前校验所有步骤涉及的设备是否已连接，未连接立即返 1003（避免异步失败无反馈）：

```json
{
  "code": 1003,
  "message": "设备未就绪，请先连接设备: esp8266_arm_01, esp8266_mse_01"
}
```

### 6.8 GET `/api/control/tcp/simulate-pickup/status`

查询模拟取货任务进度。前端轮询此接口驱动进度面板 UI。

**查询参数**：`jobId` — 任务 ID（由 [6.7](#67-post-apicontroltcpsimulate-pickup) 返回）

**响应** `data: Map`：

```json
{
  "jobId": "SIM-ABC123DEF456",
  "status": "RUNNING",
  "currentStep": 2,
  "totalSteps": 5,
  "message": "执行第 3/5 步: esp8266_arm_01 cmd=1"
}
```

**业务规则**：
- `jobId` 不存在 → 1002 "任务不存在: {jobId}"

> 注意：`PickupJobStore` 同时按 `recordId` 和 `jobId` 建立索引。模拟取货 `recordId=0` 会冲突，必须用 `jobId` 查询。

---

## 7. 动作模板 `ActionTemplateController` (`/api/action-template`)

管理员可视化编排设备动作链。模板不绑定图书，可独立调用 `/run` 派发到 `ActionExecutor` 执行。

### 7.1 GET `/api/action-template`

获取所有动作模板。

**响应** `data: List<ActionTemplate>`：

```json
[
  {
    "id": 1,
    "name": "标准取件序列",
    "description": "滑台定位→机械臂抓取→滑台回出货口→机械臂放置→机械臂复位",
    "sequenceJson": "[{\"device\":\"esp8266_mse_01\",\"cmd\":1,\"blocking\":true},...]"
  }
]
```

### 7.2 POST `/api/action-template`

创建动作模板。

**请求体** `ActionTemplateRequest`：

```json
{
  "name": "标准取件序列",
  "description": "滑台定位→机械臂抓取→滑台回出货口→机械臂放置→机械臂复位",
  "sequenceJson": "[{\"device\":\"esp8266_mse_01\",\"cmd\":1,\"blocking\":true},{\"device\":\"esp8266_arm_01\",\"cmd\":1,\"blocking\":true},{\"device\":\"esp8266_mse_01\",\"cmd\":0,\"blocking\":true},{\"device\":\"esp8266_arm_01\",\"cmd\":2,\"blocking\":true},{\"device\":\"esp8266_arm_01\",\"cmd\":0,\"blocking\":true}]"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 模板名（唯一） |
| `description` | string | 否 | 描述 |
| `sequenceJson` | string | 是 | `ActionStep` 列表的 JSON 字符串 |

**响应** `data: ActionTemplate`：创建后的模板

**业务规则**：
- 模板名已存在 → 1002
- `sequenceJson` 解析失败或为空 → 1001

### 7.3 PUT `/api/action-template/{id}`

更新动作模板。

**路径参数**：`id` — 模板 ID

**请求体**：同 [7.2](#72-post-apiaction-template)

**响应** `data: ActionTemplate`：更新后的模板

**业务规则**：
- 模板不存在 → 1004
- `sequenceJson` 解析失败 → 1001

### 7.4 DELETE `/api/action-template/{id}`

删除动作模板。

**路径参数**：`id` — 模板 ID

**响应** `data: String`：`"ok"`

**业务规则**：
- 模板不存在 → 1004

### 7.5 POST `/api/action-template/{id}/run`

立即试运行：解析模板动作序列并派发到 `ActionExecutor`。

**路径参数**：`id` — 模板 ID

**响应** `data: Map`：

```json
{
  "jobId": "TPL-ABC123DEF456",
  "steps": 5,
  "message": "模板动作序列已派发，请观察设备动作"
}
```

**业务规则**：
- 模板不存在 → 1004
- 模板未配置动作序列 → 1009

---

## 8. 设备上报 `DeviceUploadController` (`/api/upload`)

HTTP 上报端点，与 TCP `sensor`/`status`/`alarm` 等价，供非 TCP 接入方式。所有端点请求体为 `StatusReport`。

### 8.1 POST `/api/upload/sensor`

上报传感器数据。调 `EnvironmentService.record` 录入并检查阈值。

**请求体** `StatusReport`：

```json
{
  "protocol_version": "1.0",
  "msg_type": "sensor",
  "seq": "1751500000-1",
  "timestamp": "2026-07-04T10:30:00",
  "source": "esp8266_sensor_01",
  "target": "server",
  "device_id": "esp8266_sensor_01",
  "status": "success",
  "sensor_data": {
    "temperature": 25.6,
    "humidity": 60.2,
    "light": 320.0,
    "weight": 1.5,
    "smoke": 0.0
  }
}
```

**响应** `data: StatusReport`：原样返回上报数据

### 8.2 POST `/api/upload/status`

上报设备状态。调 `DeviceService.updateFromReport` 更新设备状态。

**请求体** `StatusReport`：含 `online`、`device_state`（柜门/电机/传送带/格口/告警状态）等字段

**响应** `data: StatusReport`：原样返回

### 8.3 POST `/api/upload/alarm`

上报设备告警。调 `DeviceService.updateFromReport` 更新设备状态。

**请求体** `StatusReport`：含 `result_msg`、`device_state.alarm_state` 等字段

**响应** `data: StatusReport`：原样返回

---

## 9. 告警处理 `AlarmController` (`/api/alarm`)

### 9.1 POST `/api/alarm/{id}/handle`

处理告警（PENDING → RESOLVED）。

**路径参数**：`id` — 告警记录 ID

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `handler` | string | 是 | 处理人 |
| `description` | string | 否 | 处理说明（覆盖原描述） |

**响应** `data: AlarmRecord`：处理后的告警记录

**业务规则**：
- 告警不存在 → 抛 `IllegalArgumentException`（被 `GlobalExceptionHandler` 兜底为 1007）

---

## 10. 客户端 IP `ClientIpController` (`/api`)

### 10.1 GET `/api/client-ip`

获取当前请求客户端的真实 IP 地址。

**响应** `data: String`：客户端 IP（如 `192.168.1.100`）

**IP 提取顺序**：
1. `X-Forwarded-For` 头（取第一个，逗号分隔）
2. `X-Real-IP` 头
3. `request.getRemoteAddr()`（本地 IPv6 环回 `::1` / `0:0:0:0:0:0:0:1` 统一为 `127.0.0.1`）

---

## 附录 A：动作序列格式

`Item.actionSequence` 字段与 `ActionTemplate.sequenceJson` 字段均为 `ActionStep` 列表的 JSON 字符串：

```json
[
  {"device": "esp8266_mse_01", "cmd": 2, "blocking": true},
  {"device": "esp8266_arm_01", "cmd": 1, "blocking": true},
  {"device": "esp8266_mse_01", "cmd": 0, "blocking": true},
  {"device": "esp8266_arm_01", "cmd": 2, "blocking": true},
  {"device": "esp8266_arm_01", "cmd": 0, "blocking": true}
]
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `device` | string | 目标设备 ID |
| `cmd` | int | 命令编号（机械臂 0-2，滑台 0-3） |
| `blocking` | boolean | `true` = 发送后等待 4s 让设备执行；`false` = 发完即继续 |

**取件 5 步序列**（`DataInitializer.pickupActionSequence`）：
1. 滑台移动到目标格口（`cmd = slotIndex % 4`）
2. 机械臂抓取（`cmd=1`）
3. 滑台回出货口（`cmd=0`）
4. 机械臂放置（`cmd=2`）
5. 机械臂复位（`cmd=0`）

**归还 5 步逆序序列**（`BorrowService.buildReturnActionSequence`）：
1. 机械臂抓取（`cmd=1`）
2. 滑台移动到目标格口（`cmd = (testId - 1) % 4`）
3. 机械臂放置（`cmd=2`）
4. 滑台回出货口（`cmd=0`）
5. 机械臂复位（`cmd=0`）

## 附录 B：设备清单

| 设备 ID | 类型 | 命令范围 | 职责 |
|---|---|---|---|
| `esp8266_arm_01` | 机械臂 | cmd 0-2（0=复位/1=抓取/2=放置） | 抓取/放置图书 |
| `esp8266_mse_01` | 滑台 | cmd 0-3（移动到格口位） | 定位到指定格口 |
| `esp8266_sensor_01` | 传感组 | 无指令 | 常驻上报温/湿/光/称重/烟雾 |

## 附录 C：相关文档

- [../ARCHITECTURE.md](../ARCHITECTURE.md) — 系统架构总览
- [device-integration.md](device-integration.md) — 设备 TCP 协议与对接指南
- [`common/ResultCode.java`](../slidtab-pro/src/main/java/com/slidtable/slidtab_pro/common/ResultCode.java) — 错误码枚举源
- [`common/GlobalExceptionHandler.java`](../slidtab-pro/src/main/java/com/slidtable/slidtab_pro/common/GlobalExceptionHandler.java) — 异常处理源
