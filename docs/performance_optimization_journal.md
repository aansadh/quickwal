# QuickWal performance optimization journal

This file is the durable learning log for capacity observations, bottleneck hypotheses, mitigations, and their measured results. It records what the evidence supports, what remains unknown, and the next experiment. A proposed configuration change is not treated as successful until a controlled before/after run shows the intended effect.

## Measurement contract

- Keep workload profiles separate. The fixed hot-wallet pair is a contention stress case; a randomized wallet pool is a distributed-key case. Neither alone represents every production workload.
- Record offered requests/s, HTTP accepted/rejected/error counts, terminal outcomes, POST p50/p95/p99, observed POST-to-terminal p50/p95/p99, completed-batch throughput, and drain time.
- Record resource limits, JVM effective processor/heap settings, Hikari metrics, Kafka partition/consumer/lag state, PostgreSQL lock waits, outbox count/age, and test data effects.
- Status-poll-based POST-to-terminal time includes polling cadence and client scheduling. It is not server-only latency.
- Do not infer a bottleneck from an idle/post-run snapshot. Capture queue and wait state during load and preserve the sample interval.
- Change one major capacity variable at a time. Do not increase pool size, relay cadence, batch size, and Kafka concurrency in the same comparison.

## Baseline: fixed hot-wallet workload

**Environment:** Spring Boot app cgroup capped at 2 CPU-equivalents and 2 GiB; current PostgreSQL/Kafka Compose services; `wallet-commands` and `transfer-events` each have one partition; same seeded source/target wallet pair; one QC minor unit per transfer. Application Hikari maximum is 10. The container resource settings are recorded as observed conditions, not carefully sized recommendations.

| Offered rate | Duration / transfers | All accepted and successful? | Completed-batch throughput | Drain | Observed POST-to-terminal p95 / p99 |
|---:|---:|---|---:|---:|---:|
| 20/s | 60 s / 1,200 | Yes | 18.90/s | 3.504 s | 4.028 / 4.035 s |
| 21/s | 60 s / 1,260 | Yes | 19.69/s | 4.001 s | 5.068 / 5.551 s |
| 22/s | 60 s / 1,320 | Yes | 20.75/s | 3.601 s | 4.530 / 5.026 s |
| 23/s | 60 s / 1,380 | Yes | 20.77/s | 6.429 s | 8.549 / 9.054 s |
| 24/s | 60 s / 1,440 | Yes | 20.97/s | 8.662 s | 12.100 / 12.170 s |

Completed-batch throughput is successful terminal transfers divided by total load-plus-drain time; it is not a continuously sampled service-rate curve. The measured throughput knee is around 21 transfers/s. Between 22 and 23 offered requests/s, the completed-batch result stayed flat while drain and observed end-to-end latency rose. These are results for the hot-wallet workload only.

Earlier evidence under the same application cgroup found roughly 375 MiB current app memory, a service-lifetime peak near 435 MiB, and no memory-limit events. Post-run Hikari samples showed 0 active / 10 idle / 0 pending, Kafka lag and outbox rows were zero after drain, and PostgreSQL had 10 idle connections plus the inspection connection. These snapshots do not rule out short waits or in-run backlog. The later multi-rate sweep recorded cgroup throttling counters, but did not have a matching sweep-start snapshot for attribution.

## Bottleneck hypotheses and assessment

### Wallet row contention

**Mechanism:** [`WalletRepositoryImpl.java`](../src/main/java/com/quickwal/wallet/adapter/out/WalletRepositoryImpl.java) updates `balance_minor` by wallet ID. Concurrent updates to the same row serialize at PostgreSQL. The hot-pair benchmark intentionally drives repeated writes to two wallet rows.

**Assessment:** plausible source of the knee, not proven as the dominant cause. We have not sampled PostgreSQL lock waits during the measured load. Application POST latency remained much shorter than POST-to-terminal workflow latency, so the delay is downstream/asynchronous, but that fact alone does not identify row-lock waiting.

**Mitigation experiment:** implemented an additive seed of 10,000 funded benchmark wallets and deterministic random selection of distinct source/target wallets per transfer. Both the fixed-pair and random-pool modes remain available in [`scripts/testing/benchmark_transfers_steady.py`](../scripts/testing/benchmark_transfers_steady.py). The seed fixture is [`scripts/testing/seed_benchmark_wallets.sql`](../scripts/testing/seed_benchmark_wallets.sql). The benchmark selection seed is 20261020.

