-- Prevent an external settlement reference from being applied to multiple payments
-- within the same tenant/provider. Pending/failed payments with no reference remain allowed.
CREATE UNIQUE INDEX uq_payment_tenant_provider_reference
    ON payment(tenant_id, provider, provider_reference)
    WHERE provider_reference IS NOT NULL;
