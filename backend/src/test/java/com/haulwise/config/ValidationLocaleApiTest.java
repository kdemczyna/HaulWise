package com.haulwise.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.dto.TruckRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Validation messages are English regardless of {@code Accept-Language} or the JVM default locale.
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
class ValidationLocaleApiTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl-PL");
    private static final String INVALID_TRUCK = """
            {"plate":"WX 1001A","capacityPallets":0,"payloadKg":24000,"baseConsumptionLPer100Km":28,
             "extraConsumptionFullLoadLPer100Km":10,"driverCostPlnPerHour":45,"homeWarehouseId":1}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Validator validator;

    @Test
    void apiMessagesAreEnglishEvenWhenPolishIsRequested() throws Exception {
        mvc.perform(post("/api/trucks")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "pl-PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_TRUCK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("capacityPallets")))
                .andExpect(jsonPath("$.errors[*].message", contains("must be greater than or equal to 1")));
    }

    @Test
    void validatorBeanIgnoresPolishDefaultAndContextLocale() {
        Locale originalDefault = Locale.getDefault();
        try {
            Locale.setDefault(POLISH);
            LocaleContextHolder.setLocale(POLISH);
            Set<ConstraintViolation<TruckRequest>> violations =
                    validator.validate(new TruckRequest("WX 1001A", 0, 24000, 28.0, 10.0, 45.0, 1L, null));

            assertThat(violations).extracting(ConstraintViolation::getMessage)
                    .containsExactly("must be greater than or equal to 1");
        } finally {
            Locale.setDefault(originalDefault);
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
