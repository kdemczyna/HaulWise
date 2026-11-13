import { numberRange, requiredChoice, textLength } from "./validation.js";

describe("textLength", () => {
  const name = textLength("Name", 2, 60);

  it("accepts trimmed text within the range", () => {
    expect(name("  WAW Central ")).toBeNull();
  });

  it("rejects blank, short and long text", () => {
    expect(name("   ")).toBe("Name is required");
    expect(name("X")).toBe("Name must be 2 to 60 characters");
    expect(name("X".repeat(61))).toBe("Name must be 2 to 60 characters");
  });
});

describe("numberRange", () => {
  it("checks inclusive bounds", () => {
    const lat = numberRange("Latitude", -90, 90);
    expect(lat(-90)).toBeNull();
    expect(lat(90)).toBeNull();
    expect(lat(90.1)).toBe("Latitude must be between -90 and 90");
    expect(lat("")).toBe("Latitude is required");
  });

  it("checks an exclusive lower bound", () => {
    const base = numberRange("Base consumption", 0, 100, {
      minExclusive: true,
    });
    expect(base(0)).toBe(
      "Base consumption must be greater than 0 and at most 100",
    );
    expect(base(0.1)).toBeNull();
  });

  it("rejects fractions for whole-number fields", () => {
    const pallets = numberRange("Pallets", 1, 100, { integer: true });
    expect(pallets(1.5)).toBe("Pallets must be a whole number");
    expect(pallets("-")).toBe("Pallets must be a number");
  });
});

describe("requiredChoice", () => {
  it("requires a non-empty value", () => {
    const origin = requiredChoice("Choose the origin warehouse");
    expect(origin("")).toBe("Choose the origin warehouse");
    expect(origin("1")).toBeNull();
  });
});
