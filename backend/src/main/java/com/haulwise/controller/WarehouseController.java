package com.haulwise.controller;

import com.haulwise.dto.WarehouseRequest;
import com.haulwise.dto.WarehouseResponse;
import com.haulwise.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Warehouses", description = "Depots and delivery points")
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "List warehouses sorted by name")
    @GetMapping
    public List<WarehouseResponse> list() {
        return warehouseService.findAll();
    }

    @Operation(summary = "Get a warehouse")
    @GetMapping("/{id}")
    public WarehouseResponse get(@PathVariable Long id) {
        return warehouseService.findById(id);
    }

    @Operation(summary = "Create a warehouse")
    @ConflictResponse("Name already used")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // OpenAPI only; the runtime status comes from ResponseEntity
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse created = warehouseService.create(request);
        return ResponseEntity.created(Locations.of(created.id())).body(created);
    }

    @Operation(summary = "Update a warehouse")
    @ConflictResponse("Name already used")
    @PutMapping("/{id}")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @Operation(summary = "Delete a warehouse")
    @ConflictResponse("Warehouse still used by shipments")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // OpenAPI only; the runtime status comes from ResponseEntity
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
