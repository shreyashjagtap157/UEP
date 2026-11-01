package com.universalplatform.tenancy;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private LicenseExpiryChecker licenseExpiryChecker;

    @Autowired
    private AuthorizationService authorization;

    @GetMapping
    public List<Client> getAllClients() {
        authorization.require(PermissionKey.COMMERCIAL_VIEW);
        return clientRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> getClientById(@PathVariable Long id) {
        authorization.require(PermissionKey.COMMERCIAL_VIEW);
        return clientRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Client> createClient(@Valid @RequestBody Client client) {
        authorization.require(PermissionKey.COMMERCIAL_MANAGE);
        // If licensingPeriodEnd is not set, treat as perpetual (null)
        Client saved = clientRepository.save(client);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Client> updateClient(@PathVariable Long id, @Valid @RequestBody Client clientDetails) {
        authorization.require(PermissionKey.COMMERCIAL_MANAGE);
        return clientRepository.findById(id)
                .map(client -> {
                    client.setName(clientDetails.getName());
                    client.setBusinessName(clientDetails.getBusinessName());
                    client.setBusinessType(clientDetails.getBusinessType());
                    client.setLicensingPeriodStart(clientDetails.getLicensingPeriodStart());
                    client.setLicensingPeriodEnd(clientDetails.getLicensingPeriodEnd());
                    client.setDeploymentType(clientDetails.getDeploymentType());
                    client.setLogoUrl(clientDetails.getLogoUrl());
                    Client updated = clientRepository.save(client);
                    // Trigger expiry check after update
                    licenseExpiryChecker.checkExpiry();
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        authorization.require(PermissionKey.COMMERCIAL_MANAGE);
        return clientRepository.findById(id)
                .map(client -> {
                    clientRepository.delete(client);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/license-expiry")
    public ResponseEntity<String> getLicenseExpiryStatus(@PathVariable Long id) {
        authorization.require(PermissionKey.COMMERCIAL_VIEW);
        return clientRepository.findById(id)
                .map(client -> {
                    if (client.getLicensingPeriodEnd() == null) {
                        return ResponseEntity.ok("Perpetual license");
                    }
                    long daysLeft = java.time.Period.between(java.time.LocalDate.now(), client.getLicensingPeriodEnd()).getDays()
                            + java.time.Period.between(java.time.LocalDate.now(), client.getLicensingPeriodEnd()).getMonths() * 30
                            + java.time.Period.between(java.time.LocalDate.now(), client.getLicensingPeriodEnd()).getYears() * 365;
                    if (daysLeft <= 0) {
                        return ResponseEntity.ok("Expired");
                    }
                    return ResponseEntity.ok("Expires in " + daysLeft + " days");
                })
                .orElse(ResponseEntity.notFound().build());
    }
}