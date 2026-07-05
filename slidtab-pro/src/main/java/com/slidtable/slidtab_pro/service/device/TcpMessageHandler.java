package com.slidtable.slidtab_pro.service.device;

/**
 * TCP 设备消息处理器接口 — 业务侧与传输侧的解耦边界。
 * <p>
 * 传输层（{@link TcpDeviceServer} + {@link DeviceTcpConnection}）只负责
 * 连接管理、读写、存活检测与指令下发；业务层实现本接口处理设备上报。
 * 其他后端集成本模块时，复制传输层三个类 + DTO，提供自己的 handler 实现即可。
 * </p>
 *
 * <pre>
 * TcpDeviceServer ──依赖──→ TcpMessageHandler（单向，无循环依赖）
 * DeviceTcpConnection ──持有──→ TcpMessageHandler（run 循环回调）
 * SlidtabTcpMessageHandler ──不依赖──→ TcpDeviceServer（通过 conn 间接协作）
 * </pre>
 */
public interface TcpMessageHandler {

    /**
     * 连接已建立，socket 可读写（{@code conn.getDeviceId()} 此时为 "unknown"）。
     */
    void onConnected(DeviceTcpConnection conn);

    /**
     * 收到一行数据（已保证非空、已 trim）。handler 负责解析 JSON、识别 deviceId、分发业务。
     * 实现内部不应抛出异常——传输层会防御性捕获，但业务 bug 会以 warn 记录。
     */
    void onMessage(DeviceTcpConnection conn, String line);

    /**
     * 连接已断开（socket 已关或读循环退出）。
     * handler 负责业务侧清理（如标记离线），但必须先用
     * {@link DeviceTcpConnection#isCurrentConnection()} 判断"自己是否仍是该 deviceId 的当前连接"，
     * 避免与重连后的新连接竞态（旧连接清理不应把新连接的设备拖离线）。
     */
    void onDisconnected(DeviceTcpConnection conn);
}
