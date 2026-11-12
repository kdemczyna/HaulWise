package com.haulwise.controller;

import com.haulwise.domain.Priority;
import com.haulwise.dto.ShipmentRequest;
import com.haulwise.dto.ShipmentResponse;
import com.haulwise.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shipment CRUD. Request bodies are validated by {@link ShipmentService} (not {@code @Valid}) so field
 * ranges and warehouse references are reported in one 400.
 */
@RestController
@Tag(name = "Shipments", description = "Orders to deliver from a depot")
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Operation(summary = "List shipments, optionally filtered by priority and origin")
    @GetMapping
    public List<ShipmentResponse> list(@RequestParam(required = false) Priority priority,
                                       @RequestParam(required = false) Long originId) {
        return shipmentService.search(priority, originId);
    }

    @Operation(summary = "Get a shipment")
    @GetMapping("/{id}")
    public ShipmentResponse get(@PathVariable Long id) {
        return shipmentService.findById(id);
    }

    @Operation(summary = "Create a shipment")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // OpenAPI only; the runtime status comes from ResponseEntity
    public ResponseEntity<ShipmentResponse> create(@RequestBody ShipmentRequest request) {
        ShipmentResponse created = shipmentService.create(request);
        return ResponseEntity.created(Locations.of(created.id())).body(created);
    }

    @Operation(summary = "Update a shipment")
    @PutMapping("/{id}")
    public ShipmentResponse update(@PathVariable Long id, @RequestBody ShipmentRequest request) {
        return shipmentService.update(id, request);
    }

    @Operation(summary = "Delete a shipment")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // OpenAPI only; the runtime status comes from ResponseEntity
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shipmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
