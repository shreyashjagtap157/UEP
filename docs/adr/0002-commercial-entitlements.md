# ADR 0002: Commercial licensing uses entitlements, not plan-name branches

Status: Accepted

Commercial access is computed from tenant subscription state, feature entitlement,
limits, and usage. Plan names are catalog/packaging concerns and cannot be used as
runtime authorization decisions. This permits negotiated enterprise contracts,
on-premise deployments, trials, add-ons, and future pricing changes without
forking business logic.
