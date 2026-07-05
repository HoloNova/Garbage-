package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端 IP 查询端点。
 * <p>
 * 当客户端连接服务器时，返回该客户端的真实 IP 地址。
 * 支持通过 X-Forwarded-For / X-Real-IP 等代理头正确获取源 IP。
 * </p>
 */
@RestController
@RequestMapping("/api")
public class ClientIpController {

    /**
     * 获取当前请求客户端的 IP 地址。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
    @GetMapping("/client-ip")
    public ApiResponse<String> getClientIp(HttpServletRequest request) {
        String ip = extractClientIp(request);
        return ApiResponse.success(ip);
    }

    /**
     * 从请求中提取客户端真实 IP，考虑反向代理场景。
     */
    private String extractClientIp(HttpServletRequest request) {
        // 1. 优先取 X-Forwarded-For（经过代理时）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 格式: client, proxy1, proxy2...
            // 取第一个（最左侧）为客户端真实 IP
            int commaIndex = ip.indexOf(',');
            return commaIndex > 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }

        // 2. 其次取 X-Real-IP（Nginx 代理时）
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        // 3. 回退到直接连接 IP
        ip = request.getRemoteAddr();

        // 本地 IPv6 环回 -> 统一为 127.0.0.1
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
    }
}
