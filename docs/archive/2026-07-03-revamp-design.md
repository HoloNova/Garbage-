# slidtab-pro 改造设计

> 日期：2026-07-03
> 状态：待审阅
> 范围：将系统聚焦"取书"场景，精简物资为图书、引入种子数据与动作执行引擎、改造取件流程、通信层仅保留 TCP、修复环境检测、清理设备管理页与场景文案，并产出设备对接文档。

## 1. 背景与目标

当前系统混杂了图书 / 设备 / 学习包三类物资、座位预约、模拟测试元素，且动作指令"发完即返回"不等设备回执，环境历史趋势图因无数据/渲染问题空白。本次改造目标是：

- 聚焦"取书"演示场景，物资统一为图书。
- **移除座位预约模块**（前后端 + 路由 + 文档全部清理）。
- 引入种子图书数据，每本带随机生成的动作序列。
- 设计基于设备回执（ack）的动作执行引擎，支持阻塞/异步混合、超时、重试，保证稳定性。
- 取件从"取件码"改为"借阅列表直接点击取件 → 下发动作序列"。
- 通信层仅保留 TCP（设备主动连接），移除注册/扫描/base-url/模拟等其他路径。
- 修复环境检测历史趋势渲染，移除首页环境概览。
- 产出设备对接文档。

**非目标**：不播种环境历史数据（只来自真实设备上报）；不引入持久化 job 表（内存态即可）。

## 2. 总体架构与范围

### 移除

- `ItemType.DEVICE`、`ItemType.PACKAGE`（仅保留 `BOOK`）。
- `service/registration/DeviceRegistrationService` 整目录。
- `service/discovery/` 整目录（DeviceScanner / DeviceRegistry / ScanScheduler / NetworkHelper）。
- `ControlService` 的 base-url / actuator-id / simulate-mode 配置与 HTTP 路由分支、`resolveTargetUrl` / `doHttpPost` / `parseResultCode`、`HttpClient` 字段。
- `/api/control/test`、`/api/control/config`（base-url/actuator-id 更新）端点。
- `BorrowRecord.pickupQrCode` 字段及 `BorrowService.pickup(qrCode)`。
- Admin "设备测试"Tab 的模拟测试元素（actuatorId/baseUrl 输入、"测试滑台/测试机械臂"按钮）。
- Home.vue 环境概览卡片整块。

### 保留并增强

- TCP 直连：`TcpDeviceServer` / `DeviceTcpConnection` / `TcpMessageHandler` 传输层不动。
- `SlidtabTcpMessageHandler`：增加 seq 关联回执。
- `EnvironmentService` / `DeviceService` / `TcpCommandController`（调试控制台用）。
- 座位预约模块（不动）。

### 新增

- `ActionStep` 值对象（device、cmd、blocking）。
- `ActionExecutor`：seq 关联 + 阻塞/异步 + 超时 + 重试的顺序执行器。
- 内存态 `PickupJob` 状态机 + 查询端点。
- `Item.actionSequence` 字段（JSON）。
- `docs/device-integration.md` 设备对接文档。

## 3. 数据模型与种子数据

### 3.1 ItemType 收敛

`ItemType` 枚举仅保留 `BOOK("图书")`，删除 `DEVICE`、`PACKAGE`。全站物资类型固定为图书。

### 3.2 Item 字段

`Item` 新增 `actionSequence`（TEXT，存 JSON 数组），形如：

```json
[
  {"device":"esp8266_mse_01","cmd":3,"blocking":true},
  {"device":"esp8266_arm_01","cmd":2,"blocking":true},
  {"device":"esp8266_arm_01","cmd":0,"blocking":true},
  {"device":"esp8266_mse_01","cmd":1,"blocking":true}
]
```

每本书一条独立动作序列。

### 3.3 DataInitializer 播种

- 保留现有 3 个演示用户。
- 新增播种 20 本书：title/author 随机组合；每本随机生成 4–6 步动作序列，从机械臂 {0,1,2} 与滑台 {0,1,2,3} 中随机抽取；建议首步用滑台定位、末步用机械臂复位（演示更顺），中间随机；分配到不同 slot。
- DDL：`item` 表加 `action_sequence TEXT` 列；`borrow_record` 表删除 `pickup_qr_code` 列。（H2 自动建表则更新 schema.sql / JPA 实体即可。）

