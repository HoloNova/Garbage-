# slidtab-pro 改造实现计划

> 对应 spec：`docs/superpowers/specs/2026-07-03-slidtab-pro-revamp-design.md`
> 日期：2026-07-03
> 执行方式：按 Phase 顺序执行，每个后端 Phase 完成后跑 `mvn -q test` 验证；前端 Phase 跑构建验证。

## Phase 1 — 移除座位预约模块

### 任务
- 删除后端 5 文件：`entity/Seat.java`、`enums/SeatStatus.java`、`repository/SeatRepository.java`、`service/SeatService.java`、`controller/SeatController.java`。
- 删除前端：`frontend/src/views/SeatReserve.vue`。
- `frontend/src/router/index.js`：删 `{ path: 'seats', ... }` 路由项 + SeatReserve import。
- `frontend/src/api/index.js`：删 `listSeats`/`listAvailableSeats`/`occupySeat`/`releaseSeat`。
- `frontend/src/views/Home.vue`：删导航菜单 `/seats` 项（约 13-14、63 行）。
- `frontend/src/views/Profile.vue`：核对并清理 "seat" 字样。
- `src/test/java/.../SlidtabProApplicationTests.java`：删 SeatRepository/SeatService 注入、`seatOccupyAndRelease` 测试、`seat()` 工厂。
- `ARCHITECTURE.md`：删 Seat 相关段落（约 111、125、142、146、156、173 行）。
- `接口文档与设备对接说明.md`：删 4 个 `/api/seat` 接口（245-249 行）。

### 验证
- `mvn -q test` 编译通过、剩余测试绿。
- 前端 `npm run build`（或 dev 启动）无 SeatReserve 引用报错。

## Phase 2 — 数据模型与种子数据

### 任务
- `enums/ItemType.java`：删 `DEVICE`、`PACKAGE`，仅留 `BOOK`。
- `entity/Item.java`：加 `actionSequence`（TEXT/JPA `@Lob` 或 `@Column(columnDefinition="TEXT")`）字段 + getter/setter。
- `entity/BorrowRecord.java`：删 `pickupQrCode` 字段 + getter/setter。
- 若有 `schema.sql`：`item` 加 `action_sequence TEXT`、`borrow_record` 删 `pickup_qr_code`（H2 ddl-auto 则无需）。
- `config/DataInitializer.java`：新增播种 20 本书，每本随机 4–6 步动作序列（首步滑台定位、末步 arm 复位、中间随机），分配 slot；序列 JSON 用 ObjectMapper 序列化存入 actionSequence。
- 全局搜索 `pickupQrCode` / `pickup_qr_code` 引用并清理（DTO、Service、Controller 返回值）。

### 验证
- `mvn -q test` 绿。
- 启动后查 H2：item 表 20 本书、action_sequence 非空；borrow_record 无 pickup_qr_code 列。

## Phase 3 — 协议层 seq 关联

### 任务
- `dto/protocol/ControlCommand.java`：确认 `seq` 字段；`buildCommand` 生成唯一 seq（`System.currentTimeMillis() + "-" + AtomicCounter`）。
- `service/device/SlidtabTcpMessageHandler.java`：`handleControlResponse` 解析 `seq`，从 `ActionExecutor.pendingAcks` 取 future，按 `result_code` complete；无 seq/未知 seq 记 warn 丢弃。
- `ActionExecutor` 需暴露 `pendingAcks` 给 handler 访问（注入或静态 holder，本 Phase 先建空壳，Phase 4 填实现）。

### 验证
- `mvn -q test` 绿。
- 单测：`handleControlResponse` seq 关联成功、未知 seq 丢弃、无 seq 丢弃。

## Phase 4 — 动作执行引擎

### 任务
- 新建 `dto/protocol/ActionStep.java`（record：`device`、`cmd`(int)、`blocking`(boolean)）。
- 新建 `dto/protocol/AckResult.java`（record：`success`(boolean)、`message`(String)）。
- 新建 `service/control/ActionExecutor.java`：
  - `SingleThreadExecutor` 顺序执行。
  - `pendingAcks: ConcurrentHashMap<String, CompletableFuture<AckResult>>` + `pendingAcksByDevice: ConcurrentHashMap<String, Set<String>>`。
  - `execute(jobId, recordId, deviceId, List<ActionStep>)`：按步发送，blocking 步 `future.get(5s)`、失败重试 3 次（500ms/1s/2s 退避、新 seq），非 blocking 步 fire-and-forget。
  - `failPendingForDevice(deviceId)`：断连清理。
