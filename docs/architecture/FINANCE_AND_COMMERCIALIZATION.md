# Finance and Commercialization

Version target: 0.11.0.0

The 0.11 subsystem adds institution finance and commercial controls without coupling tenant records to a particular payment vendor. Monetary values use decimal database precision and ISO-4217 alphabetic currency codes.

## Finance

Institution fees are reusable tenant-scoped fee definitions. Invoices are durable tenant/member records with explicit `DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `VOID`, and `OVERDUE` states. Installments must sum exactly to the invoice total. Captured payments update the invoice transactionally and create an immutable receipt record.

`PaymentProvider` is the server-side payment boundary. The reference `MANUAL` provider records an institution-managed payment flow; browser clients never receive provider secrets or execute capture/refund operations directly. A production provider can be added behind the same contract.

## Commercial lifecycle

The existing tenant subscription is extended with plan code, recurring amount, currency, auto-renewal, and trial end. Administrators can start bounded trials, enter bounded grace periods, suspend subscriptions, and update plans under optimistic license revision control. Existing entitlement decisions remain authoritative for feature access; payment processing requires the `PAYMENTS` entitlement.

## Metering and quotas

`QuotaService` consumes usage against the existing tenant entitlement limits and daily usage periods. The usage row is pessimistically locked when present, and the database uniqueness constraint protects the tenant/limit/period identity. Quota decisions return the consumed value and hard limit without exposing billing-provider details.

## White-labeling

Tenant-scoped white-label settings include brand name, validated six-digit primary color, logo/favicon URLs, optional custom domain, enablement, and optimistic versioning. The settings are a control-plane contract; domain ownership verification and TLS automation remain deployment concerns and must not be implied by merely storing a custom domain.

## Security and licensing

Finance and commercialization actions use explicit `FINANCE_VIEW`, `FINANCE_MANAGE`, `PAYMENTS_MANAGE`, `COMMERCIAL_VIEW`, and `COMMERCIAL_MANAGE` permissions. Tenant foreign keys are retained on invoice, installment, payment, receipt, and white-label records to prevent cross-client linkage.
