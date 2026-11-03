package com.universalplatform.integration;

import com.universalplatform.questionbank.QuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Interoperability", description = "Endpoints for OneRoster CSV import/export, IMS QTI question item exchange, and federation metadata")
@RestController
@RequestMapping("/api/v1/integrations")
class InteroperabilityController {
    private final OneRosterService roster;
    private final QtiService qti;
    private final FederationService federation;

    InteroperabilityController(OneRosterService r, QtiService q, FederationService f) {
        roster = r;
        qti = q;
        federation = f;
    }

    @Operation(
            summary = "Export OneRoster CSV file",
            description = "Exports a specific OneRoster CSV file (e.g., orgs.csv, users.csv, courses.csv, classes.csv, enrollments.csv) identified by the file name path variable."
    )
    @GetMapping(value = "/oneroster/export/{file}", produces = "text/csv")
    ResponseEntity<byte[]> exportOneRoster(@PathVariable String file) {
        String body = roster.exportCsv().get(file);
        if (body == null) throw new IllegalArgumentException("Unsupported OneRoster file");
        return ResponseEntity.ok().contentType(MediaType.valueOf("text/csv")).body(body.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(
            summary = "Export question item as QTI XML",
            description = "Exports an assessment question version as IMS Question and Test Interoperability (QTI) XML identified by questionVersionId."
    )
    @GetMapping(value = "/qti/items/{questionVersionId}", produces = "application/xml")
    ResponseEntity<String> exportQti(@PathVariable UUID questionVersionId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(qti.exportItem(questionVersionId));
    }

    @Operation(
            summary = "Import question item from QTI XML",
            description = "Imports a question item formatted in IMS QTI XML passed directly in the request body."
    )
    @PostMapping(value = "/qti/items", consumes = "application/xml", produces = "application/json")
    QuestionBankService.QuestionView importQti(@RequestBody String xml) {
        return qti.importItem(xml);
    }

    @Operation(
            summary = "Validate OneRoster CSV data",
            description = "Validates OneRoster CSV files including orgs, users, courses, classes, and enrollments provided in the request body."
    )
    @PostMapping("/oneroster/validate")
    OneRosterAdapter.ImportResult validateOneRoster(@Valid @RequestBody OneRosterValidationRequest r) {
        return roster.validateCsv(Map.of("orgs.csv", r.orgsCsv(), "users.csv", r.usersCsv(), "courses.csv", r.coursesCsv(), "classes.csv", r.classesCsv(), "enrollments.csv", r.enrollmentsCsv()));
    }

    @Operation(
            summary = "Get federation metadata configuration",
            description = "Retrieves OpenID Connect and federation configuration metadata."
    )
    @GetMapping("/federation/configuration")
    Map<String, Object> federationMetadata() {
        return Map.of("protocol", "OpenID Connect", "supportsDiscovery", true, "openIdFoundation", "OpenID Connect / OpenID Federation");
    }

    record OneRosterValidationRequest(
            @NotBlank String orgsCsv,
            @NotBlank String usersCsv,
            @NotBlank String coursesCsv,
            @NotBlank String classesCsv,
            @NotBlank String enrollmentsCsv
    ) {}
}