**Seed result:** inserted 10,000 new users and 10,000 new QC wallets. Each fixture wallet starts with 1,000,000 minor units. Existing wallets were not updated and the database was not reset. Fixture opening balances do not create ledger entries.

**Matched comparison at 22 offered transfers/s:** each run scheduled 1,320 arrivals over 60 seconds, used 150 maximum concurrent POSTs, 256 poll workers, 500 ms polling, and transferred 1 minor unit. Every accepted request was HTTP 202 and every transfer reached `SUCCESSFUL`; zero arrival slots were dropped.

| Pair | Fixed wallet pair: completed-batch/s; drain; observed E2E p95/p99 | Random 10,000-wallet pool: completed-batch/s; drain; observed E2E p95/p99 |
|---|---|---|
| First | 18.96/s; 9.626 s; 13.022 / 13.478 s | 19.27/s; 8.487 s; 11.680 / 12.152 s |
| Repeat | 17.97/s; 13.448 s; 18.425 / 18.783 s | 19.72/s; 6.943 s; 9.564 / 9.577 s |

Randomization improved completed-batch throughput in both adjacent pairs, but the improvement varied from 0.31/s to 1.75/s, and all four runs were much slower than an earlier 22/s sample. This is directional evidence, not a clean causal result. The results do not justify saying the hot-wallet lock was the dominant bottleneck. Randomized traffic should remain a separate profile, not replace the hot-wallet case.

**Data caveat:** synthetic opening balances are local test fixtures and are inserted without ledger opening entries. The seed file is additive/idempotent and does not reset or update existing balances. Actual transfers generated by the benchmark remain persistent.

### Outbox relay cadence and batch size

**Current code/config:** [`OutboxRelayJob.java`](../src/main/java/com/quickwal/common/job/OutboxRelayJob.java) uses `@Scheduled(fixedDelayString=...)`, selects at most the configured batch with `FOR UPDATE SKIP LOCKED`, synchronously waits for each Kafka send in sequence, then deletes rows within the transaction. Initial settings were 500 ms delay, batch size 100, send timeout 10,000 ms. After the controlled comparisons below, current settings are 100 ms delay and batch size 1,000; send timeout remains 10,000 ms.

**Assessment:** 100 rows / 0.5 s = 200 outbox rows/s is only an idealized zero-runtime upper bound, not an observed rate and not 200 transfers/s. A successful normal transfer creates roughly four outbox records (debit command, debit completion event, credit command, credit completion event), so the naive no-overhead bound would correspond to about 50 successful transfers/s. Actual capacity is lower because relay invocation duration and sequential send latency consume time. No evidence yet shows the relay is limiting the measured ~21/s.

**Measurement result:** [`scripts/testing/sample_postgres_load.py`](../scripts/testing/sample_postgres_load.py) sampled PostgreSQL once per second across both runs in the repeated pair. Across 145 snapshots, it observed zero lock-waiting sessions, at most 4 active sessions, a maximum sampled outbox depth of 467 rows, and oldest sampled outbox age of 5,881 ms; the deadlock counter remained zero. This sampler can miss waits shorter than its interval. The outbox queue observation is evidence to investigate relay throughput, not proof that the relay alone caused the end-to-end delay.

**Batch-size experiment (100 to 500, 500 ms delay held constant):** at 22 offered requests/s with the random 10,000-wallet profile, two batch-500 runs completed at 20.69 and 20.68/s, drained in 3.789 and 3.832 s, and observed E2E p95/p99 of 5.577/6.063 s and 5.042/5.051 s. Against the earlier batch-100 random runs (19.27 and 19.72/s; drain 8.487 and 6.943 s; E2E p95/p99 11.680/12.152 s and 9.564/9.577 s), batch 500 was directionally better. Different run cohorts and considerable variation limit causal certainty. PostgreSQL's one-second sampler saw no lock waiters during those comparisons; queue peaks in batch-500 runs were up to 232 rows and about 2.3 s old.

