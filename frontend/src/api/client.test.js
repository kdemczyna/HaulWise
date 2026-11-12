import {
  ApiError,
  NETWORK_ERROR_MESSAGE,
  apiBaseUrl,
  errorMessage,
  request,
} from "./client.js";

function jsonResponse(status, body, contentType = "application/json") {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": contentType },
  });
}

describe("api client", () => {
  let fetchMock;

  beforeEach(() => {
    vi.stubEnv("VITE_API_BASE_URL", "http://api.test/api/");
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("uses VITE_API_BASE_URL without a trailing slash", () => {
    expect(apiBaseUrl()).toBe("http://api.test/api");
  });

  it("GETs JSON from the base URL", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, [{ id: 1 }]));

    await expect(request("GET", "/warehouses")).resolves.toEqual([{ id: 1 }]);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/warehouses");
    expect(init.method).toBe("GET");
    expect(init.body).toBeUndefined();
    expect(init.headers["Content-Type"]).toBeUndefined();
  });

  it("sends a JSON body with a Content-Type header", async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { id: 7, name: "A" }));

    await expect(
      request("POST", "/warehouses", { name: "A" }),
    ).resolves.toEqual({ id: 7, name: "A" });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe("POST");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ name: "A" }));
  });

  it("resolves with null for 204 No Content", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(request("DELETE", "/trucks/1")).resolves.toBeNull();
  });

  it("turns a ProblemDetail into an ApiError with field errors", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        400,
        {
          type: "about:blank",
          title: "Validation failed",
          status: 400,
          detail: "Request has 3 invalid fields.",
          code: "VALIDATION_FAILED",
          errors: [
            { field: "destinationId", message: "must differ from originId" },
            { field: "pallets", message: "must be at least 1" },
            { field: "pallets", message: "must not be null" },
          ],
        },
        "application/problem+json",
      ),
    );

    const error = await request("POST", "/shipments", {}).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: "VALIDATION_FAILED",
      title: "Validation failed",
      detail: "Request has 3 invalid fields.",
      message: "Request has 3 invalid fields.",
      fieldErrors: {
        destinationId: "must differ from originId",
        pallets: "must be at least 1; must not be null",
      },
    });
  });

  it("keeps the detail of a ProblemDetail without errors[]", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        409,
        {
          title: "Conflict",
          status: 409,
          detail: "Warehouse 'GDN Port' is used by 3 shipments.",
          code: "CONFLICT",
        },
        "application/problem+json",
      ),
    );

    const error = await request("DELETE", "/warehouses/3").catch((e) => e);

    expect(error).toMatchObject({
      status: 409,
      code: "CONFLICT",
      message: "Warehouse 'GDN Port' is used by 3 shipments.",
      fieldErrors: {},
    });
  });

  it("gives a readable error for a non-JSON error response", async () => {
    fetchMock.mockResolvedValue(
      new Response("<html>Bad gateway</html>", {
        status: 502,
        headers: { "Content-Type": "text/html" },
      }),
    );

    const error = await request("GET", "/settings").catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.code).toBe("HTTP_502");
    expect(error.message).toBe("The server responded with status 502.");
  });

  it("falls back to the status message for JSON that is not a ProblemDetail", async () => {
    fetchMock.mockResolvedValue(jsonResponse(500, { error: "boom" }));

    const error = await request("GET", "/trucks").catch((e) => e);

    expect(error).toMatchObject({
      status: 500,
      code: "HTTP_500",
      message: "The server responded with status 500.",
      fieldErrors: {},
    });
  });

  it("turns a network failure into a NETWORK ApiError", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    const error = await request("GET", "/warehouses").catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 0,
      code: "NETWORK",
      message: NETWORK_ERROR_MESSAGE,
    });
  });

  it("rejects an unreadable success body", async () => {
    fetchMock.mockResolvedValue(new Response("not json", { status: 200 }));

    const error = await request("GET", "/warehouses").catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("INVALID_RESPONSE");
  });
});

describe("errorMessage", () => {
  it("shows the ApiError message", () => {
    expect(errorMessage(new ApiError({ status: 404, detail: "Gone." }))).toBe(
      "Gone.",
    );
  });

  it("hides the text of unexpected errors", () => {
    expect(errorMessage(new Error("x is undefined"))).toBe(
      "Something went wrong. Please try again.",
    );
  });
});
