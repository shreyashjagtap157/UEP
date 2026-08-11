# Tenancy and Security Foundation

Tenant-owned requests derive the tenant from a trusted authentication claim
(`tenant_id` in the reference OIDC profile). The backend does not accept an
arbitrary browser-selected tenant header as authority.

The request tenant context is established at the security boundary and cleared
at request completion. Repositories for tenant-owned models require tenant-aware
queries; later milestones additionally use PostgreSQL row-level defensive controls
where they provide useful defense in depth.

Cross-tenant access is a release-blocking invariant.
