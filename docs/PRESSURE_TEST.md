# Pressure Test

## Purpose

这份压测不是做生产级容量规划，而是补一页真实的工程证据，让面试时能回答：

- 核心接口不是没测过
- 网关在并发下不是完全裸奔
- 你知道怎么用成功率、延迟和 QPS 描述接口稳定性

## Scope

当前压测脚本覆盖的是 Java 网关的典型登录态接口：

- `auth/me`
- `membership`
- `quotas`
- `quotes`
- `sectors`
- `watchlists`
- `paper`
- `announcements`
- `notifications`
- `ai/sessions`

这类接口适合体现“业务网关”的基础稳定性。AI 长文本流式问答更适合单独做链路演示，不建议和普通接口混在一轮压测里。

## Run

先确保服务已启动：

```powershell
.\start_all.ps1
```

然后执行压测：

```powershell
python .\stress_test.py --concurrency 20 --requests 100 --markdown-out docs/PRESSURE_TEST_RESULT.md
```

如果想加大一点并发：

```powershell
python .\stress_test.py --concurrency 50 --requests 200 --markdown-out docs/PRESSURE_TEST_RESULT.md
```

## Metrics

- 成功率：接口返回 `200` 的比例
- 平均响应时间：整体延迟水平
- 最小/最大响应时间：波动区间
- QPS：单位时间处理请求数

## Result Template

服务启动后，脚本会自动生成一份 markdown 结果文件。你也可以先按这个模板手动整理：

| API | Success Rate | Avg(ms) | Min(ms) | Max(ms) | QPS |
| --- | ---: | ---: | ---: | ---: | ---: |
| `auth/me` | 待补 | 待补 | 待补 | 待补 | 待补 |
| `quotes` | 待补 | 待补 | 待补 | 待补 | 待补 |
| `watchlists` | 待补 | 待补 | 待补 | 待补 | 待补 |
| `paper` | 待补 | 待补 | 待补 | 待补 | 待补 |
| `ai/sessions` | 待补 | 待补 | 待补 | 待补 | 待补 |

## How To Explain In Interview

- 这轮压测主要验证的是网关层接口的基础稳定性，不是整套系统的极限容量。
- 我优先看成功率和平均延迟，先确认接口可用，再看是否需要做缓存、限流或异步化。
- 对这个项目来说，小而真实的压测结果，比堆一堆分布式术语更有说服力。
