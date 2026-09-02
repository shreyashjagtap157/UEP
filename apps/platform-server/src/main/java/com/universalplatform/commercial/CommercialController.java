package com.universalplatform.commercial; import jakarta.validation.*; import jakarta.validation.constraints.*; import java.math.BigDecimal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/commercial") class CommercialController { private final CommercialService s; CommercialController(CommercialService s){this.s=s;}
 @GetMapping("/subscription") CommercialService.SubscriptionView subscription(){return s.subscription();}
 @PutMapping("/subscription") CommercialService.SubscriptionView configure(@Valid @RequestBody PlanRequest r){return s.configure(r.planCode,r.recurringAmount,r.currency,r.autoRenew,r.expectedVersion);}
 @PostMapping("/subscription/trial") CommercialService.SubscriptionView trial(@Valid @RequestBody DaysRequest r){return s.trial(r.days);}
 @PostMapping("/subscription/grace") CommercialService.SubscriptionView grace(@Valid @RequestBody DaysRequest r){return s.grace(r.days);}
 @PostMapping("/subscription/suspend") CommercialService.SubscriptionView suspend(){return s.suspend();}
 @GetMapping("/white-label") CommercialService.WhiteLabelView whiteLabel(){return s.readWhiteLabel();}
 @PutMapping("/white-label") CommercialService.WhiteLabelView whiteLabel(@Valid @RequestBody WhiteLabelRequest r){return s.whiteLabel(r.brandName,r.primaryColor,r.logoUrl,r.faviconUrl,r.customDomain,r.enabled,r.expectedVersion);}
 record PlanRequest(@NotBlank String planCode,@NotNull @DecimalMin("0.00") BigDecimal recurringAmount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,boolean autoRenew,long expectedVersion){} record DaysRequest(@Min(1) @Max(365) int days){} record WhiteLabelRequest(@NotBlank String brandName,@Pattern(regexp="^$|^#[0-9A-Fa-f]{6}$") String primaryColor,String logoUrl,String faviconUrl,String customDomain,boolean enabled,long expectedVersion){}
}
