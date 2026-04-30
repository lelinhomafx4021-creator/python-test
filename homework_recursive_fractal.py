import turtle

def koch_curve(pen, length, depth):
    if depth == 0:
        pen.forward(length)
    else:
        koch_curve(pen, length / 3, depth - 1)
        pen.left(60)
        koch_curve(pen, length / 3, depth - 1)
        pen.right(120)
        koch_curve(pen, length / 3, depth - 1)
        pen.left(60)
        koch_curve(pen, length / 3, depth - 1)


# 【教学修改】用 3 条科赫曲线拼成一个科赫雪花
def koch_snowflake(pen, length, depth):
    for _ in range(3):
        koch_curve(pen, length, depth)
        pen.right(120)


def main():
    screen = turtle.Screen()
    screen.title("递归分形图 - 科赫雪花")
    screen.bgcolor("white")

    pen = turtle.Turtle()
    pen.speed(0)
    pen.pensize(2)
    pen.color("blue")

    pen.penup()
    pen.goto(-180, 100)
    pen.pendown()

    koch_snowflake(pen, 360, 4)

    turtle.done()


if __name__ == "__main__":
    main()