- 新建 `service/control/PickupJobStore.java`：`ConcurrentHashMap<Long recordId, PickupJob>` + 查询/更新。
- 新建 `dto/PickupJob.java`（jobId, recordId, deviceId, currentStep, totalSteps, status, message, startedAt）。
- `ControlService` 改造：构造仅 `TcpDeviceServer`+`ObjectMapper`；`dispatch` 返回 `CompletableFuture<AckResult>`；删 HTTP/模拟分支。
- `DeviceTcpConnection.onDisconnected` / `SlidtabTcpMessageHandler.onDisconnected`：调 `ActionExecutor.failPendingForDevice`。

### 验证
- `mvn -q test` 绿。
- 单测：ActionExecutor 成功/失败/超时/重试耗尽/设备未连接/断连清理。

## Phase 5 — 通信层精简（TCP-only）

### 任务
- 删 `service/registration/` 整目录。
- 删 `service/discovery/` 整目录。
- `ControlService`：删 `resolveTargetUrl`/`doHttpPost`/`parseResultCode`/`HttpClient`/`baseUrl`/`actuatorId`/`simulateMode` 及便捷方法 `dispatchTest`。
- `controller/ControlCommandController.java`：删 `/api/control/test`、`/api/control/config` 端点。
- `application.yaml`：删 `device.base-url`/`actuator-id`/`simulate-mode`/`register-enable`/`scan-enable`，保留 `device.tcp.*`。
- 全局搜引用清理（无残留 import）。

### 验证
- `mvn -q test` 绿。
- 启动日志无 registration/discovery 初始化。

## Phase 6 — 取件流程前后端

### 任务（后端）
- `service/BorrowService.java`：删 `pickup(String qrCode)`；新增 `pickup(Long recordId, Long userId)` → 校验 → 读 item.actionSequence 反序列化 → 建 PickupJob → 提交 ActionExecutor → 返回 job 摘要。
- `controller/BorrowController.java`：`POST /api/borrow/pickup/{recordId}` + `GET /api/borrow/pickup/{recordId}/status`。
- `GET /api/borrow/history/{userId}` 返回 DTO 删 pickupQrCode。

### 任务（前端）
- `frontend/src/api/index.js`：加 `pickupByRecord(recordId)` + `pickupStatus(recordId)`；删旧 `pickup(qrCode)`。
- `frontend/src/views/Pickup.vue`：删取件码输入/模拟扫码；借阅列表 RESERVED 项加"取件"按钮；点击 → 调 pickupByRecord → 弹窗进度条 + 1s 轮询 status → 成功刷新/失败可重试。
- `BookSearch.vue`：预约成功弹窗删取件码展示。

### 验证
- `mvn -q test` 绿。
- 前端构建无错。
- 手测：预约 → 取件 → 进度更新 → 状态迁移（需 TCP 设备或模拟器）。

## Phase 7 — 前端其他改造

### 任务
- `Home.vue`：删环境概览卡片整块。
- `Environment.vue`：deviceId 默认取首个 SENSOR 设备；空数据显 `<el-empty>`；动态分桶（≤2h 分钟/≤24h 小时/>24h 3小时，桶内均值）；catch 用 ElMessage.warning；历史 30s 轮询。
- `Admin.vue`："设备测试"Tab 改"设备调试"，内嵌 DeviceDebug 核心组件；删模拟测试元素（actuatorId/baseUrl/测试按钮）。
- `BookSearch.vue`：删类型切换，固定图书。
- Admin "库存"Tab：删 type 选择。
- 全站文案"物资"→"图书"。

### 验证
- 前端构建无错。
- 手测各页面。

## Phase 8 — 设备对接文档

### 任务
- 写 `docs/device-integration.md`：TCP 连接（设备主动 connect、按行 JSON）、四种消息格式（register/heartbeat/sensor/control_response）、回执必须带 seq、result_code 0000=成功、超时 5s 重试 3 次、命令编号表（arm 0-2、slide 0-3）、取件全流程收发示例。

### 验证
- 文档完整、与代码一致。

## Phase 9 — 全量测试验收

### 任务
- `mvn -q test` 全绿。
- 前端构建无错。
- 关键路径手测：图书查询 → 预约 → 取件（进度）→ 归还；环境检测实时+历史；Admin 调试台；TCP 设备连接稳定性。

### 验证
- 所有 Phase 任务勾选完成。
