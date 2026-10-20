# QuickWal performance measurement ledger

This is the running record for capacity experiments. Keep configured limits, runtime observations, derived values, and unknowns separate. Every result needs its workload, resource envelope, source, and collection method so it can be explained and repeated.

## Measurement rules

- **Configured** means a value explicitly present in source/configuration or applied by the launch command.
- **Effective** means what the running process or service actually sees, such as JVM processor count or cgroup limits.
- **Observed** means a time-bounded measurement. An idle snapshot is not a capacity result.
- **Derived** means calculated from observations; include the formula and window.
- **Unknown** means not measured yet. Do not fill it with a framework default unless the runtime confirms it.
- Keep the code revision, host/WSL/Docker versions, start command, workload profile, data state, and timestamp with each run.

## Current environment and resource envelope

Captured on 2026-10-19. Values marked idle are snapshots, not load results.

| Quantity | Value | Kind | Evidence and interpretation |
|---|---:|---|---|
| WSL host logical CPUs | 18 | Observed host capacity | `nproc`/host inspection. This is the WSL-visible host ceiling, not the app's allocation. |
| WSL memory / swap | about 7.5 GiB / 2 GiB | Observed host capacity | WSL host inspection. |
| App CPU quota | 200% (2 CPU-equivalents) | Configured at launch | Temporary user systemd unit property `CPUQuota=200%`; not a repository default. |
| App memory ceiling | 2 GiB | Configured at launch | Temporary user systemd unit property `MemoryMax=2G`; not a repository default. |
| JVM processor count | 2 | Effective | Actuator `system.cpu.count`; consistent with the app cgroup CPU quota. |
| JVM max heap | 536,870,910 bytes (about 512 MiB) | Effective | Actuator `jvm.memory.max` filtered to `area=heap`; JVM container-aware sizing under the current launch. |
| JVM heap used | 44,480,480 bytes (about 42.4 MiB) | Observed idle | Actuator `jvm.memory.used`, `area=heap`, at the fresh idle snapshot. |
| JVM live threads | 47 | Observed idle | Actuator `jvm.threads.live`; includes daemon and non-daemon threads. |
| Spring-managed virtual threads | Enabled | Configured, current | `spring.threads.virtual.enabled=true` and `spring.main.keep-alive=true` in `src/main/resources/application.properties`; Java 21. This does not raise the app cgroup CPU limit and does not by itself convert Kafka consumer loops to virtual threads. |
| Process CPU usage | 0.005624 | Observed idle | Actuator `process.cpu.usage`; a point sample, not CPU capacity or a load average. |
| App cgroup throttling / memory events under load | Not measured in the latest run | Unknown | Capture `cpu.stat`, `memory.current`, `memory.peak`, and `memory.events` from the app's actual cgroup during a sustained run. |

The app is currently launched in a temporary systemd unit with the 2 CPU / 2 GiB limits above. Actuator is bound to the current WSL interface address on port 8082 for Windows browser access; the application API is on port 8081. The WSL IP can change after a WSL restart. These runtime launch settings are not in the application source files.

## Application, database, Kafka, and relay inventory