### 3.4 BorrowRecord

移除 `pickup_qr_code` 列与对应实体字段。状态机不变：RESERVED → PICKED\_UP → RETURNED。

## 4. 动作指令协议

### 4.1 命令编号（设备固件规定，不可修改）

机械臂 `esp8266_arm_01`：

| cmd | 含义      |
| --- | ------- |
| 0   | 机械臂伸直复位 |
| 1   | 机械臂轻度弯曲 |
| 2   | 机械臂中度弯曲 |

滑台 `esp8266_mse_01`：

| cmd | 含义                      |
| --- | ----------------------- |
| 0   | 滑台1、2 都向前转 6000         |
| 1   | 滑台1、2 都向后转 6000         |
| 2   | 滑台1 向前 3000、滑台2 向后 3000 |
| 3   | 滑台1 向后 3000、滑台2 向前 3000 |

传感组 `esp8266_sensor_01`：常驻接收传感器上报，不下发指令。

### 4.2 下行指令格式（server → device）

复用现有 `ControlCommand`，强化 `seq` 唯一性：

```json
{
  "version": "1.0",
  "msg_type": "control",
  "seq": "<唯一seq>",
  "device_id": "esp8266_arm_01",
  "command": "START_ARM",
  "params": {"cmd": 2},
  "timeout_ms": 5000
}
```

- `seq`：`System.currentTimeMillis() + "-" + atomicCounter`，全局唯一。
- `command`：机械臂用 `START_ARM`，滑台用 `MOVE_TO_SLOT`（沿用枚举，语义对设备透明）。
- `params.cmd`：整数命令编号。

### 4.3 上行回执格式（device → server）

复用 `msg_type=control_response`，**强制带 seq**：

```json
{
  "msg_type": "control_response",
  "seq": "<对应下行seq>",
  "device_id": "esp8266_arm_01",
  "result_code": "0000",
  "result_msg": "ok"
}
```

- `result_code=0000` 成功；非 0000 失败；超时无回执视为失败。

## 5. 动作执行引擎（方案 A）

### 5.1 组件

- `ActionExecutor`：`SingleThreadExecutor` 顺序执行器，吃 `List<ActionStep>` + `jobId`。
- `pendingAcks: ConcurrentHashMap<String seq, CompletableFuture<AckResult>>`。
- `PickupJobStore`：`ConcurrentHashMap<Long recordId, PickupJob>`，内存态。
- `PickupJob`：`jobId, recordId, currentStep, totalSteps, status(RUNNING/SUCCESS/FAILED), message, startedAt`。

### 5.2 seq 关联

`SlidtabTcpMessageHandler.handleControlResponse` 改造：

1. 解析 `seq`；无 seq → 记 warn 丢弃。
2. 从 `pendingAcks` 取 future；未知 seq → 记 warn 丢弃。
3. `result_code=0000` → `complete(AckResult.success())`；否则 `complete(AckResult.fail(msg))`。

### 5.3 单步执行逻辑

1. 生成 seq → 存入 `pendingAcks` future → `tcpDeviceServer.sendCommand(deviceId, command)`。
2. `blocking=true`：`future.get(5, SECONDS)`。
   - 成功 → 下一步。
   - 失败/超时 → 重试（最多 3 次，间隔 500ms / 1s / 2s 退避）；重试生成新 seq。
3. `blocking=false`：发完即下一步（fire-and-forget），不等待。
4. 重试耗尽 → job 标记 `FAILED`，停止后续步，BorrowRecord 保持 RESERVED。
5. 全部步成功 → job `SUCCESS`，BorrowRecord 置 PICKED\_UP。

### 5.4 设备未连接

job 直接 `FAILED`，message="设备 esp8266\_xxx\_01 未连接"。

### 5.5 断连清理

`DeviceTcpConnection.onDisconnected` 触发：将该 deviceId 所有 pending future 标记 failed（避免内存泄漏与永久挂起），对应 job 标记 FAILED。

