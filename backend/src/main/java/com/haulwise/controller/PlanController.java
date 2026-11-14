package com.haulwise.controller;

import com.haulwise.dto.plan.DispatchRequest;
import com.haulwise.dto.plan.DispatchResponse;
import com.haulwise.dto.plan.PlanRequest;
import com.haulwise.dto.plan.PlanResponse;
import com.haulwise.dto.plan.PlanSummaryResponse;
import com.haulwise.service.PlanDispatchService;
import com.haulwise.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * Plans a run, returns stored plans and sends them (T-33). The plan request body is validated by
 * {@link PlanService} (not {@code @Valid}) so field rules and id checks are reported in one 400.
 */
@RestController
@Tag(name = "Plans", description = "Optimal and priority-respecting plans of a run, with the cost of priority")
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final PlanDispatchService dispatchService;

    public PlanController(PlanService planService, PlanDispatchService dispatchService) {
        this.planService = planService;
        this.dispatchService = dispatchService;
    }

    @Operation(summary = "List recent plans",
            description = "Summaries of every plan the in-memory store currently holds (at most 50, lost on "
                    + "restart), newest created first. dispatchedAt is null until the plan was sent. Open one "
                    + "with GET /api/plans/{id}.")
    @GetMapping
    public List<PlanSummaryResponse> list() {
        return planService.list();
    }

    @Operation(summary = "Plan a run from one depot",
            description = "Returns both variants, the cost of priority, unassigned shipments and warnings. "
                    + "Defaults: returnToDepot true, departureTime tomorrow 06:00. The plan is kept in memory "
                    + "(the 50 most recently used plans) until restart. Every truck must be based at the depot "
                    + "and available (not on route).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // OpenAPI only; the runtime status comes from ResponseEntity
    public ResponseEntity<PlanResponse> create(@RequestBody PlanRequest request) {
        PlanResponse plan = planService.create(request);
        return ResponseEntity.created(Locations.of(plan.id())).body(plan);
    }

    @Operation(summary = "Get a stored plan", description = "404 when the plan is unknown, was evicted or the "
            + "server restarted. dispatchedAt is set once the plan was sent.")
    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable String id) {
        return planService.get(id);
    }

    @Operation(summary = "Send the trucks of a plan variant",
            description = "Marks every truck with a route in the chosen variant as on route (unavailable) and "
                    + "deletes every shipment carried on those routes; unassigned shipments and unused trucks are "
                    + "untouched. 404 for an unknown plan; 409 when the plan was already sent, has no routes, or "
                    + "is out of date (a routed truck is on route or gone, or a routed shipment is gone).")
    @ConflictResponse("Plan already sent, has no routes, or is out of date")
    @PostMapping("/{id}/dispatch")
    public DispatchResponse dispatch(@PathVariable String id, @Valid @RequestBody DispatchRequest request) {
        return dispatchService.dispatch(id, request);
    }
}