| Area | Current value | Kind | Where it came from / how to modify it |
|---|---|---|---|
| Application runtime | Spring Boot 4.1.1, Java 21 | Configured | `pom.xml`; dependency and runtime versions. |
| HTTP API port | 8081 | Launch-time setting | Current `spring-boot:run` arguments. `server.port` can be set in `src/main/resources/application.properties` or launch arguments. |
| Actuator port / exposure | 8082; `health,metrics,prometheus` | Launch-time setting | Current launch arguments. Management bind address is the WSL interface IP so Windows can reach it. |
| Spring/Tomcat worker-thread limit | Not explicitly configured or measured yet | Unknown | Inspect effective Tomcat metrics/config before claiming a limit. If needed, configure `server.tomcat.threads.max` and `server.tomcat.threads.min-spare` in `application.properties`; measure active/busy threads. |
| Hikari maximum connections | 10 | Effective/observed | Actuator `hikaricp.connections.max`. No explicit pool-size setting was found in the application properties; this is the auto-configured pool value. To change deliberately, set `spring.datasource.hikari.maximum-pool-size` in `application.properties`. |
| Hikari current active / idle / pending | 0 / 10 / 0 | Observed idle | Actuator `hikaricp.connections.active`, `.idle`, and `.pending`; pool was `HikariPool-1`. |
| Hikari minimum idle | 10 in an earlier metric read; recheck on the next snapshot | Observed earlier | Actuator `hikaricp.connections.min`; no explicit property was found. Do not assume this remains the value without rechecking. |
| PostgreSQL Compose CPU / memory limit | 2 CPU / 2 GiB | Configured in Compose | `docker-compose.yaml` resource limit. The user has noted these container settings were not mindfully chosen; record them as actual conditions, not as an intentional sizing recommendation. |
| Kafka Compose CPU / memory limit | 2 CPU / 2 GiB | Configured in Compose | `docker-compose.yaml`, same qualification as PostgreSQL. |
| PostgreSQL `max_connections` | 100 | Effective server setting | Read from PostgreSQL, not inferred from Hikari. |
| PostgreSQL `shared_buffers` | 163,848 kB (about 160 MiB) | Effective server setting | Read from PostgreSQL. |
| PostgreSQL `work_mem` | 4,096 kB (4 MiB) | Effective server setting | Read from PostgreSQL. This can be used by multiple operations per query and concurrent sessions; it is not a per-server hard memory cap. |
| PostgreSQL `effective_cache_size` | 5,242,888 kB | Effective server setting | Read from PostgreSQL; planner estimate, not memory allocated by PostgreSQL. |
| PostgreSQL other queried settings | `maintenance_work_mem=65,536 kB`; `max_parallel_workers=8`; `max_parallel_workers_per_gather=2`; `max_worker_processes=8` | Effective server settings | Read from PostgreSQL. Recheck alongside each environment record. |
| Kafka topic partitions / replication | `wallet-commands`: 8 / RF1; `transfer-events`: 8 / RF1 | Observed broker state, current | Topics were expanded in place from 1 to 8 partitions; replication factor remains 1 on the single broker. Partition increases are effectively irreversible. RF1 offers no broker redundancy. |
| Active consumers | Eight consumers in each app group, assigned across eight partitions per topic | Observed broker state, current | Consumer-group descriptions for `quickwal-wallet-worker` and `quickwal-transfer-worker`; all eight assignments observed during load. Before expansion, each group had one consumer. |
| Listener concurrency | 8 | Configured and verified | `spring.kafka.listener.concurrency=8` in `src/main/resources/application.kafka.properties`; matches current topic partition count. This permits up to eight active consumers per group, not eight CPU cores. |
| Producer delivery | `acks=all`, idempotence enabled | Configured | `src/main/resources/application.kafka.properties`. Record with Kafka results because it affects producer guarantees and latency. |
| Consumer offset / ack behavior | `auto-offset-reset=earliest`, auto commit disabled, record ack mode | Configured | `src/main/resources/application.kafka.properties`. `RECORD` commits after each synchronous listener invocation returns; it does not create ordering or concurrency. |
| Outbox relay poll delay | 100 ms (was 500 ms in initial baseline) | Configured, current | `quickwal.outbox.poll-delay-ms` in `src/main/resources/application.kafka.properties`; changing it affects dispatch cadence and DB polling. |
| Outbox relay batch size | 1,000 rows (was 100 in initial baseline) | Configured, current | `quickwal.outbox.batch-size`; relay selects up to this many rows with `FOR UPDATE SKIP LOCKED`. |
| Outbox send timeout | 10,000 ms per send wait | Configured | `quickwal.outbox.send-timeout-ms`; relay waits on Kafka sends sequentially in the scheduled invocation. |
| Outbox backlog / oldest age / relay duration | Not measured yet | Unknown | Need row count/oldest age and scheduled task timing during load. Actuator scheduled-task activity was idle at the snapshot. |
| Network bandwidth / RTT / retransmits | Not measured | Unknown | Record host-to-app and app-to-DB/Kafka paths separately. A browser reaching Actuator proves connectivity, not throughput. |

Relevant flow documentation is in [`transfer_flow.md`](transfer_flow.md). The HTTP transfer endpoint accepts a transfer transactionally and appends an outbox command; Kafka consumers and further outbox events advance it asynchronously. Therefore **HTTP accepted requests/s**, **terminal successful transfers/s**, and **end-to-end latency** are different quantities.

## Pilot run: 75 transfers

This is retained as a smoke/pilot run, not a capacity claim.

| Field | Value |
|---|---|
| Date | 2026-10-19 |
| Code | Existing `scripts/testing/benchmark_transfers.py`; no application code changes for the run |
| Command | `python3 scripts/testing/benchmark_transfers.py --base-url http://127.0.0.1:8081 --requests 25 --concurrency-levels 1 5 10 --amount-minor 1 --transfer-timeout 120 --poll-interval 0.1` |
| Work | 25 transfers at each concurrency 1, 5, 10; 75 total; 1 QC minor unit per transfer |
| Data effect | The script does not reset the DB. It retained accepted transfer/event rows and moved a total of 75 minor units from the seeded sender to the seeded receiver. |
| Results | All 75 reached `SUCCESSFUL`; zero POST errors. |

| Concurrency | Accepted requests/s | POST p50/p95/p99 ms | Successful transfers/s | Script E2E p50/p95/p99 ms |
|---:|---:|---:|---:|---:|
| 1 | 59.6 | 8.1 / 14.9 / 214.0 | 8.4 | 2651.6 / 2711.9 / 2820.8 |
| 5 | 618.7 | 6.6 / 11.5 / 11.6 | 9.0 | 2637.8 / 2645.5 / 2740.3 |
| 10 | 406.7 | 19.2 / 31.9 / 34.0 | 9.4 | 2536.7 / 2623.3 / 2624.2 |

