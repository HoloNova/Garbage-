# 图书云柜 — 设备对接文档

> 面向 ESP8266/STM32 固件开发者。描述设备如何接入图书云柜服务器、通信协议、指令格式与调试接口。最后更新：2026-07-04。

## 1. 架构概览

```
ESP8266/STM32 ──TCP JSON 行协议──→ TcpDeviceServer (Spring Boot 内嵌, 端口 5000)
                                          │
                          ┌───────────────┼────────────────┐
                          ▼               ▼                ▼
                   DeviceService    EnvironmentService   ControlService
                   (心跳/在线状态)    (传感器数据)        (指令下发)
                          │                                  │
                          └──────────────┬───────────────────┘
                                         ▼
                                    ActionExecutor
                                    (取件/归还动作序列)
```

**通信方式：仅 TCP。** 设备主动连接服务器，服务器不主动连设备。无 HTTP 轮询、无 MQTT、无 UDP。

### 服务器地址

| 环境 | Host | TCP 端口 | HTTP 端口 |
|------|------|---------|----------|
| 本地开发 | 服务器 IP | 5000 | 9000 |
| 生产 | 部署服务器 IP | 5000 | 9000 |

配置见 `application.yaml`：
```yaml
device:
  tcp:
    enabled: true
    host: 0.0.0.0      # 监听所有网卡
    port: 5000          # TCP 设备接入端口
    heartbeat-idle-seconds: 90  # 空闲超时阈值
    action-step-delay-ms: 4000  # 阻塞步发送后固定等待时长
```

## 2. 协议规范

### 2.1 传输格式

- **TCP 长连接**：设备启动后建立连接，保持常驻。
- **JSON 行协议**：每条消息是一个 UTF-8 编码的 JSON 对象，以 `\n` 结尾。
- **一行一消息**：禁止跨行 JSON，禁止一行多条消息。

### 2.2 存活检测

| 参数 | 值 | 说明 |
|------|-----|------|
| `heartbeat-idle-seconds` | 90s | 设备超过 90s 未发送任何数据 → 判死，服务器关闭连接 |
| SO_TIMEOUT | 30s | 服务器每 30s 唤醒一次检查空闲 |
| TCP keepalive | 开启 | OS 级辅助检测半开连接 |

**设备必须**在 90s 内至少发送一次数据（心跳或传感器数据），否则会被断开。建议每 30s 发一次心跳。

### 2.3 设备身份识别

设备建立 TCP 连接后，第一条消息应发送 `register` 带上 `device_id`。服务器收到后会将连接的临时 key（`IP:端口`）替换为 `device_id`，后续指令按 `device_id` 路由。

若不先 register，连接会以 `IP:端口` 作为标识，无法接收业务指令（只能用调试接口发原始文本）。

## 3. 设备 → 服务器 消息

所有消息为 JSON 对象，公共字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `msg_type` | string | 是 | 消息类型，见下表 |
| `device_id` | string | 是 | 发送方设备 ID |
| `seq` | string | 否 | 消息序号（回执关联用） |
| `timestamp` | string | 否 | ISO 格式时间戳 |
| `source` | string | 否 | 消息来源（默认 = device_id） |

### 3.1 register — 设备注册

建立连接后首发，声明设备身份。

```json
{
  "msg_type": "register",
  "device_id": "esp8266_arm_01",
  "node_type": "ARM",
  "port": 8081
}
```

| 字段 | 说明 |
|------|------|
| `device_id` | 设备唯一标识 |
| `node_type` | 节点类型，自定义字符串（如 `ARM`/`SLIDE`/`SENSOR`/`ACTUATOR`），默认 `ACTUATOR` |
| `port` | 设备本地服务端口（可选，仅记录用，默认 8081） |

**服务器响应** `register_ack`：
```json
{
  "msg_type": "register_ack",
  "result_code": "0000",
  "result_msg": "注册成功",
  "device_id": "esp8266_arm_01",
  "timestamp": "2026-07-03T10:30:00"
}
```

