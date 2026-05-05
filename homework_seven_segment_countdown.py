"""
七段数码管倒计时显示

功能：在终端用 ASCII 字符绘制七段数码管效果的倒计时牌
原理：用 3 行文本模拟每个数字的显示（类似电子表的 LED 数码管）
"""
import os
import time

# 七段数码管字形定义（每个数字用 3 行 ASCII 字符表示）
DIGITS = {
    "0": [" _ ", "| |", "|_|"],
    "1": ["   ", "  |", "  |"],
    "2": [" _ ", " _|", "|_ "],
    "3": [" _ ", " _|", " _|"],
    "4": ["   ", "|_|", "  |"],
    "5": [" _ ", "|_ ", " _|"],
    "6": [" _ ", "|_ ", "|_|"],
    "7": [" _ ", "  |", "  |"],
    "8": [" _ ", "|_|", "|_|"],
    "9": [" _ ", "|_|", " _|"],
}


def clear_screen():
    """清屏：Windows 用 cls，Linux/Mac 用 clear"""
    os.system("cls" if os.name == "nt" else "clear")
def render_number_text(number_text):
    """把数字字符串渲染成 3 行 ASCII 艺术文本"""
    lines = ["", "", ""]
    for ch in number_text:
        digit_lines = DIGITS[ch]
        for i in range(3):
            lines[i] += digit_lines[i] + "  "
    return "\n".join(lines)


def countdown(start=10):
    """从 start 秒开始倒计时到 0"""
    for num in range(start, -1, -1):
        clear_screen()
        print("-------倒计时牌开始-------")
        print(f"剩余时间：{num} 秒\n")
        print(render_number_text(str(num)))
        time.sleep(1)

    print("\n-------倒计时结束-------")


if __name__ == "__main__":
    countdown()