The script submits the full POST batch before it starts polling transfer statuses. Its E2E timing therefore includes harness waiting/polling delay and is unsuitable as a user-visible transfer latency result. The sample is only 25 requests per operating point, so the non-monotonic accepted rate at concurrency 10 does not establish saturation. Do not put these values on a resume as a capacity claim.

## Controlled run: 10 offered transfers/s

This is the first steady-arrival point. It describes the current two-wallet workload and resource envelope; it is not yet a general capacity limit.

| Field | Value |
|---|---|
| Date | 2026-10-19 |
| Client | [`scripts/testing/benchmark_transfers_steady.py`](../scripts/testing/benchmark_transfers_steady.py) |
| Command | `python3 scripts/testing/benchmark_transfers_steady.py --base-url http://127.0.0.1:8081 --requests 300 --rate 10 --max-concurrency 20 --poll-workers 8 --poll-interval 0.25 --transfer-timeout 120 --drain-timeout 180 --http-timeout 15 --amount-minor 1 --currency QC --source-wallet-id aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa --target-wallet-id bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` |
| Offered load | 300 scheduled arrivals over 30 seconds (10/s); one minor unit per transfer; same seeded source/target pair. |
| Result | 300 POSTs accepted, all 300 terminal `SUCCESSFUL`, zero dropped arrival slots, zero POST errors. |
| Duration | 30.000 s configured arrival window; 32.469 s total through completion drain. |
| Persistence | No reset. Up to 300 minor units moved from sender to receiver; all generated transfer and workflow records remain in the database. |

| Measurement | Result | Definition / caveat |
|---|---:|---|
| Accepted requests per configured load second | 10.00/s | HTTP 202 count divided by 30-second arrival window. All slots were sent and accepted. |
| Successful transfers per configured load second | 10.00/s | All 300 became successful, divided by the 30-second request-generation window. This is a batch completion result, not a continuously sampled completion-rate curve. |
| POST latency p50 / p95 / p99 | 5.4 / 7.9 / 15.0 ms | Client-observed time for POST requests. |
| POST scheduling lag p50 / p95 / p99 | 0.3 / 0.5 / 0.7 ms | Difference between scheduled arrival time and actual POST start; shows the generator kept pace with this offered rate. |
| Observed POST-to-terminal p50 / p95 / p99 | 2553.4 / 2834.4 / 2879.4 ms | Time from POST start until a GET poll observed a terminal status. Includes up to 250 ms poll interval and poll-worker scheduling. |
| Full-batch successful rate including drain | about 9.24/s | 300 divided by 32.469 seconds. This includes the 2.469-second drain after the configured arrival window. |

### Resource and queue observations during the run

The read-only sampler took six snapshots across the latter part of the run and drain. These are short samples, not high-resolution telemetry.

| Quantity | Observed values | Interpretation |
|---|---:|---|
| App cgroup `memory.current` | about 389.6–390.0 MiB | Well below the configured 2 GiB maximum in these samples. |
| App cgroup `memory.peak` | 456,052,736 bytes (about 435 MiB) | Peak since this service cgroup started; it did not rise across the six samples. |
| App cgroup memory events | `high=0`, `max=0`, `oom=0`, `oom_kill=0` | No memory pressure/limit/OOM event recorded by the cgroup. |
| App cgroup CPU counters | `nr_throttled=32` and `throttled_usec=1,106,059` remained unchanged across samples | The service had prior throttling before the sample window; no additional throttled periods were recorded during the sampled interval. No before/after snapshot at the exact load start was captured. |
| Actuator process CPU usage | about 0.0051–0.0122 in samples | Point samples only; they do not represent average CPU for the full 30 seconds. |
| Heap used / live threads | about 49.5–54.7 MiB / 49 threads | Point samples during the latter part of the test and drain. |
| Hikari active / idle / pending | 0 / 10 / 0 in all six samples | No pool wait was visible at sample instants. A short-lived database operation between samples can be missed. |
| Docker PostgreSQL CPU / memory | about 0.06–0.10% / 44–45 MiB of 2 GiB | `docker stats` point samples; existing Compose limit is recorded as observed configuration, not as a designed recommendation. |
| Docker Kafka CPU / memory | about 1.16–1.94% / 576 MiB of 2 GiB | `docker stats` point samples. |
| Kafka consumer-group lag after run | 0 for partition 0 of both topics | `kafka-consumer-groups --describe --all-groups` after completion; current and log-end offsets both 2,838. This is a post-run snapshot, not peak lag during the run. |
| PostgreSQL outbox rows after run | 0 | `COUNT(*)` and oldest `created_at` query after completion; relay had drained the outbox by then. |
| PostgreSQL connections after run | 10 idle, 1 active | `pg_stat_activity` after completion; the active connection was the inspection query itself. |

