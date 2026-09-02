package com.universalplatform.credential;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/credentials") class CredentialController { private final CredentialService s; CredentialController(CredentialService s){this.s=s;}
@GetMapping List<CredentialService.CredentialView> credentials(@RequestParam(required=false) UUID membershipId){return s.credentials(membershipId);}
@GetMapping("/templates") List<CredentialService.TemplateView> templates(){return s.templates();}
@PostMapping("/templates") CredentialService.TemplateView template(@Valid @RequestBody TemplateRequest r){return s.createTemplate(r.code,r.name,r.description,r.credentialType);}
@PostMapping CredentialService.CredentialView issue(@Valid @RequestBody IssueRequest r){return s.issue(r.templateId,r.membershipId,r.reason,r.sourceId);}
@PostMapping("/{id}/revoke") void revoke(@PathVariable UUID id,@Valid @RequestBody RevokeRequest r){s.revoke(id,r.reason,r.expectedVersion);}
@GetMapping("/verify/{code}") CredentialService.PublicVerification verify(@PathVariable String code){return s.publicVerify(code);}
record TemplateRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=200) String name,@Size(max=2000) String description,@NotBlank @Pattern(regexp="[A-Z_]{2,32}") String credentialType){}
record IssueRequest(@NotNull UUID templateId,@NotNull UUID membershipId,@NotBlank @Size(max=500) String reason,UUID sourceId){}
record RevokeRequest(@NotBlank @Size(max=500) String reason,long expectedVersion){}
}
