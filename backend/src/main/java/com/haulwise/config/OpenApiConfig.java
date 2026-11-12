package com.haulwise.config;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.haulwise.controller.ConflictResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the Swagger UI ({@code /swagger-ui.html}) plus the shared problem+json error shape
 * (D-12), attached to the error responses every operation can return. Tags and paths are ordered so
 * the UI (no sorters configured, it keeps the spec order) shows the resources in a fixed order and
 * each resource's operations as list, create, get, update, delete.
 */
@Configuration
public class OpenApiConfig {

    static final String TITLE = "HaulWise API";

    /** Kept in step with the Maven project version. */
    private static final String API_VERSION = "0.1.0";

    private static final String PROBLEM_SCHEMA = "Problem";

    /** Order of the tags declared on the controllers; tags not listed here follow in their own order. */
    static final List<String> TAG_ORDER = List.of("Warehouses", "Trucks", "Shipments", "Settings", "Distances");

    @Bean
    OpenAPI haulWiseOpenApi() {
        return new OpenAPI().info(new Info()
                .title(TITLE)
                .version(API_VERSION)
                .description("Plan delivery runs: manage warehouses, trucks and shipments, tune the cost "
                        + "settings and inspect the distance matrix between warehouses. Date-times are local "
                        + "Europe/Warsaw times without offset. Errors use RFC 9457 problem+json with a "
                        + "machine-readable `code`."));
    }

    /**
     * Sorts the tags by {@link #TAG_ORDER} and the paths by key. Within a path item operations are written
     * as GET, PUT, POST, DELETE, so {@code /api/x} (list, create) followed by {@code /api/x/{id}}
     * (get, update, delete) gives the intended operation order.
     */
    @Bean
    OpenApiCustomizer tagAndPathOrderCustomizer() {
        return openApi -> {
            if (openApi.getTags() != null) {
                openApi.setTags(openApi.getTags().stream()
                        .sorted(Comparator.comparingInt(tag -> tagRank(tag.getName())))
                        .collect(Collectors.toCollection(ArrayList::new)));
            }
            Paths sorted = new Paths();
            openApi.getPaths().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.addPathItem(entry.getKey(), entry.getValue()));
            sorted.setExtensions(openApi.getPaths().getExtensions());
            openApi.setPaths(sorted);
        };
    }

    private static int tagRank(String name) {
        int index = TAG_ORDER.indexOf(name);
        return index < 0 ? TAG_ORDER.size() : index;
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