### What this run supports

Under the temporary app envelope of 2 CPU-equivalents and 2 GiB, with the current database/Kafka setup and one hot wallet pair, the system accepted 10 requests/s for 30 seconds and completed all 300 within the measured drain. The generator did not fall behind, the sampled cgroup showed no new throttling or memory events, post-run Kafka lag was zero, and the outbox was empty after drain. This supports **at least this tested workload point**; it does not identify maximum throughput or prove that CPU, database, Kafka, or relay capacity is the next bottleneck. The roughly 2.55-second median observed workflow time includes the asynchronous debit/credit/saga path plus polling observation time. The current run did not record peak Kafka lag or continuous DB/relay queues.

## Agreed measurement inventory

Collect these for every controlled run; keep values unavailable until actually measured.

1. **Machine and limits:** host/WSL logical CPUs and memory; app, PostgreSQL, and Kafka cgroup CPU quota and period; memory max/current/peak/events; CPU throttling deltas; Docker Desktop and WSL versions.
2. **JVM and Spring:** JVM version and flags; effective processors; heap max/used/committed; non-heap/direct memory; RSS; GC count/time/pause; live/peak threads; Tomcat max/active/busy threads and request queue; scheduler pool and active executions.
3. **Database:** Hikari max/min/active/idle/pending, acquisition and usage latency, timeout count; PostgreSQL active/idle connections, wait events/locks, transaction and query rates/latency, buffer/cache and WAL/checkpoint/I/O indicators; all effective server settings.
4. **Kafka:** broker resource envelope; topic partitions and replication; group member count, assignment and lag; records in/out, produce/fetch latency, errors/retries, batch and acknowledgment settings; consumer processing rate and failures.
5. **Outbox/inbox and workflow:** pending outbox count and oldest age, relay batch size/duration/errors, scheduled delay, Kafka send wait; inbox growth/dedupes; transfer state counts and terminal completion rate.
6. **Network:** interface and path, RTT, bandwidth, retransmits/drops, connection reuse, and whether the load generator shares the app host.
7. **Workload and results:** offered arrival rate, in-flight concurrency, accepted/rejected/error rates, POST p50/p95/p99, terminal success/failure rate, end-to-end p50/p95/p99, timeouts, and backlog growth/drain time.

For queueing interpretation, record a measurement window and calculate `lambda` (arrival rate), `mu` (service/completion rate), and utilization only when a single service center and worker count are clearly defined. Use Little's Law, `L = lambda × W`, only with matching boundary, units, and a stable window. For Kafka work, partition count and consumer assignment constrain parallelism; for database work, connection count is a concurrency ceiling, not a throughput guarantee.

## Controlled rate curve and 20/s verification

The fixed-arrival client was used for all points below. Every run used the same seeded wallet pair and 1 QC minor unit per accepted request. The API accepted all scheduled requests; all accepted transfers reached `SUCCESSFUL`. No database reset was performed. The client reports both load-window acceptance and full-batch completion throughput (`successful transfers / total elapsed through drain`). These are distinct: the first describes the offered cohort, while the second includes the time needed for its final work to complete.

| Offered arrivals | Duration / count | POSTs accepted; dropped | Total elapsed incl. drain | Completed-batch throughput | Drain | POST p50/p95/p99 ms | Observed POST-to-terminal p50/p95/p99 ms |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10/s | 30 s / 300 | 300; 0 | 32.469 s | 9.24/s | 2.469 s | 5.4 / 7.9 / 15.0 | 2,553 / 2,834 / 2,879 |
| 20/s | 30 s / 600 | 600; 0 | 33.511 s | 17.91/s | 3.511 s | 4.8 / 8.3 / 14.5 | 4,023 / 4,073 / 4,530 |
| 22/s | 30 s / 660 | 660; 0 | 35.025 s | 18.84/s | 5.025 s | 5.5 / 21.2 / 37.0 | 5,047 / 6,553 / 6,589 |
| 25/s | 30 s / 750 | 750; 0 | 38.061 s | 19.71/s | 8.061 s | 5.0 / 17.4 / 31.5 | 8,047 / 10,585 / 11,075 |
| 30/s | 30 s / 900 | 900; 0 | 46.547 s | 19.33/s | 16.547 s | 5.8 / 84.7 / 212.4 | 16,471 / 20,479 / 20,623 |
| **20/s verification** | **60 s / 1,200** | **1,200; 0** | **63.504 s** | **18.90/s** | **3.504 s** | **4.7 / 7.2 / 10.0** | **4,022 / 4,028 / 4,035** |

For the 60-second verification, the precise command was:

