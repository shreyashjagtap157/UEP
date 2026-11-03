package com.universalplatform.finance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Finance", description = "Endpoints for managing institutional fee schedules, invoicing, installments, and payment settlements")
@RestController
@RequestMapping("/api/v1/finance")
class FinanceController {
    private final FinanceService s;

    FinanceController(FinanceService s) {
        this.s = s;
    }

    @Operation(
        summary = "List fee structures",
        description = "Retrieves all configured fee structures within the workspace."
    )
    @GetMapping("/fees")
    List<FinanceService.FeeView> fees() {
        return s.listFees();
    }

    @Operation(
        summary = "Create fee structure",
        description = "Creates a new fee configuration. Requires FeeRequest body containing name, amount, 3-letter currency code, status, and expectedVersion."
    )
    @PostMapping("/fees")
    FinanceService.FeeView fee(@Valid @RequestBody FeeRequest r) {
        return s.upsertFee(null, r.name, r.amount, r.currency, r.status, 0);
    }

    @Operation(
        summary = "Update fee structure",
        description = "Updates an existing fee configuration. Requires fee ID path variable and FeeRequest body containing name, amount, currency code, status, and expectedVersion."
    )
    @PutMapping("/fees/{id}")
    FinanceService.FeeView fee(@PathVariable UUID id, @Valid @RequestBody FeeRequest r) {
        return s.upsertFee(id, r.name, r.amount, r.currency, r.status, r.expectedVersion);
    }

    @Operation(
        summary = "List invoices",
        description = "Retrieves a paginated list of invoices. Accepts optional page (default 0) and size (default 25) query parameters."
    )
    @GetMapping("/invoices")
    FinanceService.PageView<FinanceService.InvoiceView> invoices(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return s.listInvoices(page, size);
    }

    @Operation(
        summary = "Get invoice by ID",
        description = "Retrieves invoice details and payment schedule by invoice ID path variable."
    )
    @GetMapping("/invoices/{id}")
    FinanceService.InvoiceView invoice(@PathVariable UUID id) {
        return s.getInvoice(id);
    }

    @Operation(
        summary = "Create invoice",
        description = "Creates a new student/member invoice. Requires CreateInvoiceRequest body containing membershipId, totalAmount, currency, dueOn, and optional installments list."
    )
    @PostMapping("/invoices")
    FinanceService.InvoiceView create(@Valid @RequestBody CreateInvoiceRequest r) {
        return s.createInvoice(r.membershipId, r.totalAmount, r.currency, r.dueOn, r.installments);
    }

    @Operation(
        summary = "Issue invoice",
        description = "Transitions an invoice from draft to issued status. Requires invoice ID path variable and VersionRequest body containing expectedVersion."
    )
    @PostMapping("/invoices/{id}/issue")
    FinanceService.InvoiceView issue(@PathVariable UUID id, @Valid @RequestBody VersionRequest r) {
        return s.issue(id, r.expectedVersion);
    }

    @Operation(
        summary = "Record invoice payment",
        description = "Records a payment settlement against an invoice. Requires invoice ID path variable and PayRequest body containing amount, currency, settlementReference, and confirmSettlement flag."
    )
    @PostMapping("/invoices/{id}/payments")
    FinanceService.PaymentView pay(@PathVariable UUID id, @Valid @RequestBody PayRequest r) {
        return s.pay(id, r.amount, r.currency, r.settlementReference, r.confirmSettlement);
    }

    record FeeRequest(@NotBlank String name, @NotNull @DecimalMin("0.00") BigDecimal amount, @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency, @NotNull FeeStatus status, long expectedVersion) {}
    record CreateInvoiceRequest(@NotNull UUID membershipId, @NotNull @DecimalMin("0.01") BigDecimal totalAmount, @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency, @NotNull LocalDate dueOn, List<FinanceService.InstallmentRequest> installments) {}
    record VersionRequest(long expectedVersion) {}
    record PayRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency, @NotBlank @Size(max = 120) String settlementReference, @NotNull @jakarta.validation.constraints.AssertTrue Boolean confirmSettlement) {}
}
