"""
文本进度条

功能：在终端显示一个从 0% 到 100% 的文本进度条
原理：用 \r 回车符覆盖上一次输出，实现原地刷新效果
"""
import sys
import time

def text_progress(total=50, delay=0.05):
    """显示文本进度条
    
    参数：
    - total：总步数（默认50步）
    - delay：每步间隔时间（默认0.05秒）
    """
    print("-------执行开始-------")

    for i in range(total + 1):
        percent = int(i / total * 100)
        bar = "*" * i
        sys.stdout.write(f"\r{percent:>3}%[{bar:<50}->]")
        sys.stdout.flush()
        time.sleep(delay)

    print()
    print("-------执行结束-------")


if __name__ == "__main__":
    text_progress()