```sh
python3 scripts/testing/benchmark_transfers_steady.py --base-url http://127.0.0.1:8081 --requests 1200 --rate 20 --max-concurrency 150 --poll-workers 256 --poll-interval 0.5 --transfer-timeout 120 --drain-timeout 180 --http-timeout 15 --amount-minor 1 --currency QC --source-wallet-id aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa --target-wallet-id bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb
```

The run retained all 1,200 transfers and workflow records and moved 1,200 minor units from the sender to the receiver. Database totals after it were 5,525 successful transactions across the database (not a count attributable only to this experiment), and the outbox table had zero rows. Kafka consumer offsets and log-end offsets both stood at 11,058 for each of the two single-partition topics, so post-run lag was zero. The offsets were 8,658 before the run: each topic advanced by 1,200 records, consistent with one record per transfer per topic. PostgreSQL showed 10 idle connections and one active inspection connection.

### Resource evidence around the 60-second verification

The pre-run and post-run cgroup snapshots bracketed the benchmark; they are cumulative counters, not an isolated profiler trace of exactly the 60-second arrival window.

| Quantity | Before | After | Interpretation |
|---|---:|---:|---|
| App CPU `usage_usec` | 148,048,048 | 177,208,872 | Delta 29.16 CPU-seconds between snapshots; about 0.46 CPU-equivalents over the 63.5 seconds from offered load start through drain, with some measurement-command overhead included. |
| `nr_throttled` / `throttled_usec` | 42 / 1,513,280 | 42 / 1,513,280 | No new CPU-throttled periods recorded between snapshots, despite the configured 2 CPU-equivalent quota. |
| App `memory.current` | 393,007,104 B (~374.6 MiB) | 393,633,792 B (~375.3 MiB) | Current usage rose by about 0.6 MiB. |
| App `memory.peak` | 456,052,736 B (~435 MiB) | unchanged | Peak since the service cgroup started, not peak for this run alone. |
| App `memory.events` | all zero | all zero | No cgroup high/max/OOM events recorded. |
| Actuator point sample after run | — | process CPU 0.0173; heap used ~40.0 MB; 49 live threads | Point values only, not run averages or JVM memory peak. |
| Hikari point sample after run | — | 0 active / 10 idle / 0 pending | No pool wait at that snapshot; this cannot rule out short waits during the load. |
| Kafka lag / outbox after run | before offsets 8,658 / 0 outbox rows | lag 0 / 0 outbox rows | Queues had drained by the post-run inspection; peak in-run backlog was not sampled. |
| Kafka / PostgreSQL container snapshot | — | 678.1 MiB / 2 GiB; 58.3 MiB / 2 GiB | `docker stats --no-stream`; point snapshot after the run. Compose limits are environmental conditions, not validated sizing targets. |

### Fine-grained sweep around the throughput knee

To refine the 20–24/s area, additional 60-second runs used the same script settings, 150 maximum in-flight POSTs, 256 poll workers, 500 ms status poll interval, same wallet pair, and 1 QC minor unit per transfer. Each row is a separate retained workload cohort; all requests were accepted and all reached `SUCCESSFUL`.

| Offered rate | Requests | Total elapsed | Full-batch throughput | Drain after arrivals | POST p95/p99 | Observed POST-to-terminal p95/p99 |
|---:|---:|---:|---:|---:|---:|---:|
| 18/s | 1,080 | 63.331 s | 17.05/s | 3.331 s | 13.5 / 34.0 ms | 4.061 / 4.546 s |
| 20/s | 1,200 | 63.504 s | 18.90/s | 3.504 s | 7.2 / 10.0 ms | 4.028 / 4.035 s |
| 21/s | 1,260 | 64.001 s | 19.69/s | 4.001 s | 12.8 / 28.5 ms | 5.068 / 5.551 s |
| 22/s | 1,320 | 63.601 s | 20.75/s | 3.601 s | 6.7 / 12.1 ms | 4.530 / 5.026 s |
| 23/s | 1,380 | 66.429 s | 20.77/s | 6.429 s | 10.7 / 17.8 ms | 8.549 / 9.054 s |
| 24/s | 1,440 | 68.662 s | **20.97/s** | 8.662 s | 17.1 / 42.3 ms | 12.100 / 12.170 s |

The full-batch throughput calculation is `successful terminal transfers / (load-window duration + drain)`. The POST latency is client-observed HTTP request duration. POST-to-terminal is client-observed from POST start until a polling GET first saw a terminal status; its p95/p99 includes the 500 ms poll cadence and client scheduling. It is an end-to-end workflow observation, not just HTTP handler latency.

The highest measured full-batch rate in this sweep was 20.97/s while offering 24/s, but the run needed 8.66 seconds to drain and had observed terminal p95/p99 of 12.10/12.17 seconds. At 22/s the full-batch result was 20.75/s with 3.60 seconds drain and 4.53/5.03 seconds p95/p99; increasing the offer to 23/s added almost no measured throughput and materially raised latency and drain. This places the practical queueing knee between 22 and 23 offered requests/s for this profile. Run-to-run p95 variation exists, so a latency SLO should be set before labeling 22/s an approved operating point.

