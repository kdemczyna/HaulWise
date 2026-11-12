package com.haulwise.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
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

    @Test
    void apiDocsDescribeTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(OpenApiConfig.TITLE))
                .andExpect(jsonPath("$.info.version").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/warehouses'].get.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/trucks/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/shipments']").exists());
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
    void swaggerUiIsServedAtTheDocumentedPath() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
