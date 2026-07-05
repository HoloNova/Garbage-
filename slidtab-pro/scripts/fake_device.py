"""
模拟 ESP8266/STM32 设备 — 用于端到端验证后端 TCP 链路。

用法：
    python fake_device.py                       # 默认 device_id=esp8266_arm_01
    python fake_device.py my_device_02           # 自定义 device_id
    python fake_device.py esp8266_arm_01 --host 192.168.1.10 --port 5000

行为：
1. TCP 连接到后端 5000 端口
2. 发送 register 消息（带 device_id）
3. 每 15 秒发送一次 heartbeat
4. 收到任何下行消息都打印到屏幕（前 500 字符）
5. Ctrl+C 退出时优雅关闭 socket
"""
import json
import socket
import sys
import threading
import time
from datetime import datetime, timezone, timedelta


def now_iso():
    return datetime.now(timezone(timedelta(hours=8))).strftime("%Y-%m-%dT%H:%M:%S")


def send_msg(sock, msg: dict):
    line = json.dumps(msg, ensure_ascii=False) + "\n"
    try:
        sock.sendall(line.encode("utf-8"))
        print(f"[{now_iso()}] → SEND: {line.strip()}")
    except Exception as e:
        print(f"[{now_iso()}] !! SEND failed: {e}")


def recv_loop(sock):
    buf = b""
    while True:
        try:
            chunk = sock.recv(4096)
        except Exception as e:
            print(f"[{now_iso()}] !! RECV error: {e}")
            return
        if not chunk:
            print(f"[{now_iso()}] << connection closed by server")
            return
        buf += chunk
        while b"\n" in buf:
            line, _, buf = buf.partition(b"\n")
            line = line.strip()
            if not line:
                continue
            try:
                preview = line.decode("utf-8", errors="replace")
                if len(preview) > 500:
                    preview = preview[:500] + "...(truncated)"
                print(f"[{now_iso()}] ← RECV: {preview}")
            except Exception:
                pass


def heartbeat_loop(sock, device_id):
    while True:
        try:
            time.sleep(15)
            send_msg(sock, {
                "protocol_version": "1.0",
                "msg_type": "heartbeat",
                "seq": str(int(time.time() * 1000)),
                "timestamp": now_iso(),
                "source": device_id,
                "target": "server",
                "device_id": device_id,
            })
        except Exception:
            return


def stdin_loop(sock, device_id):
    """从 stdin 读一行直接发给后端。空行跳过，输入 quit 退出。"""
    print(f"[{now_iso()}] (stdin 输入一行 JSON 即可发送；输入 quit 退出)")
    while True:
        try:
            line = input()
        except (EOFError, OSError):
            return
        line = line.strip()
        if not line:
            continue
        if line.lower() in ("quit", "exit"):
            print(f"[{now_iso()}] ## stdin quit, closing")
            try:
                sock.close()
            except Exception:
                pass
            return
        try:
            msg = json.loads(line)
        except Exception as e:
            print(f"[{now_iso()}] !! not JSON, send as raw text: {e}")
            try:
                sock.sendall((line + "\n").encode("utf-8"))
                print(f"[{now_iso()}] → SEND raw: {line}")
            except Exception as e2:
                print(f"[{now_iso()}] !! SEND failed: {e2}")
            continue
        send_msg(sock, msg)


def main():
    args = sys.argv[1:]
    device_id = args[0] if args and not args[0].startswith("--") else "esp8266_arm_01"
    host = "127.0.0.1"
    port = 5000
    it = iter(args)
    next(it, None)
    for a in it:
        if a == "--host":
            host = next(it, host)
        elif a == "--port":
            port = int(next(it, port))

    print(f"=== Fake device starting ===")
    print(f"  device_id = {device_id}")
    print(f"  server    = {host}:{port}")
    print(f"  Press Ctrl+C to exit")

    sock = socket.create_connection((host, port), timeout=5)
    sock.settimeout(None)  # connect 完成后让 recv 阻塞，不再 timeout
    print(f"[{now_iso()}] ## TCP connected to {host}:{port}")

    # 启动接收线程
    t_recv = threading.Thread(target=recv_loop, args=(sock,), daemon=True)
    t_recv.start()
    # 启动心跳线程
    t_hb = threading.Thread(target=heartbeat_loop, args=(sock, device_id), daemon=True)
    t_hb.start()
    # 启动 stdin 输入线程
    t_in = threading.Thread(target=stdin_loop, args=(sock, device_id), daemon=True)
    t_in.start()

    # 发送 register
    send_msg(sock, {
        "protocol_version": "1.0",
        "msg_type": "register",
        "seq": str(int(time.time() * 1000)),
        "timestamp": now_iso(),
        "source": device_id,
        "target": "server",
        "device_id": device_id,
        "node_type": "ACTUATOR",
        "port": 8081,
    })

    # 主线程等待
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"\n[{now_iso()}] ## shutting down")
        try:
            sock.close()
        except Exception:
            pass


if __name__ == "__main__":
    main()