After the sweep, cgroup memory was 394,027,008 bytes (~375.8 MiB), the service-lifetime peak remained 456,052,736 bytes (~435 MiB), and all memory event counters were zero. Cumulative CPU throttling counters were `nr_throttled=53`, `throttled_usec=2,073,027`; there was no sweep-start snapshot, so these totals cannot be attributed specifically to the new runs. Actuator showed 49 live threads, zero active/pending Hikari connections, and process CPU point sample 0.0496. Kafka lag was zero on both groups, outbox rows were zero, and PostgreSQL reported 12,005 successful transactions across the complete database (not only this sweep). These are post-run snapshots, not peak-in-run measurements.

### Capacity conclusion for this exact workload

**The highest measured completed-batch throughput is about 21 transfers/s; the practical queueing knee is around 22 offered requests/s.** At 22/s the 60-second run accepted and completed all 1,320 transfers, drained in 3.60 seconds, and observed terminal p95/p99 of 4.53/5.03 seconds. At 23/s, throughput was effectively flat (20.77/s versus 20.75/s) while drain rose to 6.43 seconds and p95/p99 to 8.55/9.05 seconds. At 24/s the batch result reached 20.97/s, but drain was 8.66 seconds and p95/p99 12.10/12.17 seconds. Higher offered rates therefore produced more queueing and latency for little additional completion throughput. This is an observed knee, not a claim that the service crashes above it.

For a concise capacity claim, report: **“On a 2-vCPU-equivalent / 2-GiB-capped Spring Boot instance, with the current one-partition Kafka topics, current PostgreSQL/Kafka Compose environment, and a single hot source/target wallet pair, QuickWal completed about 21 transfers/s at its measured throughput knee. A 22/s, 60-second offered load completed all 1,320 transfers with 3.60 seconds of drain and client-observed POST-to-terminal p95/p99 of 4.53/5.03 seconds; at 23/s the throughput stayed flat while p95 rose to 8.55 seconds.”** Always quote workload and latency measurement method. Full-batch throughput includes drain and should not be mislabeled as the exact steady-state service rate.

This is not yet the application's universal or production capacity. A single hot pair can serialize on wallet-level database locks, only one consumer can work each one-partition topic, and the local load generator shares the machine. The observations show no post-run Hikari wait, memory pressure, or persistent Kafka/outbox backlog, but do not identify which internal stage sets the ~21/s completion knee: queue depth, per-stage service times, database lock waits, and in-run Kafka/outbox backlog were not sampled continuously. Before changing pool sizes, partitions, thread counts, or relay cadence, add time-series measurements for DB waits/locks and per-stage Kafka/outbox throughput, then repeat with a realistic wallet distribution.

## Next experiment status

The initial hot-wallet result above applies to the original relay configuration and is distinct from the later randomized-wallet tuning results below. The next phase is bottleneck attribution and generalization, not presenting either result as a universal limit. Preserve the run command, cap settings, wallet profile, data effects, and snapshots above when comparing future runs. The fixed-batch script remains a pilot only; its poll-after-submit order distorts end-to-end timing.

## Random-wallet rate sweep after relay tuning (2026-10-20)

Relay changes were made one setting at a time from the initial baseline: batch size 100→500 with delay held at 500 ms, delay 500→100 ms with batch held at 500, then batch 500→1,000 with delay held at 100 ms. Current settings are `quickwal.outbox.batch-size=1000`, `quickwal.outbox.poll-delay-ms=100` in `src/main/resources/application.kafka.properties`. Kafka topics remained at one partition each; listener concurrency and Hikari settings were not changed. The application was capped at 2 CPU-equivalents and 2 GiB by its user systemd cgroup. Compose database and Kafka limits are the local environment as found, not deliberate sizing recommendations.

Each run used the seeded 10,000-wallet pool, deterministic selection seed 20261020, distinct random source/target wallets, 1 QC minor unit per transfer, a 60-second arrival window, 150 maximum concurrent POSTs, 256 status-poll workers, 500 ms polling, and retained database data. The command template was:

```sh
python3 scripts/testing/benchmark_transfers_steady.py --base-url http://127.0.0.1:8081 --requests <rate-times-60> --rate <rate> --max-concurrency 150 --poll-workers 256 --poll-interval 0.5 --transfer-timeout 120 --drain-timeout 180 --http-timeout 15 --amount-minor 1 --currency QC --wallet-pool-size 10000 --wallet-selection-seed 20261020
```

