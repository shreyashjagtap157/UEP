package com.universalplatform.questionbank;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Question Bank", description = "Endpoints for managing question bank items and item versions")
@RestController
@RequestMapping("/api/v1/questions")
class QuestionBankController {
    private final QuestionBankService service;

    QuestionBankController(QuestionBankService service) {
        this.service = service;
    }

    @Operation(
            summary = "List questions",
            description = "Retrieves a paginated list of questions. Accepts page and size query parameters."
    )
    @GetMapping
    QuestionBankService.PageResult<QuestionBankService.QuestionView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @Operation(
            summary = "Create question",
            description = "Creates a new question with initial version. Requires title, type, difficulty, language, payloadJson, positiveMarks, and negativeMarks in the request body."
    )
    @PostMapping
    QuestionBankService.QuestionView create(@Valid @RequestBody QuestionRequest r) {
        return service.create(r.title(), r.type(), r.difficulty(), r.language(), r.payloadJson(), r.positiveMarks(), r.negativeMarks());
    }

    @Operation(
            summary = "Create question version",
            description = "Adds a new version to an existing question identified by ID. Requires question fields and expectedQuestionVersion in the request body."
    )
    @PostMapping("/{id}/versions")
    QuestionBankService.QuestionView version(@PathVariable UUID id, @Valid @RequestBody QuestionVersionRequest r) {
        return service.addVersion(id, r.title(), r.type(), r.difficulty(), r.language(), r.payloadJson(), r.positiveMarks(), r.negativeMarks(), r.expectedQuestionVersion());
    }

    @Operation(
            summary = "List question versions",
            description = "Retrieves all versions for a question identified by the path variable ID."
    )
    @GetMapping("/{id}/versions")
    List<QuestionBankService.QuestionVersionView> versions(@PathVariable UUID id) {
        return service.versions(id);
    }

    record QuestionRequest(
            @NotBlank String title,
            @NotNull QuestionType type,
            String difficulty,
            String language,
            @NotBlank String payloadJson,
            @Positive int positiveMarks,
            int negativeMarks
    ) {}

    record QuestionVersionRequest(
            String title,
            @NotNull QuestionType type,
            String difficulty,
            String language,
            @NotBlank String payloadJson,
            @Positive int positiveMarks,
            int negativeMarks,
            long expectedQuestionVersion
    ) {}
}
