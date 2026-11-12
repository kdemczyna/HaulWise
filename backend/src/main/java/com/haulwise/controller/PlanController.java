package com.haulwise.controller;

import com.haulwise.dto.plan.PlanRequest;
import com.haulwise.dto.plan.PlanResponse;
import com.haulwise.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plans a run and returns stored plans. The request body is validated by {@link PlanService} (not
 * {@code @Valid}) so field rules and id checks are reported in one 400.
 */
@RestController
@Tag(name = "Plans", description = "Optimal and priority-respecting plans of a run, with the cost of priority")
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @Operation(summary = "Plan a run from one depot",
            description = "Returns both variants, the cost of priority, unassigned shipments and warnings. "
                    + "Defaults: returnToDepot true, departureTime tomorrow 06:00. The plan is kept in memory "
                    + "(the 50 most recently used plans) until restart.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // OpenAPI only; the runtime status comes from ResponseEntity
    public ResponseEntity<PlanResponse> create(@RequestBody PlanRequest request) {
        PlanResponse plan = planService.create(request);
        return ResponseEntity.created(Locations.of(plan.id())).body(plan);
    }

    @Operation(summary = "Get a stored plan", description = "404 when the plan is unknown, was evicted or the "
            + "server restarted.")
    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable String id) {
        return planService.get(id);
    }
}
