package com.haulwise.config;

import static com.haulwise.controller.ApiTestIds.MISSING_ID;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@HaulWiseApiTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "haulwise.cors.allowed-origins=" + CorsApiTest.ALLOWED + ", " + CorsApiTest.OTHER_ALLOWED)
class CorsApiTest {

    static final String ALLOWED = "http://localhost:5173";
    static final String OTHER_ALLOWED = "http://frontend.test:4173";
    private static final String DISALLOWED = "http://evil.example";

    @Autowired
    private MockMvc mvc;

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        mvc.perform(preflight(ALLOWED, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void everyConfiguredOriginIsAllowedAfterTrimming() throws Exception {
        mvc.perform(preflight(OTHER_ALLOWED, "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, OTHER_ALLOWED));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mvc.perform(preflight(DISALLOWED, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightForMethodOutsideTheListIsRejected() throws Exception {
        mvc.perform(preflight(ALLOWED, "PATCH"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void actualGetFromAllowedOriginCarriesAllowOrigin() throws Exception {
        mvc.perform(get("/api/warehouses").header(HttpHeaders.ORIGIN, ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    @Test
    void actualGetFromDisallowedOriginIsRejected() throws Exception {
        mvc.perform(get("/api/warehouses").header(HttpHeaders.ORIGIN, DISALLOWED))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void createExposesLocationToTheBrowser() throws Exception {
        mvc.perform(post("/api/warehouses").header(HttpHeaders.ORIGIN, ALLOWED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "CORS Depot", "city": "Warsaw", "lat": 52.23, "lon": 21.01}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.LOCATION));
    }

    @Test
    void notFoundFromAllowedOriginStillCarriesAllowOrigin() throws Exception {
        mvc.perform(get("/api/warehouses/" + MISSING_ID).header(HttpHeaders.ORIGIN, ALLOWED))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    @Test
    void validationErrorFromAllowedOriginStillCarriesAllowOrigin() throws Exception {
        mvc.perform(post("/api/warehouses").header(HttpHeaders.ORIGIN, ALLOWED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    private static MockHttpServletRequestBuilder preflight(String origin, String method) {
        return options("/api/warehouses")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type");
    }
}
