package com.universalplatform.questionbank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
class QuestionBankController {
    private final QuestionBankService service;
    QuestionBankController(QuestionBankService service) { this.service = service; }

    @GetMapping QuestionBankService.PageResult<QuestionBankService.QuestionView> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) { return service.list(page, size); }
    @PostMapping QuestionBankService.QuestionView create(@Valid @RequestBody QuestionRequest r) { return service.create(r.title(), r.type(), r.difficulty(), r.language(), r.payloadJson(), r.positiveMarks(), r.negativeMarks()); }
    @PostMapping("/{id}/versions") QuestionBankService.QuestionView version(@PathVariable UUID id, @Valid @RequestBody QuestionVersionRequest r) { return service.addVersion(id, r.title(), r.type(), r.difficulty(), r.language(), r.payloadJson(), r.positiveMarks(), r.negativeMarks(), r.expectedQuestionVersion()); }
    @GetMapping("/{id}/versions") List<QuestionBankService.QuestionVersionView> versions(@PathVariable UUID id) { return service.versions(id); }

    record QuestionRequest(@NotBlank String title, @NotNull QuestionType type, String difficulty, String language, @NotBlank String payloadJson, @Positive int positiveMarks, int negativeMarks) {}
    record QuestionVersionRequest(String title, @NotNull QuestionType type, String difficulty, String language, @NotBlank String payloadJson, @Positive int positiveMarks, int negativeMarks, long expectedQuestionVersion) {}
}