### 3.2 heartbeat — 心跳

维持连接存活，刷新最后活跃时间。

```json
{
  "msg_type": "heartbeat",
  "device_id": "esp8266_arm_01"
}
```

服务器不回执，仅更新内部活跃时间戳。建议 30s 发送一次。

### 3.3 sensor — 传感器数据

上报温湿度等环境数据。传感器设备（`esp8266_sensor_01`）常驻上报。

```json
{
  "msg_type": "sensor",
  "device_id": "esp8266_sensor_01",
  "seq": "1751500000-1",
  "timestamp": "2026-07-03T10:30:00",
  "source": "esp8266_sensor_01",
  "temperature": 25.6,
  "humidity": 60.2,
  "light": 320.0,
  "weight": 1.5,
  "smoke": 0.0
}
```

传感器字段（全部可选，至少发一个）：

| 字段 | 类型 | 单位 | 说明 |
|------|------|------|------|
| `temperature` | number | ℃ | 温度 |
| `humidity` | number | % | 湿度 |
| `light` | number | lux | 光照 |
| `weight` | number | kg | 重量 |
| `smoke` | number | ppm | 烟雾浓度 |

### 3.4 control_response — 指令回执（可选）

设备收到 `control` 指令后**可选**回复此消息。服务器 `ActionExecutor` 不依赖 `control_response` 推进动作序列，采用 4 秒固定等待策略（见 [§6.2](#62-执行规则)）。回执仅写入响应缓冲供调试接口 `GET /api/control/tcp/responses` 查询。

```json
{
  "msg_type": "control_response",
  "device_id": "esp8266_arm_01",
  "seq": "1751500000-1",
  "result_code": "0000",
  "result_msg": "执行成功"
}
```

| 字段 | 说明 |
|------|------|
| `seq` | 与收到的 control 指令 seq 一致，便于调试关联 |
| `result_code` | `"0000"` = 成功，其他 = 失败 |
| `result_msg` | 人类可读说明 |

> 服务器不校验 `seq` 是否匹配，也不根据回执推进或重试。即使设备不回复，4 秒后服务器也会发送下一条指令。

### 3.5 status — 状态上报

主动上报设备状态（柜门、电机、传送带、告警等）。

```json
{
  "msg_type": "status",
  "device_id": "esp8266_arm_01",
  "seq": "1751500000-2",
  "timestamp": "2026-07-03T10:30:00",
  "source": "esp8266_arm_01",
  "online": true,
  "action": "grab",
  "result_code": "0000",
  "result_msg": "抓取完成",
  "cabinet_door": "closed",
  "motor_state": "idle",
  "conveyor_state": "stopped",
  "slot_state": "S03",
  "alarm_state": "normal",
  "book_id": "BK003",
  "item_state": "placed"
}
```

设备状态字段（可选，按需上报）：

| 字段 | 说明 |
|------|------|
| `online` | boolean，设备是否在线 |
| `action` | 当前动作名称 |
| `result_code` / `result_msg` | 动作结果 |
| `cabinet_door` | 柜门状态（`open`/`closed`） |
| `slot_state` | 当前格口位置 |
| `motor_state` | 电机状态（`idle`/`running`/`error`） |
| `conveyor_state` | 传送带状态 |
| `alarm_state` | 告警状态（`normal`/`warning`/`error`） |
| `book_id` | 当前图书 ID |
| `item_state` | 图书状态（`placed`/`grabbed`/`missing`） |

### 3.6 alarm — 告警上报

设备发生异常时上报。

```json
{
  "msg_type": "alarm",
  "device_id": "esp8266_arm_01",
  "seq": "1751500000-3",
  "timestamp": "2026-07-03T10:30:00",
  "source": "esp8266_arm_01",
  "result_msg": "电机过热",
  "motor_state": "error",
  "alarm_state": "error"
}
```

服务器会将设备标记为告警状态，管理员可在后台告警 Tab 处理。

## 4. 服务器 → 设备 消息

### 4.1 control — 控制指令

服务器下发的动作指令。设备收到后执行，**可选**回复 `control_response`（服务器不依赖回执推进）。

```json
{
  "protocol_version": "1.0",
  "msg_type": "control",
  "seq": "1751500000-1",
  "timestamp": "2026-07-03T10:30:00",
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

| 字段 | 说明 |
|------|------|
| `seq` | 指令序号（供设备关联回执，服务器不校验） |
| `command` | 指令名称（见下表） |
| `params.cmd` | **命令编号**，设备按此值执行具体动作 |
| `timeout_ms` | 字段保留，服务器实际不等待回执（改用 4s 固定等待） |

**command 与 params.cmd 的对应关系**：

| 设备 | device_id | command | params.cmd | 含义（由设备固件定义） |
|------|-----------|---------|------------|----------------------|
| 机械臂 | `esp8266_arm_01` | `START_ARM` | 0 | 复位 |
| 机械臂 | `esp8266_arm_01` | `START_ARM` | 1 | 抓取 |
| 机械臂 | `esp8266_arm_01` | `START_ARM` | 2 | 放置 |
| 滑台 | `esp8266_mse_01` | `MOVE_TO_SLOT` | 0 | 移动到格口 0 |
| 滑台 | `esp8266_mse_01` | `MOVE_TO_SLOT` | 1 | 移动到格口 1 |
| 滑台 | `esp8266_mse_01` | `MOVE_TO_SLOT` | 2 | 移动到格口 2 |
| 滑台 | `esp8266_mse_01` | `MOVE_TO_SLOT` | 3 | 移动到格口 3 |

> `command` 字段由服务器根据 device_id 自动选择（含 `arm` → `START_ARM`，含 `mse` → `MOVE_TO_SLOT`）。设备固件应主要解析 `params.cmd` 执行动作。

### 4.2 register_ack — 注册回执

见 [3.1 register](#31-register--设备注册)。

## 5. 设备清单

| 设备 ID | 类型 | 命令范围 | 职责 |
|--------|------|---------|------|
| `esp8266_arm_01` | 机械臂 | cmd 0-2 | 抓取/放置图书 |
| `esp8266_mse_01` | 滑台 | cmd 0-3 | 定位到指定格口 |
| `esp8266_sensor_01` | 传感组 | 无指令 | 常驻上报温湿度等环境数据 |

> 设备 ID 在 `register` 消息中由设备自行声明。服务器按 ID 路由指令，不匹配则指令发不到设备。

## 6. 取件动作序列

每本图书在播种时生成一个动作序列（存于 `Item.actionSequence` 字段），取件时按序下发。

### 6.1 序列格式

JSON 数组，每个元素是一个 `ActionStep`：

```json
[
  {"device":"esp8266_mse_01","cmd":2,"blocking":true},
  {"device":"esp8266_arm_01","cmd":1,"blocking":true},
  {"device":"esp8266_mse_01","cmd":0,"blocking":true},
  {"device":"esp8266_arm_01","cmd":2,"blocking":true},
  {"device":"esp8266_arm_01","cmd":0,"blocking":true}
]
```

| 字段 | 说明 |
|------|------|
| `device` | 目标设备 ID |
| `cmd` | 命令编号（机械臂 0-2，滑台 0-3） |
| `blocking` | `true` = 发送后等待 4 秒让设备执行再发下一步；`false` = 发完即继续（不等待） |

### 6.2 执行规则

- **顺序执行**：`ActionExecutor` 使用 `SingleThreadExecutor`（线程名 `action-executor`），按数组顺序依次下发，不并发。取件/归还/模板试运行共享同一队列。
- **阻塞步**：发送后固定等待 4 秒（`ActionExecutor.stepDelayMs`，配置项 `device.tcp.action-step-delay-ms`，默认 4000）让设备执行，**不等待回执**。
- **发送失败处理**：发送失败（设备未连接或 socket 异常）立即标记 job `FAILED`，**不重试**。
- **异步步**：fire-and-forget，发完即继续（当前播种的所有序列均为阻塞步）。
- **设备断连**：`sendStep` 调 `tcpDeviceServer.isDeviceConnected` 返回 false 时立即返 FAILED，不等待 socket 抛 IOException。
- **首步**：通常为滑台定位（`esp8266_mse_01`）。
- **末步**：通常为机械臂复位（`esp8266_arm_01`, cmd=0）。
- **完成事件**：job 终态后发布 `PickupJobCompletedEvent`，`BorrowService.onPickupComplete` 监听器异步更新借阅记录状态。

### 6.3 归还动作序列

归还时由 `BorrowService.buildReturnActionSequence` 构造 5 步逆序动作序列（与取件共用 `ActionExecutor` 单线程队列，保证不并发）：

| 步序 | 设备 | cmd | blocking | 含义 |
|------|------|-----|----------|------|
| 1 | `esp8266_arm_01` | 1 | true | 机械臂抓取 |
| 2 | `esp8266_mse_01` | `(testId - 1) % 4` | true | 滑台移动到目标格口 |
| 3 | `esp8266_arm_01` | 2 | true | 机械臂放置 |
| 4 | `esp8266_mse_01` | 0 | true | 滑台回出货口 |
| 5 | `esp8266_arm_01` | 0 | true | 机械臂复位 |

**同步等待**：`BorrowService.returnItem` 提交后轮询 `PickupJobStore` 直到 job 终态（`RETURN_JOB_TIMEOUT_SECONDS=90s` 超时）。前端 `Return.vue` 使用 120s axios 超时。job SUCCESS → `BorrowRecord.RETURNED`、`Item.AVAILABLE`、`Slot.OCCUPIED`；job FAILED → 抛 `BusinessException(1007)`。

## 7. 错误码

| result_code | 含义 |
|-------------|------|
| `0000` | 成功 |
| `1010` | 连接失败 |
| 其他 | 设备自定义错误码 |

服务器仅判断 `"0000"` 为成功，其他一律视为失败。

> 上述为设备端 `result_code`。服务器侧业务错误码（前端 `/api/**` 响应 `code` 字段）见 [api.md](./api.md) §1.4 错误码表。

## 8. 调试接口（REST）

供前端设备调试控制台使用，base 路径 `/api/control/tcp`。完整 API 参考见 [api.md](./api.md) §6。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/devices` | 获取已连接的 TCP 设备列表 |
| POST | `/send-raw` | 向指定设备发送原始文本（不包装） |
| POST | `/broadcast` | 向所有设备广播原始文本 |
| GET | `/responses?deviceId=&limit=` | 获取设备响应缓存（最近 N 条） |
| DELETE | `/responses?deviceId=` | 清空设备响应缓存 |
| POST | `/test?deviceId=` | 测试设备 TCP 连接状态（**仅检查 `isDeviceConnected`，不发送任何数据**） |
| POST | `/simulate-pickup?itemId=` | 模拟取货：派发图书动作序列到 `ActionExecutor`（不创建借阅记录，recordId=0） |
| GET | `/simulate-pickup/status?jobId=` | 查询模拟取货任务进度（按 jobId 查询） |

### 8.1 设备列表示例

`GET /api/control/tcp/devices`
```json
{
  "code": 0,
  "data": [
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
}
```

### 8.2 发送原始指令

`POST /api/control/tcp/send-raw`
```json
{"deviceId": "esp8266_arm_01", "content": "{\"msg_type\":\"ping\"}"}
```

> send-raw 不经过业务层，直接写入 TCP socket。适合协议调试阶段发送任意格式数据。

### 8.3 模拟取货

`POST /api/control/tcp/simulate-pickup?itemId=BK001`

派发前会校验所有步骤涉及的设备是否已连接，未连接立即返回错误：

```json
{
  "code": 1003,
  "message": "设备未就绪，请先连接设备: esp8266_arm_01, esp8266_mse_01"
}
```

成功响应：

```json
{
  "code": 0,
  "data": {
    "jobId": "SIM-ABC123DEF456",
    "steps": 5,
    "message": "取件已启动"
  }
}
```

进度查询 `GET /api/control/tcp/simulate-pickup/status?jobId=SIM-ABC123DEF456`：

```json
{
  "code": 0,
  "data": {
    "jobId": "SIM-ABC123DEF456",
    "status": "RUNNING",
    "currentStep": 2,
    "totalSteps": 5,
    "message": "执行第 3/5 步: esp8266_arm_01 cmd=1"
  }
}
```

> 注意：`PickupJobStore` 同时按 `recordId` 和 `jobId` 建立索引。模拟取货 `recordId=0` 会冲突，必须用 `jobId` 查询。

## 9. ESP8266 参考实现

以下为 Arduino + WiFiClient 的最小可用示例：

```cpp
#include <ESP8266WiFi.h>
#include <ArduinoJson.h>

const char* WIFI_SSID = "your-ssid";
const char* WIFI_PASS = "your-password";
const char* SERVER_HOST = "192.168.1.100";  // 服务器 IP
const int   SERVER_PORT = 5000;             // TCP 端口

const char* DEVICE_ID = "esp8266_arm_01";   // 设备 ID
const char* NODE_TYPE = "ARM";

WiFiClient client;
unsigned long lastHeartbeat = 0;

void setup() {
  Serial.begin(115200);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected, IP: " + WiFi.localIP().toString());

  connectToServer();
}

void connectToServer() {
  Serial.println("Connecting to server...");
  while (!client.connect(SERVER_HOST, SERVER_PORT)) {
    Serial.print(".");
    delay(2000);
  }
  Serial.println("TCP connected");
  sendRegister();
}

void sendRegister() {
  StaticJsonDocument<200> doc;
  doc["msg_type"] = "register";
  doc["device_id"] = DEVICE_ID;
  doc["node_type"] = NODE_TYPE;
  doc["port"] = 8081;
  serializeJson(doc, client);
  client.print("\n");
}

void sendHeartbeat() {
  StaticJsonDocument<128> doc;
  doc["msg_type"] = "heartbeat";
  doc["device_id"] = DEVICE_ID;
  serializeJson(doc, client);
  client.print("\n");
}

void sendControlResponse(const String& seq, const char* code, const char* msg) {
  StaticJsonDocument<256> doc;
  doc["msg_type"] = "control_response";
  doc["device_id"] = DEVICE_ID;
  doc["seq"] = seq;
  doc["result_code"] = code;
  doc["result_msg"] = msg;
  serializeJson(doc, client);
  client.print("\n");
}

void handleControlMessage(const StaticJsonDocument<512>& doc) {
  String seq = doc["seq"] | "";
  int cmd = doc["params"]["cmd"] | -1;

  // 按命令编号执行动作
  bool ok = true;
  const char* msg = "ok";
  switch (cmd) {
    case 0: ok = doReset();   break;   // 复位
    case 1: ok = doGrab();    break;   // 抓取
    case 2: ok = doPlace();   break;   // 放置
    default: ok = false; msg = "unknown cmd"; break;
  }

  // 回执可选：服务器不依赖回执推进，但发送回执便于调试日志关联
  sendControlResponse(seq, ok ? "0000" : "1000", ok ? "成功" : msg);
}

bool doReset()  { /* 驱动机械臂复位 */ return true; }
bool doGrab()   { /* 驱动机械臂抓取 */ return true; }
bool doPlace()  { /* 驱动机械臂放置 */ return true; }