| Offered rate | Transfers | Accepted / dropped / successful | Total elapsed / drain | Completed-batch throughput | POST p95/p99 | Observed POST-to-terminal p95/p99 | Max sampled outbox / age |
|---:|---:|---|---:|---:|---:|---:|---:|
| 24/s | 1,440 | 1,440 / 0 / 1,440 | 61.421 / 1.421 s | 23.44/s | 10.6 / 13.1 ms | 2.035 / 2.046 s | Combined 24–28 sweep max 251 / 2.241 s |
| 26/s | 1,560 | 1,560 / 0 / 1,560 | 61.900 / 1.900 s | 25.20/s | 11.5 / 14.8 ms | 3.050 / 3.062 s | Combined 24–28 sweep max 251 / 2.241 s |
| 28/s | 1,680 | 1,680 / 0 / 1,680 | 63.504 / 3.504 s | 26.45/s | 13.9 / 31.1 ms | 4.622 / 5.113 s | Combined 24–28 sweep max 251 / 2.241 s |
| 30/s, batch 500 | 1,800 | 1,800 / 0 / 1,800 | 67.582 / 7.582 s | 26.63/s | 194.7 / 330.7 ms | 10.721 / 11.095 s | 589 / 5.576 s |
| 30/s, batch 1,000 | 1,800 | 1,800 / 0 / 1,800 | 63.030 / 3.030 s | **28.56/s** | 10.8 / 20.2 ms | 4.045 / 4.057 s | 219 / 1.767 s |
| 31/s, batch 1,000 | 1,860 | 1,860 / 0 / 1,860 | 78.494 / 18.494 s | 23.70/s | 427.8 / 700.9 ms | 23.592 / 23.915 s | 1,233 / 12.319 s |
| 32/s, batch 1,000 | 1,920 | 1,920 / 0 / 1,920 | 69.399 / 9.399 s | 27.67/s | 150.7 / 252.9 ms | 12.575 / 12.972 s | 667 / 6.533 s |

Every run accepted all offered POSTs, dropped no arrival slots, and every transfer reached `SUCCESSFUL`. For the 24/26/28/s sweep, a combined 225-snapshot PostgreSQL sample saw zero lock-waiting sessions, at most 4 active sessions, maximum outbox 251 rows / 2.241 s age, and zero deadlocks. The 30/s batch-1,000 run's 75 samples saw zero sampled lock waiters, at most 3 active sessions, max outbox 219 / 1.767 s age, and zero deadlocks. The 31/s and 32/s runs each had 75 samples, no sampled lock waiters/deadlocks, and maxima of 1,233 / 12.319 s and 667 / 6.533 s respectively; active sessions peaked at 7 and 3. One-second snapshots can miss short waits and queue peaks between samples.

After the sweep the outbox was empty, Actuator Hikari active connections were zero, and the combined heap/non-heap JVM memory-used metric was about 160.3 MB. App cgroup cumulative counters since service start were CPU usage 277,708,432 µs, 287 throttled periods, and 28,054,507 throttled µs; memory current 424,448,000 B (~404.7 MiB), lifetime peak 426,565,632 B (~406.6 MiB), and `memory.events` all zero. Cumulative CPU values lack matching per-run start counters and cannot be attributed to a particular benchmark. No memory pressure event was recorded.

**Capacity interpretation for this tuned random-wallet profile:** the highest measured completed-batch result was 28.56 successful transfers/s at 30 offered/s, with 3.03 s drain and client-observed workflow p95/p99 of 4.045/4.057 s. This is one 60-second run, not proof of a hard maximum. At 31/s and 32/s, finite cohorts eventually succeeded but queue depth, drain, and tail latency grew sharply. The non-monotonic 31/32 results signal saturation and run variability; do not turn them into one precise capacity figure. Treat 30 offered requests/s as a candidate operating point for this exact local setup. Repeat 28–30/s at least three times and check queue stability during the arrival window before using a resume-ready capacity claim. HTTP 202 acceptance is not completed-transfer throughput.

The detailed hypotheses, code/config changes, results, and next experiments are in [`performance_optimization_journal.md`](performance_optimization_journal.md). All benchmark scripts, fixture SQL, and the PostgreSQL sampler are persisted under `scripts/testing/`.

## Kafka partition and virtual-thread experiment (2026-10-20)

The two existing topics were expanded in place from 1 to 8 partitions, preserving existing records. Before expansion, both consumer groups were caught up and PostgreSQL outbox count was zero. Replication factor remains 1 on the single Kafka broker. `spring.kafka.listener.concurrency=8` was added in `application.kafka.properties`; group descriptions confirmed eight consumers assigned across the eight partitions for both `quickwal-wallet-worker` and `quickwal-transfer-worker`.

Before expansion, wallet commands used transfer ID as Kafka record key, which could place commands for one wallet on different partitions. `OutboxRelayJob` now derives the command key from its `sourceWalletId` or `targetWalletId` JSON field. Wallet result events already use wallet ID as key. The outbox aggregate ID remains the transfer ID for command records. This keeps records for the same wallet on one partition and preserves their order within that topic partition. The source compiled successfully with Java 21; the formal test suite was not run.