**Cadence experiment (500 ms to 100 ms, batch 500 held constant):** two matched 22/s random-wallet runs both completed at 21.64/s, drained in about 0.99 s, and had E2E p95/p99 of 1.535/1.570 s and 1.523/1.526 s. Compared with batch 500 / 500 ms runs, this was a large observed improvement in drain and workflow latency. Sampled outbox maxima were 59 rows / 586 ms and zero, with no sampled lock waits. Kafka lag sampled mid-run was zero in one run and briefly 1 transfer-event / 3 wallet-command records in the other, then drained. This supports relay cadence as a meaningful contributor under this workload, but is not an isolated measurement of relay service time.

**Batch experiment (500 to 1,000, 100 ms delay held constant):** current config is [`application.kafka.properties`](../src/main/resources/application.kafka.properties). The 30/s random-wallet run completed all 1,800 requests, with 28.56 completed-batch transfers/s, 3.030 s drain, POST p95/p99 10.8/20.2 ms, and observed E2E p95/p99 4.045/4.057 s. This compares directionally favorably with batch 500 / 100 ms at 30/s (26.63/s, 7.582 s drain, E2E p95/p99 10.721/11.095 s), but only one run per configuration is available. During the batch-1,000 run, 75 one-second PostgreSQL samples saw maximum outbox depth 219, oldest age 1.767 s, at most 3 active sessions, zero sampled lock waiters, and zero deadlocks. After drain, the outbox was empty, Hikari active connections were zero, and Actuator JVM memory used was about 153 MiB across heap and non-heap areas (the metric response shown was not filtered to a single area).

**Queueing sweep with batch 1,000 / delay 100 ms:** same randomized wallet pool, seed, 1 minor unit per request, 60-second arrival window, 150 max concurrent POSTs, 256 poll workers, 500 ms status polling, and 2-CPU / 2-GiB app cgroup.

| Offered rate | Requests | Completed-batch throughput | Drain | POST p95/p99 | Observed E2E p95/p99 | PostgreSQL sampled peak outbox / age |
|---:|---:|---:|---:|---:|---:|---:|
| 24/s | 1,440 | 23.44/s | 1.421 s | 10.6 / 13.1 ms | 2.035 / 2.046 s | Included in combined 24–28 sweep: max 251 / 2.241 s |
| 26/s | 1,560 | 25.20/s | 1.900 s | 11.5 / 14.8 ms | 3.050 / 3.062 s | Included in combined 24–28 sweep |
| 28/s | 1,680 | 26.45/s | 3.504 s | 13.9 / 31.1 ms | 4.622 / 5.113 s | Included in combined 24–28 sweep |
| 30/s | 1,800 | 26.63/s | 7.582 s | 194.7 / 330.7 ms | 10.721 / 11.095 s | 589 / 5.576 s |
| 30/s, batch 1,000 | 1,800 | **28.56/s** | **3.030 s** | **10.8 / 20.2 ms** | **4.045 / 4.057 s** | 219 / 1.767 s |
| 31/s | 1,860 | 23.70/s | 18.494 s | 427.8 / 700.9 ms | 23.592 / 23.915 s | 1,233 / 12.319 s |
| 32/s | 1,920 | 27.67/s | 9.399 s | 150.7 / 252.9 ms | 12.575 / 12.972 s | 667 / 6.533 s |

Every run accepted all offered POSTs, dropped no arrival slots, and ended with every transfer `SUCCESSFUL`. The 31/s and 32/s results are not monotonic, which is a warning against treating one finite batch as a precise capacity number. They both show much larger queues, drain, and tail latency than the 30/s batch-1,000 run. One-second sampling saw no PostgreSQL lock waiters or deadlocks in either run, while sampled outbox depth/age rose sharply. After the sweep the outbox had drained to zero; Hikari active connections were zero. Cgroup counters since the service start were `usage_usec=277,708,432`, `nr_throttled=287`, `throttled_usec=28,054,507`; memory.current was 424,448,000 B, memory.peak 426,565,632 B, and all `memory.events` counters were zero. These counters are cumulative since service start and cannot be attributed to a particular run. Do not read the low memory usage as proof memory cannot become a constraint under a longer or different workload.

**Interpretation and operating point:** the highest observed completed-batch result after the relay tuning is 28.56 successful transfers/s at 30 offered/s, but that is one 60-second run and includes drain. Treat 30 offered/s as a candidate tested operating point for the 2-CPU / 2-GiB app envelope, not a statistically established safe capacity. The 31/s run already showed severe queue growth and 23.6 s E2E p95; 32/s also showed overloaded tails despite a better batch-average rate. The practical low-latency knee lies around 30 offered requests/s for this randomized workload and current local dependency topology. A production SLO should run repeated longer tests at 28–30/s and monitor queue stability before using a capacity figure. Offered arrivals accepted by HTTP are not service throughput: the API can return 202 while the asynchronous workflow queue grows.

