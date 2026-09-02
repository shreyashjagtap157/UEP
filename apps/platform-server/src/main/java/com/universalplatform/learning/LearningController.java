package com.universalplatform.learning;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/learning") class LearningController { private final LearningService s; LearningController(LearningService s){this.s=s;}
@GetMapping("/outcomes") List<LearningService.OutcomeView> outcomes(){return s.outcomes();}
@PostMapping("/outcomes") LearningService.OutcomeView outcome(@Valid @RequestBody OutcomeRequest r){return s.createOutcome(r.code,r.name,r.description);}
@GetMapping("/outcomes/{outcomeId}/competencies") List<LearningService.CompetencyView> competencies(@PathVariable UUID outcomeId){return s.competencies(outcomeId);}
@PostMapping("/competencies") LearningService.CompetencyView competency(@Valid @RequestBody CompetencyRequest r){return s.createCompetency(r.outcomeId,r.code,r.name,r.description);}
@GetMapping("/paths") List<LearningService.LearningPathView> paths(){return s.paths();}
@PostMapping("/paths") LearningService.LearningPathView path(@Valid @RequestBody PathRequest r){return s.createPath(r.code,r.name,r.description);}
@PostMapping("/paths/{pathId}/items") LearningService.PathItemView item(@PathVariable UUID pathId,@Valid @RequestBody PathItemRequest r){return s.addPathItem(pathId,r.sequence,r.itemType,r.itemId,r.title,r.required);}
@PostMapping("/mappings") void mapping(@Valid @RequestBody MappingRequest r){s.mapOutcome(r.targetType,r.targetId,r.outcomeId,r.weight);}
@PostMapping("/progress") void progress(@Valid @RequestBody ProgressRequest r){s.recordProgress(r.pathId,r.membershipId,r.progress);}
@PostMapping("/mastery") void masteryWrite(@Valid @RequestBody MasteryRequest r){s.recordMastery(r.outcomeId,r.competencyId,r.membershipId,r.mastery,r.evidence);}
@GetMapping("/mastery") LearningService.MasteryView mastery(@RequestParam UUID outcomeId,@RequestParam UUID membershipId){return s.mastery(outcomeId,membershipId);}
record OutcomeRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=200) String name,@Size(max=2000) String description){}
record CompetencyRequest(@NotNull UUID outcomeId,@NotBlank @Size(max=80) String code,@NotBlank @Size(max=200) String name,@Size(max=2000) String description){}
record PathRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=200) String name,@Size(max=2000) String description){}
record PathItemRequest(@Min(1) int sequence,@NotBlank @Pattern(regexp="[A-Z_]{2,32}") String itemType,@NotNull UUID itemId,@NotBlank @Size(max=300) String title,boolean required){}
record MappingRequest(@NotBlank @Pattern(regexp="[A-Z_]{2,32}") String targetType,@NotNull UUID targetId,@NotNull UUID outcomeId,@DecimalMin("0.000001") @DecimalMax("1.0") double weight){}
record ProgressRequest(@NotNull UUID pathId,@NotNull UUID membershipId,@DecimalMin("0") @DecimalMax("1") double progress){}
record MasteryRequest(@NotNull UUID outcomeId,@NotNull UUID competencyId,@NotNull UUID membershipId,@DecimalMin("0") @DecimalMax("1") double mastery,@Size(max=4000) String evidence){}
}
