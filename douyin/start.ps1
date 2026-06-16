# 天庭号机器人启动器
chcp 65001 | Out-Null

Write-Host "[1/4] 清理旧机器人进程..." -ForegroundColor Yellow

# 1. 杀死所有运行抖音机器人的Python进程（仅杀机器人，不影响其他Python程序）
Get-Process python -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and $_.CommandLine.Contains("douyin_guardian")
} | Stop-Process -Force -ErrorAction SilentlyContinue

# 2. 杀死所有旧的启动脚本进程（仅杀本脚本的旧进程，不影响其他PS窗口）
Get-Process powershell -ErrorAction SilentlyContinue | Where-Object {
    $_.Id -ne $PID -and $_.CommandLine -and $_.CommandLine.Contains("start.ps1")
} | Stop-Process -Force -ErrorAction SilentlyContinue

Start-Sleep -Seconds 1
Write-Host "[OK] 旧机器人进程已清理" -ForegroundColor Green

Write-Host "[2/4] 打开抖音群聊页面..." -ForegroundColor Yellow
# 直接打开抖音群聊独立弹窗，不影响其他页面
Start-Process msedge -ArgumentList "--app=https://www.douyin.com/chat?isPopup=1"
Start-Sleep -Seconds 5
Write-Host "[OK] 抖音群聊窗口已打开" -ForegroundColor Green

Write-Host "[3/4] 启动天庭号机器人..." -ForegroundColor Green
Set-Location "F:\openclaw_workspace\skills\douyin-guardian"
$env:PYTHONIOENCODING = "utf-8"
python -u douyin_guardian.py

# 启动完成后暂停，避免闪退
Read-Host "按任意键退出"
