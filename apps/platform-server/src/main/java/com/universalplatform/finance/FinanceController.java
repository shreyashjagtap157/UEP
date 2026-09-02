package com.universalplatform.finance;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/finance") class FinanceController { private final FinanceService s; FinanceController(FinanceService s){this.s=s;}
 @GetMapping("/fees") List<FinanceService.FeeView> fees(){return s.listFees();}
 @PostMapping("/fees") FinanceService.FeeView fee(@Valid @RequestBody FeeRequest r){return s.upsertFee(null,r.name,r.amount,r.currency,r.status,0);}
 @PutMapping("/fees/{id}") FinanceService.FeeView fee(@PathVariable UUID id,@Valid @RequestBody FeeRequest r){return s.upsertFee(id,r.name,r.amount,r.currency,r.status,r.expectedVersion);}
 @GetMapping("/invoices") FinanceService.PageView<FinanceService.InvoiceView> invoices(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return s.listInvoices(page,size);}
 @GetMapping("/invoices/{id}") FinanceService.InvoiceView invoice(@PathVariable UUID id){return s.getInvoice(id);}
 @PostMapping("/invoices") FinanceService.InvoiceView create(@Valid @RequestBody CreateInvoiceRequest r){return s.createInvoice(r.membershipId,r.totalAmount,r.currency,r.dueOn,r.installments);}
 @PostMapping("/invoices/{id}/issue") FinanceService.InvoiceView issue(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return s.issue(id,r.expectedVersion);}
 @PostMapping("/invoices/{id}/payments") FinanceService.PaymentView pay(@PathVariable UUID id,@Valid @RequestBody PayRequest r){return s.pay(id,r.amount,r.currency);}
 record FeeRequest(@NotBlank String name,@NotNull @DecimalMin("0.00") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,@NotNull FeeStatus status,long expectedVersion){}
 record CreateInvoiceRequest(@NotNull UUID membershipId,@NotNull @DecimalMin("0.01") BigDecimal totalAmount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,@NotNull LocalDate dueOn,List<FinanceService.InstallmentRequest> installments){}
 record VersionRequest(long expectedVersion){} record PayRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency){}
}
