package com.haulwise.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.Settings;
import com.haulwise.repository.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@HaulWiseApiTest
@AutoConfigureMockMvc
class SettingsApiTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SettingsRepository settingsRepository;

    @BeforeEach
    void removeSettingsRow() {
        settingsRepository.deleteAll();
    }

    @Test
    void getWithoutRowReturnsDefaultsAndCreatesTheRow() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dieselPricePlnPerLitre").value(6.20))
                .andExpect(jsonPath("$.averageSpeedKmh").value(70.0))
                .andExpect(jsonPath("$.roadFactor").value(1.3))
                .andExpect(jsonPath("$.serviceMinutesPerStop").value(30))
                .andExpect(jsonPath("$.urgentWeightPlnPerHour").value(150.0))
                .andExpect(jsonPath("$.highWeightPlnPerHour").value(50.0))
                .andExpect(jsonPath("$.complianceEnabled").value(true))
                .andExpect(jsonPath("$.maxContinuousDrivingMinutes").value(270))
                .andExpect(jsonPath("$.breakMinutes").value(45))
                .andExpect(jsonPath("$.maxDailyDrivingMinutes").value(540))
                .andExpect(jsonPath("$.consolidationFillThresholdPct").value(60))
                .andExpect(jsonPath("$.id").doesNotExist());

        assertThat(settingsRepository.findById(Settings.SINGLETON_ID)).isPresent();
    }

    @Test
    void putReplacesSettingsAndGetReflectsIt() throws Exception {
        String changed = """
                {"dieselPricePlnPerLitre": 6.85, "averageSpeedKmh": 65, "roadFactor": 1.25,
                 "serviceMinutesPerStop": 20, "urgentWeightPlnPerHour": 200, "highWeightPlnPerHour": 80,
                 "complianceEnabled": false, "maxContinuousDrivingMinutes": 240, "breakMinutes": 30,
                 "maxDailyDrivingMinutes": 600, "consolidationFillThresholdPct": 75}
                """;

        mvc.perform(putSettings(changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dieselPricePlnPerLitre").value(6.85))
                .andExpect(jsonPath("$.complianceEnabled").value(false));

        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dieselPricePlnPerLitre").value(6.85))
                .andExpect(jsonPath("$.averageSpeedKmh").value(65.0))
                .andExpect(jsonPath("$.roadFactor").value(1.25))
                .andExpect(jsonPath("$.serviceMinutesPerStop").value(20))
                .andExpect(jsonPath("$.urgentWeightPlnPerHour").value(200.0))
                .andExpect(jsonPath("$.highWeightPlnPerHour").value(80.0))
                .andExpect(jsonPath("$.complianceEnabled").value(false))
                .andExpect(jsonPath("$.maxContinuousDrivingMinutes").value(240))
                .andExpect(jsonPath("$.breakMinutes").value(30))
                .andExpect(jsonPath("$.maxDailyDrivingMinutes").value(600))
                .andExpect(jsonPath("$.consolidationFillThresholdPct").value(75));
        assertThat(settingsRepository.count()).isEqualTo(1);
    }

    @Test
    void putAcceptsBoundaryValuesForCrossFieldRules() throws Exception {
        mvc.perform(putSettings(body(0.01, 0.0, 0.0, 540, 540)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgentWeightPlnPerHour").value(0.0))
                .andExpect(jsonPath("$.maxContinuousDrivingMinutes").value(540));
    }

    @Test
    void outOfRangeAndMissingValuesReturn400WithFieldErrors() throws Exception {
        String invalid = """
                {"dieselPricePlnPerLitre": 0, "averageSpeedKmh": 70, "roadFactor": 0.9,
                 "serviceMinutesPerStop": 30, "urgentWeightPlnPerHour": 150, "highWeightPlnPerHour": -1,
                 "complianceEnabled": null, "maxContinuousDrivingMinutes": 270, "breakMinutes": 45,
                 "maxDailyDrivingMinutes": 540, "consolidationFillThresholdPct": 101}
                """;

        mvc.perform(putSettings(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Request has 5 invalid fields."))
                .andExpect(jsonPath("$.errors", hasSize(5)))
                .andExpect(jsonPath("$.errors[0].field").value("complianceEnabled"))
                .andExpect(jsonPath("$.errors[1].field").value("consolidationFillThresholdPct"))
                .andExpect(jsonPath("$.errors[1].message").value("must be less than or equal to 100"))
                .andExpect(jsonPath("$.errors[2].field").value("dieselPricePlnPerLitre"))
                .andExpect(jsonPath("$.errors[3].field").value("highWeightPlnPerHour"))
                .andExpect(jsonPath("$.errors[4].field").value("roadFactor"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "averageSpeedKmh, 9.9", "averageSpeedKmh, 90.1",
            "maxContinuousDrivingMinutes, 29", "maxContinuousDrivingMinutes, 901",
            "breakMinutes, 14", "breakMinutes, 181",
            "maxDailyDrivingMinutes, 59", "maxDailyDrivingMinutes, 901"
    })
    void valueJustOutsideRangeReturns400ForThatField(String field, String value) throws Exception {
        String invalid = body(6.2, 150.0, 50.0, 270, 540)
                .replaceFirst("\"" + field + "\": [0-9.]+", "\"" + field + "\": " + value);

        mvc.perform(putSettings(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]", hasSize(1)));
    }

    @Test
    void fractionalValueForIntegerFieldReturns400() throws Exception {
        String fractional = body(6.2, 150.0, 50.0, 270, 540)
                .replace("\"serviceMinutesPerStop\": 30", "\"serviceMinutesPerStop\": 20.7");

        mvc.perform(putSettings(fractional))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void crossFieldViolationsReturn400AndKeepStoredSettings() throws Exception {
        mvc.perform(putSettings(body(7.0, 40.0, 50.0, 600, 540)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("maxContinuousDrivingMinutes"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be less than or equal to maxDailyDrivingMinutes"))
                .andExpect(jsonPath("$.errors[1].field").value("urgentWeightPlnPerHour"))
                .andExpect(jsonPath("$.errors[1].message")
                        .value("must be greater than or equal to highWeightPlnPerHour"));

        mvc.perform(get("/api/settings")).andExpect(jsonPath("$.dieselPricePlnPerLitre").value(6.20));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(putSettings("{\"dieselPricePlnPerLitre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongValueTypeReturns400() throws Exception {
        String wrongType = body(6.2, 150.0, 50.0, 270, 540)
                .replace("\"complianceEnabled\": true", "\"complianceEnabled\": \"maybe\"");

        mvc.perform(putSettings(wrongType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getFromAllowedOriginCarriesAllowOrigin() throws Exception {
        mvc.perform(get("/api/settings").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    private static MockHttpServletRequestBuilder putSettings(String body) {
        return put("/api/settings").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(double dieselPrice, double urgentWeight, double highWeight,
                               int maxContinuousMinutes, int maxDailyMinutes) {
        return """
                {"dieselPricePlnPerLitre": %s, "averageSpeedKmh": 70, "roadFactor": 1.3,
                 "serviceMinutesPerStop": 30, "urgentWeightPlnPerHour": %s, "highWeightPlnPerHour": %s,
                 "complianceEnabled": true, "maxContinuousDrivingMinutes": %d, "breakMinutes": 45,
                 "maxDailyDrivingMinutes": %d, "consolidationFillThresholdPct": 60}
                """.formatted(dieselPrice, urgentWeight, highWeight, maxContinuousMinutes, maxDailyMinutes);
    }
}
