# ADR 0011 — Derived Tenant Analytics and Operations Reporting

## Status

Accepted for 0.12.0.0.

## Decision

Build analytics as a tenant-authoritative read module over existing transactional tables. Use bounded SQL aggregation for operational dashboards and small administrative CSV exports. Do not introduce a second mutable data store for derived metrics in this milestone.

## Rationale

This preserves one authoritative record for attendance, assessment results, recording metadata, finance, licensing usage, audit, and notification state. It also makes analytics immediately consistent with transactional changes without requiring an asynchronous warehouse pipeline.

A future warehouse or materialized aggregation layer can be introduced once scale or reporting latency justifies it; its inputs must remain the same authoritative records/events.

## Security

Analytics is permission-gated and tenant-filtered. Finance amounts remain conditional on `FINANCE_VIEW`. Operational statistics are separately gated by `OPERATIONS_VIEW`. Branch-scoped administrative permissions are not used to expose tenant-wide analytics until branch-filtered aggregation is explicitly implemented.
