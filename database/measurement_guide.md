# Analytics Before/After Measurement Guide

This guide captures a repeatable way to measure the effect of the analytics
materialized view optimization on query latency and overall system throughput.

## Goal

Compare two scenarios:

1. Baseline: analytics queries against the raw `messages` table
2. Optimized: analytics queries using materialized views with scheduled refresh

Record both:

- query latency
- write/processing throughput under load

## Recommended Setup

- Start local dependencies with Docker:

```bash
cd deployment
docker-compose up -d
```

- Initialize the database schema, indexes, and materialized views
- Start `consumer-v3`
- Start `server-v2`
- Use the existing load-test configs under `load-tests/configs/`

## Query Latency Measurement

### Baseline

Use the raw-table queries in `database/explain_analytics.sql` and record:

- planning time
- execution time
- rows scanned
- whether PostgreSQL uses sequential scan, index scan, or aggregate nodes

### Optimized

Run the same script after:

- enabling the Task 1 analytics service changes
- ensuring materialized views are created
- refreshing the materialized views with:

```sql
SELECT refresh_analytics_views();
```

Record the same metrics and compare:

- raw table execution time vs materialized view execution time
- relative improvement or regression

## Throughput Measurement

Use one of the existing load-test configs, for example:

- `load-tests/configs/baseline.json`
- `load-tests/configs/stress.json`

For each run, capture:

- messages published per second
- messages persisted per second
- consumer queue depth
- DB writer queue depth
- analytics API latency during load

## Suggested Run Order

1. Start with baseline code path and collect:
   - `EXPLAIN ANALYZE` results
   - load-test throughput metrics
2. Switch to optimized code path and collect the same metrics
3. Keep the dataset size and load-test config the same
4. Compare results side by side

## What to Watch

- If execution time improves for `messages per minute`, `top users`, and
  `top rooms`, the materialized views are helping analytics latency
- If throughput drops sharply after enabling frequent refresh, the refresh
  interval may be too aggressive
- If small datasets show no gain or even slight regressions, repeat the test
  with larger data volume before drawing conclusions

## Practical Notes

- Small datasets can make raw-table queries appear faster because planning and
  sort overhead dominate the measurement
- Test with representative data volume before tuning refresh intervals
- A 60-second refresh interval is a reasonable default for local or moderate
  load; shorter intervals should be justified by observed latency needs
