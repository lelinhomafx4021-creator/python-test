# Pressure Test Result

- Target: `http://127.0.0.1:8080`
- Concurrency: `10`
- Requests per API: `20`

| API | Success Rate | Avg(ms) | Min(ms) | Max(ms) | QPS |
| --- | ---: | ---: | ---: | ---: | ---: |
| `auth/me` | 100.0% | 37.0 | 18.9 | 69.3 | 228 |
| `membership` | 100.0% | 320.0 | 31.4 | 776.7 | 26 |
| `quotas` | 100.0% | 92.1 | 26.7 | 169.9 | 96 |
| `quotes` | 100.0% | 2083.4 | 40.3 | 4132.5 | 5 |
| `sectors` | 100.0% | 18.6 | 9.5 | 33.8 | 214 |
| `watchlists` | 100.0% | 92.7 | 14.1 | 211.9 | 78 |
| `paper` | 100.0% | 33.7 | 16.5 | 69.6 | 143 |
| `announcements` | 100.0% | 16.5 | 9.4 | 33.3 | 199 |
| `notifications` | 100.0% | 17.8 | 10.1 | 40.3 | 271 |
| `ai/sessions` | 100.0% | 32.2 | 11.9 | 76.7 | 161 |

## Notes

- This is a lightweight local pressure test for interview demonstration.
- The result mainly reflects gateway interface stability, not full production capacity.
