package com.haulwise.controller;

import com.haulwise.dto.DistanceMatrixResponse;
import com.haulwise.service.DistanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Distances", description = "Road distance and driving time matrix between warehouses")
@RequestMapping("/api/distances")
public class DistanceController {

    private final DistanceService distanceService;

    public DistanceController(DistanceService distanceService) {
        this.distanceService = distanceService;
    }

    @Operation(summary = "Get the complete distance matrix (gaps filled with estimates)")
    @GetMapping
    public DistanceMatrixResponse get() {
        return distanceService.getMatrix();
    }
}