### 5.6 状态查询

`GET /api/borrow/pickup/{recordId}/status` → `{jobId, currentStep, totalSteps, status, message}`。只读内存 map，不阻塞执行器。前端 1s 轮询，job 终态后停止。

### 5.7 ControlService 改造

- 构造仅注入 `TcpDeviceServer` + `ObjectMapper`。
- `dispatch` 返回 `CompletableFuture<AckResult>`：TCP 已连接则发送并返回 future；未连接返回已 complete(failed) 的 future。
- 删除 HTTP 路由与模拟分支。

## 6. 取件流程

### 6.1 后端

- 移除 `BorrowService.pickup(String qrCode)` 与 `pickupQrCode` 相关逻辑。
- 新增 `POST /api/borrow/pickup/{recordId}`：
  1. 校验 record 属于当前用户且状态 RESERVED。
  2. 读 `item.actionSequence` 反序列化为 `List<ActionStep>`。
  3. 创建 `PickupJob` 存入 store，提交 `ActionExecutor` 异步执行。
  4. 立即返回 `{jobId, currentStep:0, totalSteps, status:"RUNNING"}`。
- 新增 `GET /api/borrow/pickup/{recordId}/status`：见 §5.6。
- `GET /api/borrow/history/{userId}` 返回值移除 pickupQrCode 字段。

### 6.2 前端 Pickup.vue

- 删除取件码输入框、"模拟扫码"按钮、`qrCode` ref。
- 借阅列表中状态 RESERVED 的记录显示"取件"按钮。
- 点击 → `POST /pickup/{recordId}` 拿 jobId → 弹窗展示进度条（currentStep/totalSteps）+ 当前步骤描述 → 每 1s 轮询 status。
- 取件中按钮禁用；job SUCCESS → 关闭弹窗 + 刷新列表；FAILED → 提示可重试。
- 预约入口（BookSearch 预约成功弹窗）不再展示取件码。

## 7. 通信层精简（仅保留 TCP）

- `ControlService` 构造参数：移除 `DeviceRegistrationService`、`DeviceRegistry`、`baseUrl`、`actuatorId`、`simulateMode`；仅留 `TcpDeviceServer` + `ObjectMapper`。
- `dispatch` 简化为：`tcpDeviceServer.isDeviceConnected(deviceId)` ? `sendCommand` 返回 future : 返回 complete(failed) future。删除 `resolveTargetUrl` / `doHttpPost` / `parseResultCode` / `HttpClient`。
- 删除 `service/registration/` 与 `service/discovery/` 整目录。
- 删除 `ControlCommandController` 的 `/api/control/test`、`/api/control/config` 端点。
- `application.yaml`：移除 `device.base-url` / `actuator-id` / `simulate-mode` / `register-enable` / `scan-enable`；保留 `device.tcp.*`（端口、心跳 idle 秒数，默认 90s）。
- 保留 `TcpCommandController`（`/api/control/tcp/*` 调试控制台用）。

## 8. 设备管理页面（管理员看板）

- Admin.vue "设备测试"Tab → 改名"设备调试"，内容替换为 `DeviceDebug.vue` 的核心（TCP 设备列表 + 命令终端 + 通信日志），作为内嵌组件。
- 移除模拟测试元素：actuatorId/baseUrl 输入、"测试滑台/测试机械臂"按钮、`/control/test` 调用。
- `/debug` 路由保留（管理员可直接访问独立页），与 Admin 内嵌组件复用同一组件。

## 9. 环境检测

### 9.1 Home.vue

删除环境概览卡片整块（"暂无环境数据"不再出现）。

### 9.2 Environment.vue 修复

1. `deviceId` 默认值不再硬编码 `esp8266_sensor_01`；`loadDevices` 后取第一个 SENSOR 设备作默认。
2. `loadHistory` 空数据时图表区域显示 `<el-empty description="暂无历史数据，等待设备上报" />`，而非空坐标轴。
3. 时间动态分区：按**实际数据的时间跨度**（最早记录到最新记录的区间，非固定 24h 查询窗）自动选分桶——跨度 ≤2h 按分钟、≤24h 按小时、>24h 按 3 小时；xAxis 用分桶聚合（桶内平均值），避免密集成点。无数据时走第 2 条空态。
4. `catch` 不再静默：错误时 `ElMessage.warning` 提示。
5. 轮询：历史每 30s 重载一次（不再只刷 latest）。

