"""
端到端 UI 验证：
1. 打开登录页 -> 登录 -> /home
2. 访问 /debug 设备调试页
3. 截图：登录页、调试页（默认移动端 390x844 + 桌面 1280x800）
4. 验证：(a) 桌面端不再有 430px 居中限制 (b) 设备列表能展示 esp8266_arm_01
"""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

OUT_DIR = Path(r"c:\Users\zdw00\Desktop\slidtab-pro\slidtab-pro\scripts\ui-shots")
OUT_DIR.mkdir(parents=True, exist_ok=True)

LOGIN_URL = "http://localhost:9100/login"
DEBUG_URL = "http://localhost:9100/debug"


def run_one(viewport, label):
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": viewport[0], "height": viewport[1]})
        page = ctx.new_page()
        # 抓控制台错误
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.on("console", lambda m: errors.append(f"[{m.type}] {m.text}") if m.type in ("error", "warning") else None)

        page.goto(LOGIN_URL, wait_until="networkidle")
        page.wait_for_load_state("networkidle")
        page.screenshot(path=str(OUT_DIR / f"{label}-01-login.png"), full_page=True)

        # 查看登录页输入框
        inputs = page.locator("input").all()
        print(f"[{label}] login page input count = {len(inputs)}")
        for i, inp in enumerate(inputs):
            t = inp.get_attribute("type") or "text"
            ph = inp.get_attribute("placeholder") or ""
            print(f"  [{i}] type={t} placeholder={ph}")

        # 尝试用 placeholder 找手机号/学号输入框
        phone_input = page.locator("input[placeholder*='手机'], input[placeholder*='电话'], input[type='tel'], input[inputmode='tel']").first
        try:
            phone_input.fill("13800000001", timeout=2000)
        except Exception:
            # 退而求其次：第一个 input
            page.locator("input").nth(0).fill("13800000001")

        # 学号
        sid_input = page.locator("input[placeholder*='学号'], input[placeholder*='S0']").first
        try:
            sid_input.fill("S001", timeout=2000)
        except Exception:
            page.locator("input").nth(1).fill("S001")

        page.screenshot(path=str(OUT_DIR / f"{label}-02-login-filled.png"), full_page=True)

        # 点登录按钮
        try:
            page.locator("button:has-text('登录'), button:has-text('登 录'), button[type='submit']").first.click(timeout=2000)
        except Exception:
            page.locator("button").first.click()

        # 等待跳转
        page.wait_for_load_state("networkidle", timeout=5000)
        page.wait_for_timeout(800)
        print(f"[{label}] after login url = {page.url}")
        page.screenshot(path=str(OUT_DIR / f"{label}-03-after-login.png"), full_page=True)

        # 访问 /debug
        page.goto(DEBUG_URL, wait_until="networkidle")
        page.wait_for_timeout(1500)  # 等 loadDevices 轮询
        page.screenshot(path=str(OUT_DIR / f"{label}-04-debug.png"), full_page=True)

        # 验证：调试页是否显示设备 esp8266_arm_01
        body_text = page.locator("body").inner_text()
        has_device = "esp8266_arm_01" in body_text
        has_empty = "暂无 TCP 设备连接" in body_text or "暂无" in body_text
        print(f"[{label}] debug page shows device esp8266_arm_01: {has_device}")
        print(f"[{label}] debug page shows empty: {has_empty}")

        # 抓 app-layout 元素的宽度（验证 max-width 是否生效）
        try:
            layout_box = page.locator(".app-layout").bounding_box()
            print(f"[{label}] .app-layout bounding box = {layout_box}")
            if layout_box:
                print(f"[{label}] layout width = {layout_box['width']} (viewport = {viewport[0]})")
                if layout_box['width'] >= viewport[0] - 5:
                    print(f"[{label}] ✓ 全宽适配")
                else:
                    print(f"[{label}] ✗ 仍有宽度限制（可能 max-width 未生效）")
        except Exception as e:
            print(f"[{label}] !! cannot get .app-layout box: {e}")

        if errors:
            print(f"[{label}] !! console errors/warnings:")
            for e in errors[:10]:
                print(f"  {e}")
        else:
            print(f"[{label}] no console errors")

        browser.close()


print("=== Mobile viewport 390x844 (iPhone 14) ===")
run_one((390, 844), "mobile")

print("\n=== Desktop viewport 1280x800 ===")
run_one((1280, 800), "desktop")

print(f"\nScreenshots saved to: {OUT_DIR}")
