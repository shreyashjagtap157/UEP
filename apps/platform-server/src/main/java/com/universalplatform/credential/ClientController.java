package com.universalplatform.credential;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credential")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/clients/{id}")
    public ResponseEntity<ClientView> getClient(@PathVariable Long id) {
        ClientView client = clientService.findById(id);
        return ResponseEntity.ok(client);
    }

    @PostMapping("/clients")
    public ResponseEntity<ClientView> createClient(@RequestBody CreateClientInput input) {
        ClientView client = clientService.create(input);
        return ResponseEntity.ok(client);
    }

    @PutMapping("/clients/{id}")
    public ResponseEntity<ClientView> updateClient(@PathVariable Long id, @RequestBody UpdateClientInput input) {
        ClientView client = clientService.update(id, input);
        return ResponseEntity.ok(client);
    }

    @DeleteMapping("/clients/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }