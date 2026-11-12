const DEFAULT_BASE_URL = "http://localhost:8080/api";

export const NETWORK_ERROR_MESSAGE =
  "Cannot reach the HaulWise server. Is the backend running on :8080?";

const GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

/**
 * Error thrown for every failed API call: a ProblemDetail response (D-12),
 * a non-JSON error response, an unreadable success body or a network failure.
 */
export class ApiError extends Error {
  constructor({ status, code, title, detail, fieldErrors = {} }) {
    super(detail || title || GENERIC_ERROR_MESSAGE);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.title = title;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
  }
}

/** A message that is safe to show to the user for any thrown value. */
export function errorMessage(error) {
  return error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE;
}

export function apiBaseUrl() {
  const configured = import.meta.env.VITE_API_BASE_URL || DEFAULT_BASE_URL;
  return configured.replace(/\/+$/, "");
}

/**
 * Sends a JSON request to the HaulWise API.
 * Resolves with the parsed body, or null for 204 / an empty body.
 * Rejects with an ApiError.
 */
export async function request(method, path, body) {
  const init = {
    method,
    headers: { Accept: "application/json, application/problem+json" },
  };
  if (body !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(apiBaseUrl() + path, init);
  } catch {
    throw new ApiError({
      status: 0,
      code: "NETWORK",
      detail: NETWORK_ERROR_MESSAGE,
    });
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return null;
  }
  return parseBody(response);
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError({
      status: response.status,
      code: "INVALID_RESPONSE",
      detail: "The server sent a response that could not be read.",
    });
  }
}

async function toApiError(response) {
  const problem = await readProblem(response);
  if (!problem) {
    return new ApiError({
      status: response.status,
      code: `HTTP_${response.status}`,
      detail: `The server responded with status ${response.status}.`,
    });
  }
  return new ApiError({
    status: problem.status ?? response.status,
    code: problem.code ?? `HTTP_${response.status}`,
    title: problem.title,
    detail: problem.detail,
    fieldErrors: toFieldErrors(problem.errors),
  });
}

/** The ProblemDetail body, or null when the error body is not JSON or not problem-shaped. */
async function readProblem(response) {
  try {
    const body = await response.json();
    const isProblem =
      body !== null &&
      typeof body === "object" &&
      (typeof body.detail === "string" || typeof body.title === "string");
    return isProblem ? body : null;
  } catch {
    return null;
  }
}

/** Turns ProblemDetail errors[] into {field: message}; several messages for one field are joined. */
export function toFieldErrors(errors) {
  if (!Array.isArray(errors)) {
    return {};
  }
  return errors.reduce((fieldErrors, { field, message }) => {
    if (field) {
      fieldErrors[field] = fieldErrors[field]
        ? `${fieldErrors[field]}; ${message}`
        : message;
    }
    return fieldErrors;
  }, {});
}
