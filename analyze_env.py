import os
import winreg


def get_reg_env(key_path, value_name, hive=winreg.HKEY_CURRENT_USER):
    """
    从注册表读取环境变量值。

    参数：
    - key_path：注册表项路径。
    - value_name：注册表中的值名称。
    - hive：注册表根键，默认使用当前用户（HKEY_CURRENT_USER）。
    """
    try:
        with winreg.OpenKey(hive, key_path) as key:
            value, _ = winreg.QueryValueEx(key, value_name)
            return value
    except Exception as e:
        return f"读取失败：{e}"


def analyze_path(path_str):
    """
    分析路径字符串中的每个路径项。

    返回结果包含：
    - path：原始路径字符串。
    - exists：该路径在本机是否存在。
    - is_duplicate：该路径是否在此前已经出现过（重复项）。
    """
    paths = path_str.split(";")
    results = []
    seen = set()

    for p in paths:
        p = p.strip()
        if not p:
            continue

        exists = os.path.exists(p)
        is_dup = p in seen
        seen.add(p)

        results.append(
            {
                "path": p,
                "exists": exists,
                "is_duplicate": is_dup,
            }
        )

    return results


# 读取“用户级”路径环境变量（当前用户）
user_path_str = get_reg_env("Environment", "Path", winreg.HKEY_CURRENT_USER)

# 读取“系统级”路径环境变量（本机级别）
system_path_str = get_reg_env(
    r"SYSTEM\CurrentControlSet\Control\Session Manager\Environment",
    "Path",
    winreg.HKEY_LOCAL_MACHINE,
)

# 输出用户路径的分析结果
print("--- 用户路径分析 ---")
user_results = analyze_path(user_path_str)
for r in user_results:
    status = "[存在]" if r["exists"] else "[不存在]"
    dup = "[重复]" if r["is_duplicate"] else ""
    print(f"{status}{dup} {r['path']}")

# 输出系统路径的分析结果
print("\n--- 系统路径分析 ---")
system_results = analyze_path(system_path_str)
for r in system_results:
    status = "[存在]" if r["exists"] else "[不存在]"
    dup = "[重复]" if r["is_duplicate"] else ""
    print(f"{status}{dup} {r['path']}")

# 检查关键系统路径是否存在
print("\n--- 关键路径检查 ---")
critical = [
    r"C:\Windows\system32",
    r"C:\Windows",
    r"C:\Windows\System32\Wbem",
    r"C:\Windows\System32\WindowsPowerShell\v1.0\\",
]
all_paths = [r["path"].lower() for r in user_results + system_results]
for c in critical:
    if c.lower() not in all_paths:
        print(f"[缺失] {c}")
    else:
        print(f"[已存在] {c}")