Virtual threads were enabled with `spring.threads.virtual.enabled=true`; `spring.main.keep-alive=true` was also set because virtual scheduler threads can be daemon threads. This is a Spring Boot-managed virtual-thread setting; Kafka listener container concurrency remains independently set to 8. Both changes were applied together, so this experiment cannot isolate the contribution of virtual threads from that of Kafka partitions/concurrency/keying. The 200% CPU / 2 GiB app cgroup was unchanged.

The workload used the 10,000-wallet fixture, random distinct pairs, seed 20261020, 1 QC minor unit, 150 max in-flight POSTs, 256 poll workers, 500 ms polling, and 60 seconds of offered arrivals. Each successful transfer remains in the database. The exact commands used the persisted runner:

```sh
python3 scripts/testing/benchmark_transfers_steady.py --base-url http://127.0.0.1:8081 --requests 1800 --rate 30 --max-concurrency 150 --poll-workers 256 --poll-interval 0.5 --transfer-timeout 120 --drain-timeout 180 --http-timeout 15 --amount-minor 1 --currency QC --wallet-pool-size 10000 --wallet-selection-seed 20261020
python3 scripts/testing/benchmark_transfers_steady.py --base-url http://127.0.0.1:8081 --requests 1920 --rate 32 --max-concurrency 150 --poll-workers 256 --poll-interval 0.5 --transfer-timeout 120 --drain-timeout 180 --http-timeout 15 --amount-minor 1 --currency QC --wallet-pool-size 10000 --wallet-selection-seed 20261020
```

| Offered rate | Partitions / listener concurrency | Accepted / dropped / successful | Completed-batch throughput | Drain | POST p95/p99 | Observed POST-to-terminal p95/p99 | Sampled outbox maximum / age |
|---:|---|---|---:|---:|---:|---:|---:|
| 30/s | 1 / prior default | 1,800 / 0 / 1,800 | 28.56/s | 3.030 s | 10.8 / 20.2 ms | 4.045 / 4.057 s | 219 / 1.767 s |
| 30/s | 8 / 8 | 1,800 / 0 / 1,800 | 28.19/s | 3.843 s | 20.9 / 44.8 ms | 5.091 / 5.155 s | 284 / 2.506 s |
| 32/s | 1 / prior default | 1,920 / 0 / 1,920 | 27.67/s | 7.582 s | 194.7 / 330.7 ms | 10.721 / 11.095 s | 589 / 5.576 s |
| 32/s | 8 / 8 | 1,920 / 0 / 1,920 | 27.67/s | 9.392 s | 49.5 / 98.8 ms | 12.213 / 12.474 s | 610 / 5.290 s |

Each row is one 60-second run, so this is a directional comparison rather than a repeatable causal result. The 8-partition setup showed no demonstrated throughput gain at either rate. At 30/s, the 8-partition run's observed end-to-end p95/p99 were higher than the prior one-partition run. At 32/s, POST p95/p99 were lower but end-to-end p95/p99 and drain were slightly higher. Queueing was visible at 32/s in both runs.

Kafka snapshots during the 8-partition runs showed all eight partitions active. At 30/s, both groups had zero sampled lag. At 32/s, transfer-event lag was zero and wallet-command lag was between 0 and 3 records across partitions. The PostgreSQL sampler took 75 one-second snapshots per run; no lock waiters or deadlocks were sampled, active sessions peaked at 4 (30/s) and 6 (32/s), and outbox maxima/oldest ages were 284/2.506 s and 610/5.290 s. After both runs, outbox count and Kafka lag were zero.

Post-run cgroup counters were cumulative for the service cgroup: CPU usage 155,071,428 µs, `nr_throttled=80`, `throttled_usec=3,669,301`; `memory.current=472,596,480` B, `memory.peak=474,476,544` B, and all memory event counters zero. There was no matching pre-run cgroup counter snapshot, so throttling cannot be attributed to these test runs. The service remained active at `CPUQuotaPerSecUSec=2s` and `MemoryMax=2147483648`.

`spring.kafka.listener.ack-mode=record` was retained, with `enable-auto-commit=false`. In this synchronous record-listener setup, Spring commits an offset after each listener call returns; `BATCH` would commit after all records from a poll. Record mode is compatible with 8-way consumption, but may incur more frequent commit overhead. It does not create partition-level ordering; wallet-based record keys and Kafka partition assignment do that. The Spring Kafka offset semantics are described in the [official listener-container documentation](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html).

**Decision:** keep the topics at 8 partitions and matching concurrency 8 for the next repeat, while keeping the CPU cap fixed. Isolate virtual-thread impact by comparing virtual threads on/off with 8 partitions held constant, and repeat 28–32/s points before claiming a capacity increase. The one-broker replication factor remains 1 and is not a production availability configuration.
