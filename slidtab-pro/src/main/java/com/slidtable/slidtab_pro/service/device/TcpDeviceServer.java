package com.slidtable.slidtab_pro.service.device;

import tools.jackson.databind.ObjectMapper;
import com.slidtable.slidtab_pro.dto.protocol.ControlCommand;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP 设备服务器 — 嵌入 Spring Boot 的 TCP 接入点。
 * <p>
 * 替代四层 HTTP 路由，作为 ESP8266/STM32 设备的统一 TCP 接入点。
 * 设备通过 TCP JSON 行协议连接，数据直接路由到后端业务服务，
 * 无需中间进程转发。
 * </p>
 *
 * <pre>
 * ESP8266/STM32 ──TCP JSON──→ TcpDeviceServer (Spring @Component)
 *                                        │
 *                          ┌─────────────┼──────────────┐
 *                          ▼             ▼              ▼
 *                   DeviceService  EnvironmentService  ControlService
 *                   (心跳/状态)      (传感器/阈值)       (指令下发)
 * </pre>
 *
 * <p>
 * 配置 (application.yaml):
 * <pre>{@code
 * device:
 *   tcp:
 *     enabled: true
 *     host: 0.0.0.0
 *     port: 5000
 * }</pre>
 * </p>
 */
@Component
public class TcpDeviceServer {

    private static final Logger log = LoggerFactory.getLogger(TcpDeviceServer.class);

    private final boolean enabled;
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final TcpMessageHandler messageHandler;
    private final long heartbeatIdleSeconds;

    /** 设备 ID → 连接映射 */
    private final ConcurrentHashMap<String, DeviceTcpConnection> connections = new ConcurrentHashMap<>();

    /** 线程工厂命名 */
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    /** 接受线程 */
    private Thread acceptThread;
    private volatile boolean running = false;

    /** 连接处理线程池 */
    private ExecutorService connectionPool;

