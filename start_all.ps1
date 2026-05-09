﻿$ErrorActionPreference = "Stop"

# 本地一键启动脚本：
# 1. 拉起根目录 docker compose 中的中间件
# 2. 启动 Python AI、Java 网关、前端开发服务
# 3. 轮询健康检查并输出访问地址

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$logsDir = Join-Path $rootDir "logs"
$pythonDir = Join-Path $rootDir "aipy2"
$javaDir = Join-Path $rootDir "java-ai-gateway"
$frontendDir = Join-Path $rootDir "frontend"
$pythonEnvFile = Join-Path $pythonDir ".env"
$pythonEnvExample = Join-Path $pythonDir ".env.example"
$pythonExe = Join-Path $pythonDir ".venv\Scripts\python.exe"

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

function Write-Step {
    param([string]$message)

    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Assert-Command {
    param(
        [string]$Name,
        [string]$Hint
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name 不可用。$Hint"
    }
}

function Get-DockerComposeInvoker {
    if (Get-Command "docker-compose" -ErrorAction SilentlyContinue) {
        return @{
            FilePath = (Get-Command "docker-compose").Source
            Arguments = "up -d"
        }
    }

    if (Get-Command "docker" -ErrorAction SilentlyContinue) {
        return @{
            FilePath = (Get-Command "docker").Source
            Arguments = "compose up -d"
        }
    }

    throw "未找到 docker-compose 或 docker compose。"
}

function Wait-HttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "$Name 已就绪：$Url" -ForegroundColor Green
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "$Name 启动超时，请检查日志：$Url"
}

function Start-BackgroundProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string]$Arguments,
        [string]$WorkingDirectory,
        [string]$LogPrefix
    )

    $stdout = Join-Path $logsDir "$LogPrefix.out.log"
    $stderr = Join-Path $logsDir "$LogPrefix.err.log"

    Write-Host "启动 $Name ..." -ForegroundColor Yellow
    Start-Process `
        -FilePath $FilePath `
        -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr | Out-Null
}

Write-Step "检查基础依赖"
Assert-Command -Name "docker" -Hint "请先安装 Docker Desktop。"
Assert-Command -Name "java" -Hint "请先安装 JDK 17 或更高版本。"
Assert-Command -Name "node" -Hint "请先安装 Node.js 18 或更高版本。"
Assert-Command -Name "npm" -Hint "请先安装 npm。"
Assert-Command -Name "mvn" -Hint "请先安装 Maven。"

if (-not (Test-Path $pythonExe)) {
    throw "未找到 Python 虚拟环境解释器：$pythonExe"
}

if (-not (Test-Path $pythonEnvFile)) {
    Copy-Item $pythonEnvExample $pythonEnvFile
    throw "已创建 aipy2/.env，请先补齐真实配置后重新执行。"
}

Write-Step "启动中间件容器 (mysql, redis, rabbitmq, postgres)"
$dockerComposeInvoker = Get-DockerComposeInvoker
$dockerArgs = ($dockerComposeInvoker.Arguments + " mysql redis rabbitmq postgres").Split(" ")
& $dockerComposeInvoker.FilePath $dockerArgs

Write-Step "启动 Python AI"
Start-BackgroundProcess `
    -Name "Python AI" `
    -FilePath $pythonExe `
    -Arguments "main.py" `
    -WorkingDirectory $pythonDir `
    -LogPrefix "python"

Write-Step "启动 Java 网关"
Start-BackgroundProcess `
    -Name "Java 网关" `
    -FilePath "mvn" `
    -Arguments "spring-boot:run" `
    -WorkingDirectory $javaDir `
    -LogPrefix "java"

Write-Step "启动前端"
Start-BackgroundProcess `
    -Name "前端" `
    -FilePath "npm" `
    -Arguments "run dev -- --host 127.0.0.1 --port 5173" `
    -WorkingDirectory $frontendDir `
    -LogPrefix "frontend"

Write-Step "等待服务健康检查"
Wait-HttpOk -Name "Python AI" -Url "http://127.0.0.1:8000/ai/v1/util/health"
Wait-HttpOk -Name "Java 网关" -Url "http://127.0.0.1:8080/actuator/health"
Wait-HttpOk -Name "前端" -Url "http://127.0.0.1:5173"

Write-Step "启动完成"
Write-Host "前端工作台：http://127.0.0.1:5173" -ForegroundColor Green
Write-Host "Java 网关：http://127.0.0.1:8080" -ForegroundColor Green
Write-Host "Python AI：http://127.0.0.1:8000" -ForegroundColor Green
Write-Host "RabbitMQ 管理台：http://127.0.0.1:15672" -ForegroundColor Green
Write-Host "演示账号：admin / 123456" -ForegroundColor Green
Write-Host "日志目录：$logsDir" -ForegroundColor Green