### Kafka partitions, concurrency, key ordering, and virtual threads

**Original baseline:** both topics had one partition and one active consumer per group. That capped each group at one active partition worker regardless of listener concurrency.

**Current test configuration:** both existing topics were expanded in place from 1 to 8 partitions; replication factor remains 1 on the single local broker. `spring.kafka.listener.concurrency=8` is set, and verification showed eight consumer instances assigned eight partitions in each group. The app remained capped at 200% CPU (2 CPU-equivalents) / 2 GiB. This increases potential I/O concurrency but does not grant more CPU. Partition expansion is effectively one-way and changes key-to-partition placement for future records; before expansion both groups were caught up and the outbox was empty. Existing Kafka records and database contents were preserved.

**Ordering fix required for safe partitioning:** previously, the outbox relay used `aggregate_id` as the Kafka key for all records. Transfer aggregate records are wallet commands and therefore used a transfer ID; the same wallet could receive commands from different transfers in different partitions. The relay now derives a wallet command's key from its `sourceWalletId` or `targetWalletId` payload field. Wallet result events continue to use their wallet aggregate ID. Outbox rows still retain their transfer aggregate ID. Thus commands/events for a wallet are routed consistently within each topic, and each blocking listener processes its assigned partition sequentially. The command key change compiled successfully. Existing records were drained before partition expansion.

**Virtual threads:** `spring.threads.virtual.enabled=true` is set in `application.properties`; `spring.main.keep-alive=true` keeps the JVM alive when the scheduler uses daemon virtual threads. Spring Boot uses virtual threads for its managed task execution and supported web request paths under Java 21+. Kafka listener concurrency is separately configured to 8; do not interpret the virtual-thread switch as converting the Kafka consumer loops to virtual threads. This experiment changed partitioning/concurrency and enabled virtual threads together, so their individual effects are not isolated.

**Acknowledgment mode:** `spring.kafka.listener.ack-mode=record` remains unchanged and `spring.kafka.consumer.enable-auto-commit=false` remains set. Record mode commits the offset after each synchronous record listener returns; it does not limit consumer concurrency or establish key ordering. It can produce more frequent commits than batch mode, so commit overhead is a possible measurement factor, not a conflict with 8-way consumption.

**Combined experiment at batch 1,000 / 100 ms relay settings:** the random 10,000-wallet profile used 60-second runs, same deterministic selection seed, 1 QC minor unit, 150 max in-flight POSTs, 256 polling workers, and 500 ms status polling. Each result was compared with the immediately preceding one-partition/virtual-threads-off result under the same workload rate.

| Offered rate | Topology | Transfers | Completed-batch throughput | Drain | POST p95/p99 | Observed E2E p95/p99 | Sampled outbox peak / age |
|---:|---|---:|---:|---:|---:|---:|---:|
| 30/s | 1 partition / listener default; virtual threads off | 1,800 | 28.56/s | 3.030 s | 10.8 / 20.2 ms | 4.045 / 4.057 s | 219 / 1.767 s |
| 30/s | 8 partitions / concurrency 8; virtual threads on | 1,800 | 28.19/s | 3.843 s | 20.9 / 44.8 ms | 5.091 / 5.155 s | 284 / 2.506 s |
| 32/s | 1 partition / listener default; virtual threads off | 1,920 | 27.67/s | 7.582 s | 194.7 / 330.7 ms | 10.721 / 11.095 s | 589 / 5.576 s |
| 32/s | 8 partitions / concurrency 8; virtual threads on | 1,920 | 27.67/s | 9.392 s | 49.5 / 98.8 ms | 12.213 / 12.474 s | 610 / 5.290 s |

All 7,440 transfers across these four comparison runs were accepted, had no dropped arrival slots, and reached `SUCCESSFUL`; every transfer's database effects remain. The 8-partition group assignments used all eight partitions. Mid-run lag at 30/s was zero on both topics; at 32/s transfer-event lag was zero and wallet-command lag was 0–3 records across partitions. The 75-sample PostgreSQL samplers reported no sampled lock waiters/deadlocks; active-session maxima were 4 at 30/s and 6 at 32/s. These are point samples and may miss short waits. Post-run outbox and Kafka lag both drained to zero.

