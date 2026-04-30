import os
import time
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
    os.system("cls" if os.name == "nt" else "clear")
def render_number_text(number_text):
    lines = ["", "", ""]
    for ch in number_text:
        digit_lines = DIGITS[ch]
        for i in range(3):
            lines[i] += digit_lines[i] + "  "
    return "\n".join(lines)


def countdown(start=10):
    for num in range(start, -1, -1):
        clear_screen()
        print("-------倒计时牌开始-------")
        print(f"剩余时间：{num} 秒\n")
        print(render_number_text(str(num)))
        time.sleep(1)

    print("\n-------倒计时结束-------")


if __name__ == "__main__":
    countdown()
