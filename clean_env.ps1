# ==============================================================================
# 环境变量安全清理脚本 (Safe Environment Cleanup Script)
# 功能：备份注册表、自动补全关键系统路径、去除重复、清理无效路径
# ==============================================================================

$BackupFile = "$HOME\Desktop\Env_Backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').reg"

# 1. 备份 (非常重要！)
Write-Host "正在备份环境变量到桌面: $BackupFile ..." -ForegroundColor Cyan
reg export "HKCU\Environment" "$BackupFile" /y | Out-Null
reg export "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" "$BackupFile-System.reg" /y | Out-Null

function Optimize-Path {
    param([string]$PathType) # "User" or "Machine"
    
    $RawPath = [Environment]::GetEnvironmentVariable("Path", $PathType)
    $OldPaths = $RawPath -split ';' | Where-Object { $_ -ne "" }
    $NewPaths = New-Object System.Collections.Generic.List[string]
    $Seen = New-Object System.Collections.Generic.HashSet[string]
    
    # 定义关键路径 (仅针对 Machine/System 类型)
    if ($PathType -eq "Machine") {
        $CriticalPaths = @(
            "C:\Windows\system32",
            "C:\Windows",
            "C:\Windows\System32\Wbem",
            "C:\Windows\System32\WindowsPowerShell\v1.0\"
        )
        foreach ($cp in $CriticalPaths) {
            if ($Seen.Add($cp.ToLower())) { $NewPaths.Add($cp) }
        }
    }

    foreach ($p in $OldPaths) {
        $pClean = $p.Trim()
        $pLower = $pClean.ToLower()
        
        # 逻辑：路径存在 且 未见过
        if (Test-Path "$pClean") {
            if ($Seen.Add($pLower)) {
                $NewPaths.Add($pClean)
            } else {
                Write-Host "  [删除重复] $pClean" -ForegroundColor Yellow
            }
        } else {
            Write-Host "  [删除无效] $pClean" -ForegroundColor Red
        }
    }

    $FinalPath = $NewPaths -join ';'
    return $FinalPath
}

# 2. 优化处理
Write-Host "`n处理用户变量 (User Path)..." -ForegroundColor Cyan
$NewUserPath = Optimize-Path -PathType "User"

Write-Host "`n处理系统变量 (System Path)..." -ForegroundColor Cyan
$NewSystemPath = Optimize-Path -PathType "Machine"

# 3. 应用更改 (需要管理员权限)
Write-Host "`n--- 正在应用更改 ---" -ForegroundColor Green

[Environment]::SetEnvironmentVariable("Path", $NewUserPath, "User")
try {
    [Environment]::SetEnvironmentVariable("Path", $NewSystemPath, "Machine")
    Write-Host "系统变量与用户变量均已更新。" -ForegroundColor Green
} catch {
    Write-Host "系统变量更新失败（权限不足），仅用户变量已更新。" -ForegroundColor Yellow
    Write-Host "错误信息: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n清理完成！请重启终端或电脑以使更改生效。" -ForegroundColor Green
