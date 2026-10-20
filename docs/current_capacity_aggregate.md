# QuickWal current capacity aggregate

Date: 2026-10-20

## Scope and measurement method

These measurements use the local QuickWal environment with the application constrained to 200% CPU (two CPU-equivalents) and 2 GiB memory. Kafka topics have 8 partitions, listener concurrency is 8, Spring virtual threads are enabled, the outbox batch size is 1,000 with a 100 ms poll delay, and the relay now sends records asynchronously and waits for the batch acknowledgments before deleting rows. The high-rate tests used the persisted runner [`scripts/testing/benchmark_transfers_steady.py`](../scripts/testing/benchmark_transfers_steady.py), random distinct wallet pairs from the seeded 10,000-wallet pool, seed 20261020, 1 QC minor unit, 150 POST concurrency, 256 poll workers, and 500 ms status polling. Each full test offered arrivals for 60 seconds.

**Completed-batch throughput** is terminal successful transfers divided by total elapsed time, including the drain period. POST latency measures the HTTP submission. POST-to-terminal latency is client-observed through status polling and includes polling interval/scheduling delay. The PostgreSQL sampler takes one-second snapshots and can miss shorter waits. High-rate Kafka lag and per-run cgroup counter deltas were not captured, so this report measures application-level outcomes and PostgreSQL outbox behavior rather than identifying every bottleneck.

## Previous results

The table separates the sequential outbox publisher from the asynchronous publisher. These are different code paths and should not be blended as if they were one matched experiment.

| Publisher path | Offered/s | Run | Completed/s | Drain | POST p95/p99 | Observed POST-to-terminal p95/p99 | Sampled outbox max / oldest age |
|---|---:|---:|---:|---:|---:|---:|---:|
| Sequential send-and-wait | 30 | 1 | 28.19 | 3.843 s | 20.9 / 44.8 ms | 5.091 / 5.155 s | 284 / 2.506 s |
| Sequential send-and-wait | 32 | 1 | 27.67 | 9.392 s | 49.5 / 98.8 ms | 12.213 / 12.474 s | 610 / 5.290 s |
| Async batch acknowledgment | 32 | 1 | 31.73 | 0.506 s | 12.3 / 20.8 ms | 1.021 / 1.034 s | 17 / 0.139 s |
| Async batch acknowledgment | 32 | 2 | 31.73 | 0.514 s | 13.7 / 36.6 ms | 1.018 / 1.053 s | 22 / 0.254 s |
| Async batch acknowledgment | 36 | 1 | 35.60 | 0.674 s | 11.9 / 17.8 ms | 1.021 / 1.033 s | 26 / 0.154 s |
| Async batch acknowledgment | 40 | 1 | 39.56 | 0.670 s | 13.3 / 19.3 ms | 1.031 / 1.038 s | 30 / 0.175 s |
| Async batch acknowledgment | 48 | 1 | 47.21 | 1.010 s | 18.7 / 271.1 ms | 1.042 / 1.620 s | 70 / 0.706 s |

The async publisher substantially improved the 32/s comparison and kept the outbox small through the 48/s run. At 48/s, all 2,880 requests completed successfully with no dropped arrivals. This established headroom beyond the earlier roughly 32/s sequential-publisher limit, but did not locate the new capacity knee.

## Binary search and follow-up results

The first requested interval was 90–150/s. A midpoint at 120/s overloaded the system, followed by 105/s, approximately 98/s, and a complete 90/s run. Since even 90/s showed substantial queueing, the search moved below 90/s. The later points narrowed the stable/unstable transition. Offered rates below are rounded to whole requests/s as requested; request counts are exact. Two points shown as 85/s are distinct: one was 5,092 requests (about 84.9/s), and the other was exactly 5,100 requests (85/s).

All completed runs in this table accepted every offered request (HTTP 202), dropped no arrival slots, and ultimately reached `SUCCESSFUL`. The listed database effects remain; no reset was performed. The interrupted 90/s attempt is excluded from the throughput table because it stopped after about 24 seconds and did not report exact accepted/completed counts. The 150/s endpoint was not run.