**Result:** the combined change did not raise the observed completed-batch throughput in these runs: 28.19/s at 30 offered/s versus the prior 28.56/s, and 27.67/s at 32 offered/s in both configurations. At 30/s the 8-partition run had higher client-observed tails; at 32/s POST tails improved versus the one-partition run but workflow p95/p99 were slightly higher and drain longer. These single-run comparisons do not prove the partition change harmed performance; they show no demonstrated throughput gain at the tested rates. The sparse Kafka lag and larger outbox queue under 32/s suggest Kafka consumer backlog was not the obvious constraint in these samples. Virtual threads and partition changes were enabled together, so attribution needs a controlled follow-up that toggles one factor at a time.

**Post-run resource snapshot:** service remained active at `CPUQuotaPerSecUSec=2s` and `MemoryMax=2147483648`. Cgroup counters since this service cgroup started were `usage_usec=155,071,428`, `nr_throttled=80`, and `throttled_usec=3,669,301`; without a matching immediate pre-run snapshot, these cannot be attributed to either benchmark. Current memory was 472,596,480 B, lifetime peak 474,476,544 B, with `memory.events` all zero. Outbox count/age was 0/0 after drain. Do not compare this cgroup memory snapshot directly with prior service lifetimes.

## Actions and results

| Date | Action | Result | Decision |
|---|---|---|---|
| 2026-10-19 | Fixed-pair arrival-rate sweep, then 60-second verification and points from 18–24/s | All tested transfers succeeded; completed-batch throughput topped out near 21/s; latency/drain rose sharply beyond 22/s offered | Baseline established; retain as hot-wallet profile |
| 2026-10-20 | Added deterministic 10,000-wallet SQL fixture and repeatable random-pair mode; moved benchmark runners, sampler, and fixture to `scripts/testing` | Seed verified: 10,000 expected wallets; existing balances preserved | Keep both workload profiles |
| 2026-10-20 | Two matched hot-pair vs random-pool comparisons at 22/s, with 1-second PostgreSQL sampling on the repeat | All transfers successful; random profile modestly faster in both pairs, but substantial run-to-run variation; 0 sampled lock waiters; outbox peaked at 467 rows / 5.881 s age | Hot-wallet contention is not established as primary bottleneck; test relay batch size next, in isolation |
| 2026-10-20 | Batch 100→500 at 22/s; then delay 500→100 ms at 22/s; then batch 500→1,000 at 30/s | Batch/delay tuning materially improved observed drain and E2E latency. At batch 1,000, 30/s produced 28.56/s completed-batch rate; 31–32/s caused sharp queue/tail growth. Sampler saw no PostgreSQL lock waits, but outbox grew to 1,233 rows at 31/s | Keep current 100 ms / 1,000 configuration for the next controlled repeat; treat 30/s as candidate operating point, not firm capacity. Repeat 28–30/s and instrument Kafka lag / CPU over each run |
| 2026-10-20 | Expanded both topics from 1 to 8; set listener concurrency 8; keyed wallet commands by wallet ID; enabled Spring virtual threads and keep-alive; ran 30/s and 32/s | Eight consumers per group used all partitions. All 3,720 requests succeeded across the two runs. Completed-batch throughput was 28.19/s at 30/s and 27.67/s at 32/s; no improvement over matched prior one-partition results was demonstrated. Kafka lag samples were near zero; outbox peaked at 610 rows / 5.29 s at 32/s | Keep 8 partitions and wallet keys for the next tests, but isolate virtual-thread effect and repeat rates before claiming a throughput gain. Maintain 200% CPU cap |

## Next steps

1. Isolate the virtual-thread effect by comparing 8 partitions/concurrency 8 with virtual threads disabled versus enabled, holding every other setting, workload, and 200% cgroup fixed. Repeat each point at least three times.
2. Repeat randomized 28/s and 30/s runs under the selected configuration for at least 3 cohorts; capture Kafka lag during load and per-stage completion rates to establish repeatability and stable backlog.
3. Keep the hot-pair profile as a separate diagnostic experiment; do not treat randomized-wallet results as a replacement for contention testing.
4. Continue collecting cgroup counter deltas from immediately before to after each load, and sample app CPU, JVM/GC, Hikari pending, DB waits, Kafka lag, and outbox depth through the load window.
