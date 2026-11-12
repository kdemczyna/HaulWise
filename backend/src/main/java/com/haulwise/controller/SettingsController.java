package com.haulwise.controller;

import com.haulwise.dto.SettingsRequest;
import com.haulwise.dto.SettingsResponse;
import com.haulwise.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Settings", description = "Fuel price, estimate fallbacks, priority weights and driving-time limits")
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Operation(summary = "Get the planning settings")
    @GetMapping
    public SettingsResponse get() {
        return settingsService.get();
    }

    @Operation(summary = "Replace the planning settings")
    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return settingsService.update(request);
    }
}
