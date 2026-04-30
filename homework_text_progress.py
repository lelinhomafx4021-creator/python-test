import sys
import time

def text_progress(total=50, delay=0.05):
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