void loop() {
  if (!client.connected()) {
    Serial.println("Disconnected, reconnecting...");
    delay(2000);
    connectToServer();
    return;
  }

  // 30s 心跳
  if (millis() - lastHeartbeat > 30000) {
    sendHeartbeat();
    lastHeartbeat = millis();
  }

  // 读取服务器指令
  if (client.available()) {
    String line = client.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      StaticJsonDocument<512> doc;
      DeserializationError err = deserializeJson(doc, line);
      if (!err) {
        String msgType = doc["msg_type"] | "";
        if (msgType == "control") {
          handleControlMessage(doc);
        } else if (msgType == "register_ack") {
          Serial.println("Registered: " + String(doc["result_msg"] | ""));
        }
      }
    }
  }
}
```

### 传感器设备示例

`esp8266_sensor_01` 无需处理 control 指令，只需定时上报传感器数据：

```cpp
void sendSensorData() {
  StaticJsonDocument<256> doc;
  doc["msg_type"] = "sensor";
  doc["device_id"] = "esp8266_sensor_01";
  doc["seq"] = String(millis());
  doc["timestamp"] = getIsoTimestamp();
  doc["source"] = "esp8266_sensor_01";
  doc["temperature"] = readTemperature();
  doc["humidity"] = readHumidity();
  serializeJson(doc, client);
  client.print("\n");
}
```

建议传感器设备每 10-30s 上报一次，同时兼作心跳。

## 10. 对接检查清单

设备开发者可按以下步骤验证对接：

1. **TCP 连接**：设备能建立到 `服务器IP:5000` 的 TCP 连接。
2. **注册**：发送 `register` 消息，服务器日志出现 `[TCP注册] 设备注册: deviceId=xxx`。
3. **心跳**：发送 `heartbeat`，服务器日志出现 `[TCP心跳] deviceId=xxx`（DEBUG 级）。
4. **设备列表**：调用 `GET /api/control/tcp/devices` 能看到设备，`connected: true`。
5. **原始指令**：在调试控制台用 send-raw 发送 `{"msg_type":"ping"}`，设备能收到。
6. **控制指令**：在调试控制台用 `POST /api/control/tcp/simulate-pickup?itemId=BK001` 模拟取件流程，设备能收到 `control` 消息并解析 `params.cmd`。
7. **回执（可选）**：设备回复 `control_response`，可在 `GET /api/control/tcp/responses?deviceId=xxx` 查到。服务器不依赖回执推进。
8. **断线重连**：断开设备网络，服务器 90s 内标记离线；恢复后重连能重新注册。

## 11. 常见问题

**Q: 设备连上 TCP 但收不到指令？**
检查是否发送了 `register` 消息并带上了正确的 `device_id`。未注册的连接以 `IP:端口` 标识，业务指令按 `device_id` 路由，匹配不到。

**Q: 指令发了但设备没动作？**
检查 `params.cmd` 是否在设备固件支持的范围内（机械臂 0-2，滑台 0-3）。服务器发送后固定等待 4 秒即发下一条，若设备执行时间 >4s 需调大 `device.tcp.action-step-delay-ms`。

**Q: 设备频繁掉线？**
确认心跳间隔 < 90s。若 90s 内无任何数据上行，服务器会判定为死连接并关闭。

**Q: 取件动作序列里有多设备，顺序如何？**
`ActionExecutor` 单线程顺序执行，按 JSON 数组顺序依次下发。阻塞步发送后等待 4 秒再发下一步，不依赖回执。

**Q: 设备固件如何区分 START_ARM 和 MOVE_TO_SLOT？**
服务器根据 `device_id` 自动选择 `command` 字段（含 `arm` → `START_ARM`，含 `mse` → `MOVE_TO_SLOT`）。固件可直接解析 `params.cmd` 执行，`command` 字段仅作语义标注。

**Q: 设备是否必须回复 `control_response`？**
否。服务器采用 4 秒固定等待策略，不依赖回执推进。回执仅写入响应缓冲供调试接口查询，便于日志关联。设备可选择回复或不回复。
