import { ApiError } from "../api/client.js";
import { applyServerErrors } from "./formErrors.js";

function fakeForm(values) {
  return { getValues: () => values, setErrors: vi.fn() };
}

describe("applyServerErrors", () => {
  it("puts known field errors on the form and returns a hint", () => {
    const form = fakeForm({ pallets: 1, destinationId: "2" });
    const error = new ApiError({
      status: 400,
      code: "VALIDATION_FAILED",
      detail: "Request has 1 invalid field.",
      fieldErrors: { destinationId: "must differ from originId" },
    });

    expect(applyServerErrors(form, error, "shipment")).toBe(
      "The shipment could not be saved. Fix the highlighted fields.",
    );
    expect(form.setErrors).toHaveBeenCalledWith({
      destinationId: "must differ from originId",
    });
  });

  it("adds errors of fields the form does not show to the message", () => {
    const form = fakeForm({ name: "" });
    const error = new ApiError({
      status: 400,
      code: "VALIDATION_FAILED",
      detail: "Request has 2 invalid fields.",
      fieldErrors: { name: "size must be between 2 and 60", id: "unknown" },
    });

    expect(applyServerErrors(form, error, "warehouse")).toBe(
      "The warehouse could not be saved. Fix the highlighted fields. unknown",
    );
  });

  it("returns the server message when no field is involved", () => {
    const form = fakeForm({ plate: "" });
    const error = new ApiError({
      status: 409,
      code: "CONFLICT",
      detail: "A truck with plate 'WX 1001A' already exists.",
    });

    expect(applyServerErrors(form, error, "truck")).toBe(
      "A truck with plate 'WX 1001A' already exists.",
    );
    expect(form.setErrors).toHaveBeenCalledWith({});
  });

  it("never shows the text of unexpected errors", () => {
    expect(
      applyServerErrors(fakeForm({}), new TypeError("x is undefined"), "truck"),
    ).toBe("Something went wrong. Please try again.");
  });
});
