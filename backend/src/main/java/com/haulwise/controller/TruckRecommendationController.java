package com.haulwise.controller;

import com.haulwise.dto.TruckRecommendationRequest;
import com.haulwise.dto.TruckRecommendationResponse;
import com.haulwise.service.TruckRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recommends trucks for a depot + shipment selection, considering the depot's whole available fleet (T-30,
 * T-32, T-33). The request body is validated by {@link TruckRecommendationService} (not {@code @Valid}), so field
 * rules and id checks are reported in one 400, the same way {@code PlanController} does for plans.
 */
@RestController
@Tag(name = "Recommendations", description = "Cheapest combination of a depot's available trucks for a shipment selection")
@RequestMapping("/api/trucks")
public class TruckRecommendationController {

    private final TruckRecommendationService recommendationService;

    public TruckRecommendationController(TruckRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Operation(summary = "Recommend trucks for a depot + shipment selection",
            description = "Searches combinations of every available truck based at the depot (not just a "
                    + "caller-provided subset; never trucks of other depots or trucks on route) and "
                    + "returns the cheapest one that carries every shipment, with its cost and utilisation. "
                    + "When no combination fits everything, names the shipments that do not fit anywhere "
                    + "instead of failing.")
    @PostMapping("/recommend")
    public TruckRecommendationResponse recommend(@RequestBody TruckRecommendationRequest request) {
        return recommendationService.recommend(request);
    }
}