## 10. 场景/物资改图书

- 全站 UI 文案："物资查询"→"图书查询"、"库存"→"图书库存"、"物资类型"过滤器移除（只显示图书）。
- `BookSearch.vue`：移除 BOOK/DEVICE/PACKAGE 类型切换，固定图书。
- Admin "库存"Tab：只展示图书，新增/编辑表单移除 type 选择。

## 11. 错误处理与稳定性

- `ActionExecutor` 单步超时 5s、重试 3 次（500ms / 1s / 2s 退避）；重试耗尽 job FAILED。
- `SlidtabTcpMessageHandler.handleControlResponse` 防御 try-catch；未知 seq 的回执记 warn 并丢弃。
- 设备断连：`onDisconnected` 清理该设备所有 pending future 为 failed。
- job 执行与状态查询解耦：状态查询只读内存 map，不阻塞执行器。
- `TcpDeviceServer` 现有稳定性机制（心跳 idle 90s、SO\_TIMEOUT 30s、竞态防护、`isCurrentConnection` 判断）保留不动。
- `ActionExecutor` 单线程顺序：同一 job 内步骤天然串行；不同 job 间也串行（避免设备指令冲突）。

## 12. 测试策略

### 12.1 后端单测

- `ActionExecutor`：成功路径、失败路径、超时路径、重试 3 次后失败、设备未连接、断连清理 pending。
- `SlidtabTcpMessageHandler.handleControlResponse`：seq 关联成功、未知 seq 丢弃、无 seq 丢弃。
- `BorrowService.pickup(recordId)`：正常提交、record 不属于用户、状态非 RESERVED、item 无 actionSequence。
- `DataInitializer`：播种后图书数 = 20、每本 actionSequence 非空且步数 4–6、命令编号在合法集合内。

### 12.2 集成测试

嵌入式 TCP 模拟设备连接 → 发送 sensor → 触发取件 → 模拟回执（成功/失败）→ 验证 BorrowRecord 状态迁移与 job 状态。

### 12.3 前端手测

取件进度弹窗、Environment 空态与动态分桶、Admin 调试台内嵌、BookSearch 仅图书。

### 12.4 门槛

`mvn -q test` 全绿。

## 13. 设备对接文档（交付物）

- 路径：`docs/device-integration.md`。
- 内容：
  - TCP 连接方式：设备主动 connect 到 `server:port`，按行发 JSON（`\n` 分隔）。
  - 四种消息格式：注册 `register` / 心跳 `heartbeat` / 传感器上报 `sensor` / 控制回执 `control_response`。
  - **回执必须带** **`seq`**，与下行 `control` 的 `seq` 一一对应。
  - 成功 `result_code=0000`，失败非 0000。
  - 超时 5s 后端会重试 3 次。
  - 命令编号表（机械臂 0-2、滑台 0-3）。
  - 完整收发示例（取件全流程）。

## 14. 实施顺序（建议）

1. **座位预约移除**：删 5 个 Seat\*.java + SeatReserve.vue + 路由/api/导航 + 测试清理 + 文档清理。
2. 数据模型：ItemType 收敛 + Item.actionSequence + 移除 pickupQrCode + DataInitializer 播种。
3. 协议层：ControlCommand seq 强化 + SlidtabTcpMessageHandler seq 关联。
4. 执行引擎：ActionExecutor + PickupJobStore + ControlService 简化。
5. 通信层精简：删除 registration/discovery + 端点清理 + yaml 清理。
6. 取件流程：后端端点 + 前端 Pickup.vue。
7. 前端其他：Home.vue 删环境卡 + Environment.vue 修复 + Admin 调试台内嵌 + BookSearch/Admin 文案。
8. 设备对接文档。
9. `mvn -q test` 全绿。