| Offered/s (rounded) | Requests | Total elapsed | Completed/s | Drain | POST p95/p99 | Arrival scheduling lag p99 | Observed completion p95/p99 | Outbox max / oldest age | Assessment |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 120 | 7,200 | 91.8 s | 78.4 | 31.8 s | 481 / 655 ms | 18.8 s | 17.2 / 20.9 s | 575 / 2.8 s | Overloaded |
| 105 | 6,300 | 97.3 s | 64.8 | 37.3 s | 440 / 566 ms | 22.8 s | 35.8 / 38.6 s | 1,005 / 4.5 s | Overloaded |
| 98 | 5,850 | 88.7 s | 66.0 | 28.7 s | 421 / 559 ms | 16.7 s | 26.1 / 27.8 s | 1,050 / 4.7 s | Overloaded |
| 90 | 5,400 | 77.5 s | 69.7 | 17.5 s | 390 / 534 ms | 4.5 s | 23.3 / 25.2 s | 1,154 / 3.8 s | Overloaded |
| 70 | 4,170 | 61.0 s | 68.4 | 1.0 s | 73 / 175 ms | 0.034 s | 1.6 / 2.5 s | 132 / 0.405 s | Kept up |
| 80 | 4,785 | 61.1 s | 78.4 | 1.1 s | 64 / 154 ms | 0.015 s | 1.3 / 1.9 s | 147 / 0.372 s | Kept up |
| 85 | 5,092 | 75.1 s | 67.8 | 15.1 s | 382 / 517 ms | 1.5 s | 22.3 / 24.1 s | 1,465 / 4.8 s | Overloaded; offered rate was about 84.9/s |
| 82 | 4,939 | 61.1 s | 80.8 | 1.1 s | 164 / 264 ms | 0.073 s | 3.1 / 3.5 s | 269 / 0.726 s | Kept up |
| 84 | 5,016 | 61.1 s | 82.1 | 1.1 s | 183 / 292 ms | 0.161 s | 3.5 / 4.4 s | 239 / 0.611 s | Kept up |
| 84 | 5,054 | 61.2 s | 82.6 | 1.2 s | 195 / 282 ms | 0.141 s | 2.7 / 3.1 s | 215 / 0.590 s | Kept up |
| 85 | 5,100 | 61.1 s | 83.4 | 1.1 s | 135 / 211 ms | 0.062 s | 2.5 / 3.1 s | 227 / 0.676 s | Kept up |
| 88 | 5,280 | 61.8 s | **85.4** | 1.8 s | 73 / 120 ms | 0.020 s | 1.8 / 2.4 s | 288 / 0.836 s | Kept up on first run |
| 89 | 5,340 | 80.5 s | 66.3 | 20.5 s | 393 / 522 ms | 6.5 s | 24.9 / 27.5 s | 1,377 / 5.3 s | Overloaded |
| 88 | 5,280 | 79.9 s | 66.1 | 19.9 s | 438 / 574 ms | 4.1 s | 23.8 / 24.9 s | 766 / 3.1 s | Overloaded on repeat |
| 87 | 5,220 | 75.5 s | 69.1 | 15.5 s | 359 / 471 ms | 1.2 s | 20.1 / 22.2 s | 1,079 / 3.8 s | Overloaded |

The one-second PostgreSQL sampler observed no lock waiters or deadlocks during these runs. That does not rule out waits shorter than the sampling interval. Outbox rows and age rose sharply in runs whose arrival schedules fell behind. Kafka lag was not sampled during this high-rate search. High-rate runs did not include matching pre/post cgroup CPU counter snapshots, so the results cannot show whether CPU quota throttling caused the variation. The interrupted 90/s attempt also left an unknown number of accepted requests to finish asynchronously; the following complete runs all reached terminal success.

## Current capacity conclusion

- **Highest single-run completed-batch throughput:** **85.4 successful transfers/s**, at 88 offered/s. That run had low measured tails and drained in 1.8 seconds.
- **Repeatability warning:** repeating 88/s completed at only 66.1/s, drained for 19.9 seconds, and reached 24.9-second observed p99 completion with a 766-row outbox. Therefore 85.4/s is a best observed point, not a defensible repeatable peak.
- **Provisional operating point:** 80 offered/s produced 78.4 completed/s, about 1.1 seconds of drain, and observed p95/p99 completion of 1.3/1.9 seconds. Runs at rounded 82–85/s also kept their sampled queues bounded in most cases, but tail latency was higher and each load point has only one observation.
- **Working estimate:** the capacity knee is around **85–88 offered transfers/s**, with useful low-queue throughput roughly **80–83 completed transfers/s** in the observed runs. Keep **80 offered/s** as the conservative candidate point until repeat runs establish a stable SLO boundary. This is not yet a statistically validated hard maximum.

At 90/s and tested higher midpoints (98, 105, and 120/s), the service accepted all requests but built substantial asynchronous backlog and exhibited long drain and tail latency. This means HTTP acceptance rate must not be presented as completed-transfer capacity. The data supports an application-level knee estimate under this local 2-CPU-equivalent / 2-GiB app limit; it does not prove production-server capacity or isolate the limiting component. In particular, no high-rate Kafka lag or per-run cgroup throttling data was collected, while the measured outbox buildup is consistent with workflow processing falling behind.

The next validation, if requested, should repeat 80 and 85/s at least three times each, with queue-empty preconditions and continuous cgroup, Kafka lag, outbox, Hikari, and stage-completion sampling. This report records the runs already performed; no additional load was generated to create it.
