class Car:
    def __init__(self, name, brand):
        self.name = name
        self.brand = brand

    def show(self):
        print("汽车名称：", self.name)
        print("汽车品牌：", self.brand)

    def run(self):
        print(f"汽车{self.name}跑起来了。")


# 测试代码
car1 = Car("小米SU7", "小米")
car1.show()
car1.run()