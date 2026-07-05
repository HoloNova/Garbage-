package com.slidtable.slidtab_pro.service.device;

import tools.jackson.databind.ObjectMapper;
import com.slidtable.slidtab_pro.dto.protocol.ControlCommand;
import com.slidtable.slidtab_pro.dto.protocol.DeviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个 TCP 设备连接的处理线程（传输层）。
 * <p>
 * 只负责 socket 读写、存活检测、指令下发与响应缓存；业务逻辑由
 * {@link TcpMessageHandler} 处理，本类不依赖任何业务服务，可随传输层整体移植。
 * </p>
 *
 * <h3>存活检测</h3>
 * <ul>
 *   <li>阻塞 {@code reader.readLine()} + {@code setSoTimeout(30s)} 作 tick；
 *       每 30s 唤醒一次检查空闲超时，并让 {@code running} 标志可被及时响应。</li>
 *   <li>{@code lastActiveTime} <b>仅在收到数据时更新</b>——发送成功不能证明远端活着
 *       （TCP 缓冲会吸收数据直到 RST）。设备须在 {@code heartbeatIdleSeconds} 内发数据，否则判死。</li>
 *   <li>发送抛 {@link IOException} 立即 {@link #markDead()} 关闭 socket，唤醒阻塞读走 cleanup。</li>
 * </ul>
 *
 * <h3>异常不崩溃</h3>
 * <ul>
 *   <li>{@code handler.onMessage} 在 run 循环内被防御性 try-catch 包裹，业务 bug 不杀读线程。</li>
 *   <li>{@code handler.onDisconnected} 在 cleanup 内被防御性 try-catch 包裹，确保 socket 关闭不被阻断。</li>
 * </ul>
 */
public class DeviceTcpConnection implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DeviceTcpConnection.class);
    /** SO_TIMEOUT tick 间隔：每 30s 唤醒一次阻塞读，检查空闲超时与 running 标志 */
    private static final int SOCKET_TIMEOUT_MS = 30_000;
    private static final int MAX_RESPONSES = 100;

    private final Socket socket;
    private final ObjectMapper objectMapper;
    private final TcpMessageHandler handler;
    private final TcpDeviceServer server;
    private final long heartbeatIdleSeconds;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private String deviceId = "unknown";
    private String nodeType = "ACTUATOR";
    private Writer writer;

    /** 最后活跃时间（仅收到数据时更新），{@code volatile} 保证读线程与查询线程可见性 */
    private volatile long lastActiveTime = System.currentTimeMillis();

    /** 设备响应环形缓冲区 (最多 100 条) */
    private final ConcurrentLinkedDeque<DeviceResponse> responses = new ConcurrentLinkedDeque<>();

    public DeviceTcpConnection(Socket socket, ObjectMapper objectMapper,
                               TcpMessageHandler handler,
                               TcpDeviceServer server,
                               long heartbeatIdleSeconds) {
        this.socket = socket;
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.server = server;
        this.heartbeatIdleSeconds = heartbeatIdleSeconds;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), "UTF-8"));

            handler.onConnected(this);

            while (running.get()) {
                String line;
                try {
                    line = reader.readLine();
                } catch (SocketTimeoutException e) {
                    // tick：检查空闲超时
                    if (System.currentTimeMillis() - lastActiveTime > heartbeatIdleSeconds * 1000L) {
                        log.info("[TCP设备] 空闲超时({}s)，关闭连接: {} ({})",
                                heartbeatIdleSeconds, deviceId, getRemoteAddr());
                        break;
                    }
                    continue; // 未超空闲阈值，继续等下一行
                }
                if (line == null) {
                    // 对端正常关闭（FIN）
                    log.info("[TCP设备] 连接断开: {} ({})", deviceId, getRemoteAddr());
                    break;
                }
                lastActiveTime = System.currentTimeMillis(); // 仅收侧更新
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                // 防御性：业务 handler 任何异常都不应中断读循环
                try {
                    handler.onMessage(this, trimmed);
                } catch (Exception e) {
                    log.warn("[TCP设备] 业务处理异常: deviceId={}, err={}", deviceId, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.warn("[TCP设备] 连接异常: {} ({}) — {}", deviceId, getRemoteAddr(), e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    // ==================== 发送指令 ====================

    /**
     * 向设备发送一条控制指令（JSON 行）。
     *
     * @return true=发送成功, false=设备不在线或发送失败（连接已标记死）
     */
    public boolean sendCommand(ControlCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command) + "\n";
            if (!sendRaw(json)) {
                log.warn("[TCP设备] 指令发送失败: deviceId={}, command={}（sendRaw 返回 false）",
                        deviceId, command.command());
                return false;
            }
            log.info("[TCP设备] → 发送指令: deviceId={}, command={}", deviceId, command.command());
            return true;
        } catch (Exception e) {
            log.warn("[TCP设备] 指令发送异常: deviceId={}, err={}", deviceId, e.getMessage());
            markDead();
            return false;
        }
    }

    /**
     * 向设备发送原始文本（自由格式，不包装成 ControlCommand）。
     *
     * @return true=发送成功, false=发送失败（连接已标记死）
     */
    public boolean sendRaw(String text) {
        try {
            String payload = text.endsWith("\n") ? text : text + "\n";
            sendRawData(payload);
            log.info("[TCP设备] → 发送原始数据: deviceId={}, len={}", deviceId, payload.length());
            return true;
        } catch (Exception e) {
            log.warn("[TCP设备] 原始数据发送失败: deviceId={}, err={}", deviceId, e.getMessage());
            markDead();
            return false;
        }
    }

    private void sendRawData(String data) throws IOException {
        synchronized (this) {
            if (writer != null) {
                writer.write(data);
                writer.flush();
            }
            // 不更新 lastActiveTime：发送成功不能证明远端活着（TCP 缓冲会吸收数据直到 RST）
        }
    }

    // ==================== 响应缓存（供 handler 与调试接口使用） ====================

    /**
     * 存储一条设备响应到环形缓冲区（业务 handler 调用）。
     */
    public void addResponse(String content) {
        responses.addLast(new DeviceResponse(content, System.currentTimeMillis(), this.deviceId));
        if (responses.size() > MAX_RESPONSES) {
            responses.pollFirst();
        }
    }

    public java.util.List<DeviceResponse> getResponses() {
        return java.util.List.copyOf(responses);
    }

    public void clearResponses() {
        responses.clear();
    }

    // ==================== 身份与协作（供 handler 调用） ====================

    /**
     * 识别出 deviceId 后更新本连接身份，并通知 server 更新连接映射 key（addr:port → deviceId）。
     */
    public void setDeviceId(String deviceId, String nodeType) {
        String oldKey = getRemoteAddr() + ":" + getRemotePort();
        this.deviceId = deviceId;
        this.nodeType = nodeType;
        server.updateDeviceKey(oldKey, deviceId);
    }

    /**
     * 判断自己是否仍是该 deviceId 在 server map 中的当前连接。
     * 用于 onDisconnected 时竞态防护——设备已重连时旧连接的清理不应触发 markOffline。
     */
    public boolean isCurrentConnection() {
        if ("unknown".equals(deviceId)) return false;
        return server.getConnection(deviceId) == this;
    }

    // ==================== 生命周期 ====================

    /**
     * 标记连接死并关闭 socket，唤醒阻塞的 readLine 触发 cleanup。
     */
    private void markDead() {
        running.set(false);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    /**
     * 主动停止连接（@PreDestroy 或重连替换时调用）。关闭 socket 唤醒阻塞读。
     */
    public void stop() {
        running.set(false);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getRemoteAddr() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        return socket.getPort();
    }

    /**
     * 判断 TCP 连接是否仍然有效。
     * <p>
     * 综合三要素：运行标志位、socket 未关闭、最近收到数据在空闲阈值内。
     * 不使用 {@code socket.isConnected()}（它只表示"曾经连过"，对半开检测无意义）。
     * </p>
     */
    public boolean isConnected() {
        return running.get()
                && !socket.isClosed()
                && (System.currentTimeMillis() - lastActiveTime) < heartbeatIdleSeconds * 1000L;
    }

    private void cleanup() {
        running.set(false);
        // 先业务清理（含 isCurrentConnection 竞态判断）；防御性捕获确保后续 socket 关闭不被阻断
        try {
            handler.onDisconnected(this);
        } catch (Exception e) {
            log.warn("[TCP设备] onDisconnected 异常: deviceId={}, err={}", deviceId, e.getMessage());
        }
        server.onConnectionClosed(this);
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        log.info("[TCP设备] 已清理: {} ({}:{})", deviceId, getRemoteAddr(), getRemotePort());
    }
}