    public TcpDeviceServer(
            @Value("${device.tcp.enabled:true}") boolean enabled,
            @Value("${device.tcp.host:0.0.0.0}") String host,
            @Value("${device.tcp.port:5000}") int port,
            ObjectMapper objectMapper,
            TcpMessageHandler messageHandler,
            @Value("${device.tcp.heartbeat-idle-seconds:90}") long heartbeatIdleSeconds) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
        this.messageHandler = messageHandler;
        this.heartbeatIdleSeconds = heartbeatIdleSeconds;
        log.info("[TCP服务器] 配置: enabled={}, host={}, port={}, heartbeatIdle={}s", enabled, host, port, heartbeatIdleSeconds);
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("[TCP服务器] 未启用 (device.tcp.enabled=false)");
            return;
        }

        connectionPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tcp-device-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        running = true;
        acceptThread = new Thread(this::acceptLoop, "tcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("=".repeat(55));
        log.info("  图书云柜 — TCP 设备服务器已启动");
        log.info("  监听: {}:{}", host, port);
        log.info("  等待 ESP8266/STM32 设备连接...");
        log.info("=".repeat(55));
    }

    /**
     * 接受连接的主循环。
     */
    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port, 50,
                java.net.InetAddress.getByName(host))) {
            serverSocket.setReuseAddress(true);

            while (running) {
                try {
                    serverSocket.setSoTimeout(1000);
                    Socket client = serverSocket.accept();
                    handleNewConnection(client);
                } catch (java.net.SocketTimeoutException e) {
                    // 超时唤醒，检查 running 标志
                } catch (IOException e) {
                    if (running) {
                        log.error("[TCP服务器] 接受连接失败: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("[TCP服务器] 启动失败: {}:{} — {}", host, port, e.getMessage());
        }
    }

    /**
     * 处理新接入的设备连接。
     */
    private void handleNewConnection(Socket client) {
        try {
            client.setTcpNoDelay(true);
            client.setKeepAlive(true); // OS 级 TCP keepalive 辅助检测死连接
            String remoteAddr = client.getInetAddress().getHostAddress();
            int remotePort = client.getPort();

            DeviceTcpConnection connection = new DeviceTcpConnection(
                    client, objectMapper, messageHandler, this, heartbeatIdleSeconds);

            // 临时用 addr:port 作 key，待协议识别 deviceId 后更新
            String tempKey = remoteAddr + ":" + remotePort;
            connections.put(tempKey, connection);

            connectionPool.submit(connection);

            log.info("[TCP服务器] 接受连接: {} (当前设备数: {})", tempKey, connectionCount());
        } catch (Exception e) {
            log.warn("[TCP服务器] 处理新连接异常: {}", e.getMessage());
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    // ==================== 连接管理 ====================

    /**
     * 当连接识别出 deviceId 后更新映射 key。
     * <p>
     * 如果该 deviceId 已有旧连接（设备重连场景），先停掉旧连接防止双线程抢读写。
     * 旧连接的 cleanup() 后续会触发 onConnectionClosed，但 remove(key, conn)
     * 的身份校验会阻止它误删新连接。
     * </p>
     */
    public void updateDeviceKey(String oldKey, String newDeviceId) {
        DeviceTcpConnection newConn = connections.remove(oldKey);
        if (newConn == null) return;

        // 检查是否已有同一 deviceId 的旧连接 → 停掉
        DeviceTcpConnection oldConn = connections.get(newDeviceId);
        if (oldConn != null && oldConn != newConn) {
            oldConn.stop();
            log.info("[TCP映射] 停掉旧连接: deviceId={}, oldPort={}, newPort={}",
                    newDeviceId, oldConn.getRemotePort(), newConn.getRemotePort());
        }

        connections.put(newDeviceId, newConn);
        log.debug("[TCP映射] {} → {}", oldKey, newDeviceId);
    }

    /**
     * 连接关闭时的清理回调（从 DeviceTcpConnection 调用）。
     * <p>
     * 使用 {@code remove(key, conn)} 做实例身份校验，防止竞态：
     * 如果设备已重连且新连接实例取代了旧连接，旧连接的 cleanup 不会误删新连接。
     * </p>
     */
    public void onConnectionClosed(DeviceTcpConnection conn) {
        String deviceId = conn.getDeviceId();
        if (deviceId != null && !deviceId.equals("unknown")) {
            connections.remove(deviceId, conn);
        }
        // 也尝试用 addr:port key 清理（同样做身份校验）
        String addrKey = conn.getRemoteAddr() + ":" + conn.getRemotePort();
        connections.remove(addrKey, conn);
    }

    /**
     * 获取指定 deviceId 的连接。
     */
    public DeviceTcpConnection getConnection(String deviceId) {
        return connections.get(deviceId);
    }

    /**
     * 向指定设备发送控制指令。
     *
     * @return true=发送成功, false=设备不在线
     */
    public boolean sendCommand(String deviceId, ControlCommand command) {
        DeviceTcpConnection conn = connections.get(deviceId);
        if (conn == null || !conn.isConnected()) {
            log.warn("[TCP发送] 设备不在线: {}", deviceId);
            return false;
        }
        return conn.sendCommand(command);
    }

    /**
     * 向指定设备发送原始文本。
     *
     * @return true=发送成功, false=设备不在线
     */
    public boolean sendRaw(String deviceId, String rawText) {
        DeviceTcpConnection conn = connections.get(deviceId);
        if (conn == null || !conn.isConnected()) {
            log.warn("[TCP发送] 设备不在线: {}", deviceId);
            return false;
        }
        return conn.sendRaw(rawText);
    }

    /**
     * 向所有已连接的设备广播原始文本。
     *
     * @return 成功发送的设备数量
     */
    public int broadcastRaw(String rawText) {
        int count = 0;
        for (Map.Entry<String, DeviceTcpConnection> entry : connections.entrySet()) {
            if (entry.getValue().isConnected() && entry.getValue().sendRaw(rawText)) {
                count++;
            }
        }
        log.info("[TCP广播] 已发送到 {} 台设备: {}", count, truncate(rawText, 100));
        return count;
    }

    /**
     * 获取已连接的设备列表（仅含已识别 deviceId 的设备，不含临时 key）。
     */
    public Collection<Map<String, Object>> listDevices() {
        List<Map<String, Object>> list = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        for (DeviceTcpConnection conn : connections.values()) {
            boolean identified = !"unknown".equals(conn.getDeviceId());
            // 未识别连接用 IP:端口 作显示 ID（其 map key 也是 IP:端口，sendRaw 可直接命中）
            String displayId = identified ? conn.getDeviceId()
                    : (conn.getRemoteAddr() + ":" + conn.getRemotePort());
            list.add(Map.of(
                    "deviceId", displayId,
                    "nodeType", conn.getNodeType(),
                    "ip", conn.getRemoteAddr(),
                    "port", conn.getRemotePort(),
                    "connected", conn.isConnected(),
                    "identified", identified,
                    "responseCount", conn.getResponses().size()
            ));
        }
        return list;
    }

    /**
     * 获取指定设备的响应缓存。
     */
    public java.util.List<com.slidtable.slidtab_pro.dto.protocol.DeviceResponse> getResponses(String deviceId) {
        DeviceTcpConnection conn = connections.get(deviceId);
        if (conn == null) return java.util.Collections.emptyList();
        return conn.getResponses();
    }

    /**
     * 清空指定设备的响应缓存。
     */
    public void clearResponses(String deviceId) {
        DeviceTcpConnection conn = connections.get(deviceId);
        if (conn != null) conn.clearResponses();
    }

    /**
     * 获取已连接的设备 ID 列表。
     */
    public java.util.Set<String> getConnectedDeviceIds() {
        return connections.keySet();
    }

    /**
     * 获取当前连接设备数量。
     */
    public int connectionCount() {
        return connections.size();
    }

    /**
     * 判断指定设备是否已 TCP 连接。
     */
    public boolean isDeviceConnected(String deviceId) {
        DeviceTcpConnection conn = connections.get(deviceId);
        return conn != null && conn.isConnected();
    }

    /**
     * 定时清理：移除 deviceId="unknown" 且已失效的临时连接。
     * <p>
     * 设备在建立 TCP 连接后但未发送 device_id 之前断开，
     * 该连接会以 addr:port 为 key 残留并卡在 readLine() 循环中。
     * 本方法每 30 秒扫描一次，清除这些幽灵 entry。
     * </p>
     */
    @Scheduled(fixedRate = 30_000)
    public void evictStaleTempKeys() {
        connections.forEach((key, conn) -> {
            if ("unknown".equals(conn.getDeviceId())
                    && !conn.isConnected()) {
                connections.remove(key, conn);
                log.info("[TCP清理] 移除失效临时连接: {} (端口{})",
                        key, conn.getRemotePort());
            }
        });
    }

    // ==================== 生命周期 ====================

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @PreDestroy
    public void stop() {
        log.info("[TCP服务器] 正在关闭...");
        running = false;

        // 中断接受线程
        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        // 关闭所有设备连接
        for (DeviceTcpConnection conn : connections.values()) {
            conn.stop();
        }
        connections.clear();

        // 关闭线程池
        if (connectionPool != null) {
            connectionPool.shutdown();
            try {
                if (!connectionPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    connectionPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("[TCP服务器] 已关闭");
    }
}
