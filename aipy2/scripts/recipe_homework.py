import random  
ingredients = {
    "蛋白质": ["鸡蛋", "三文鱼", "鸡胸肉", "豆腐"],
    "碳水": ["燕麦", "红薯", "全麦面包", "糙米"],
    "蔬菜": ["胡萝卜", "青椒", "蘑菇", "菠菜", "西兰花"]
}

recipe = {
    "蛋白质": random.choice(ingredients["蛋白质"]),
    "碳水": random.choice(ingredients["碳水"]),

    "蔬菜": random.sample(ingredients["蔬菜"], 2) 
}

print("今日健康食谱：")

print(recipe)

