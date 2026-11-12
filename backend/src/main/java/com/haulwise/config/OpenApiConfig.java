package com.haulwise.config;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.haulwise.controller.ConflictResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the Swagger UI ({@code /swagger-ui.html}) plus the shared problem+json error shape
 * (D-12), attached to the error responses every operation can return.
 */
@Configuration
public class OpenApiConfig {

    static final String TITLE = "HaulWise API";

    /** Kept in step with the Maven project version. */
    private static final String API_VERSION = "0.1.0";

    private static final String PROBLEM_SCHEMA = "Problem";

    @Bean
    OpenAPI haulWiseOpenApi() {
        return new OpenAPI().info(new Info()
                .title(TITLE)
                .version(API_VERSION)
                .description("Warehouses, trucks and shipments for planning delivery runs. "
                        + "Errors use RFC 9457 problem+json with a machine-readable `code`."));
    }

    /** Documents the 409 declared with {@link ConflictResponse} next to the generated success response. */
    @Bean
    OperationCustomizer conflictResponseCustomizer() {
        return (operation, handlerMethod) -> {
            ConflictResponse conflict = handlerMethod.getMethodAnnotation(ConflictResponse.class);
            if (conflict != null) {
                operation.getResponses().addApiResponse("409", new ApiResponse().description(conflict.value()));
            }
            return operation;
        };
    }

    /**
     * Registers the problem schema, adds 400 to operations with a body or parameters and 404 to operations
     * with a path parameter, and gives every 4xx response the problem+json content.
     */
    @Bean
    OpenApiCustomizer problemResponsesCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            openApi.getPaths().forEach((path, item) ->
                    item.readOperationsMap().forEach((method, operation) ->
                            documentErrors(method, operation)));
        };
    }

    private static void documentErrors(PathItem.HttpMethod method, Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (acceptsInput(method, operation)) {
            addIfAbsent(responses, "400", "Invalid or malformed request");
        }
        if (hasPathParameter(operation)) {
            addIfAbsent(responses, "404", "Resource not found");
        }
        responses.forEach((code, response) -> {
            if (code.startsWith("4")) {
                response.setContent(problemContent());
            }
        });
    }

    private static boolean hasPathParameter(Operation operation) {
        return operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(parameter -> "path".equals(parameter.getIn()));
    }

    private static boolean acceptsInput(PathItem.HttpMethod method, Operation operation) {
        return method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT
                || operation.getParameters() != null && !operation.getParameters().isEmpty();
    }

    private static void addIfAbsent(ApiResponses responses, String code, String description) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().description(description));
        }
    }

    private static Content problemContent() {
        return new Content().addMediaType(APPLICATION_PROBLEM_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
    }

    private static Schema<?> problemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().example("name"))
                .addProperty("message", new StringSchema().example("size must be between 2 and 60"));
        return new ObjectSchema()
                .description("RFC 9457 problem detail with HaulWise extensions")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Validation failed"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Request has 1 invalid field."))
                .addProperty("instance", new StringSchema().example("/api/warehouses"))
                .addProperty("code", new StringSchema().example("VALIDATION_FAILED"))
                .addProperty("errors", new ArraySchema().items(fieldError)
                        .description("Present only when code is VALIDATION_FAILED"));
    }
}
