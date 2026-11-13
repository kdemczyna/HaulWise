package com.haulwise.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulwise.HaulWiseApiTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@HaulWiseApiTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    private static final String PROBLEM_REF = "#/components/schemas/Problem";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiDocsDescribeTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(OpenApiConfig.TITLE))
                .andExpect(jsonPath("$.info.version").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/warehouses'].get.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/trucks/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/shipments']").exists())
                .andExpect(jsonPath("$.paths['/api/settings'].get.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/distances'].get.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/settings'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/settings'].put.responses['409']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/plans'].post.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/plans'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/plans'].post.responses['400']"
                        + ".content['application/problem+json'].schema.$ref").value(PROBLEM_REF))
                .andExpect(jsonPath("$.paths['/api/plans/{id}'].get.responses['400']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/warehouses/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/plans/{id}'].get.responses['404']"
                        + ".content['application/problem+json'].schema.$ref").value(PROBLEM_REF));
    }

    @Test
    void descriptionNamesEveryResourceAndTagsAreInResourceOrder() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.description", containsString("warehouses, trucks and shipments")))
                .andExpect(jsonPath("$.info.description", containsString("settings")))
                .andExpect(jsonPath("$.info.description", containsString("distance")))
                .andExpect(jsonPath("$.info.description", containsString("plan runs")))
                .andExpect(jsonPath("$.tags[*].name")
                        .value(contains("Warehouses", "Trucks", "Shipments", "Settings", "Distances", "Plans",
                                "Recommendations")))
                .andExpect(jsonPath("$.tags[*].description").value(everyItem(not(emptyOrNullString()))));
    }

    @Test
    void collectionPathComesBeforeItemPathSoOperationsReadListCreateGetUpdateDelete() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");
        List<String> pathOrder = new ArrayList<>();
        paths.fieldNames().forEachRemaining(pathOrder::add);
        List<String> itemMethods = new ArrayList<>();
        paths.get("/api/warehouses/{id}").fieldNames().forEachRemaining(itemMethods::add);

        assertThat(pathOrder).isSorted();
        assertThat(pathOrder.indexOf("/api/warehouses")).isLessThan(pathOrder.indexOf("/api/warehouses/{id}"));
        assertThat(itemMethods).containsExactly("get", "put", "delete");
    }

    @Test
    void errorResponsesUseTheProblemSchema() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.Problem.properties.code").exists())
                .andExpect(jsonPath("$.paths['/api/warehouses'].post.responses['201'].content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/api/warehouses'].post.responses['400']"
                        + ".content['application/problem+json'].schema.$ref").value(PROBLEM_REF))
                .andExpect(jsonPath("$.paths['/api/warehouses'].post.responses['409']"
                        + ".content['application/problem+json'].schema.$ref").value(PROBLEM_REF))
                .andExpect(jsonPath("$.paths['/api/warehouses/{id}'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/warehouses/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/warehouses/{id}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/warehouses/{id}'].delete.responses['404']"
                        + ".content['application/problem+json'].schema.$ref").value(PROBLEM_REF));
    }

    @Test
    void planSchemasDocumentLocalDepartureTimeAndPinnedMeaning() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.PlanRequest.properties.departureTime.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.PlanRequest.properties.departureTime.format").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PlanRequest.properties.departureTime.example")
                        .value("2026-11-13T06:00:00"))
                .andExpect(jsonPath("$.components.schemas.PlanRequest.properties.departureTime.pattern").isNotEmpty())
                .andExpect(jsonPath("$.components.schemas.PlanRequest.properties.departureTime.description",
                        containsString("without offset")))
                .andExpect(jsonPath("$.components.schemas.StopResponse.properties.pinned.description",
                        containsString("OPTIMAL ignores it")));
    }

    @Test
    void swaggerUiIsServedAtTheDocumentedPath() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
