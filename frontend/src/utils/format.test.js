import {
  formatDeadline,
  formatInteger,
  formatKg,
  fromDateTimeInput,
  toDateTimeInput,
} from "./format.js";

const NBSP = String.fromCharCode(0xa0);

describe("formatKg / formatInteger", () => {
  it("groups thousands with a no-break space", () => {
    expect(formatKg(3600)).toBe(`3${NBSP}600${NBSP}kg`);
    expect(formatKg(100000)).toBe(`100${NBSP}000${NBSP}kg`);
    expect(formatInteger(36)).toBe("36");
  });
});

describe("formatDeadline", () => {
  it("shows day, month and time of a local date-time", () => {
    expect(formatDeadline("2026-11-13T20:00:00")).toBe("13 Nov, 20:00");
    expect(formatDeadline("2026-01-05T07:05")).toBe("5 Jan, 07:05");
  });

  it("shows a dash when there is no deadline", () => {
    expect(formatDeadline(null)).toBe("-");
    expect(formatDeadline(undefined)).toBe("-");
    expect(formatDeadline("not a date")).toBe("-");
  });
});

describe("deadline input conversion", () => {
  it("turns an API date-time into a datetime-local value", () => {
    expect(toDateTimeInput("2026-11-13T20:00:00")).toBe("2026-11-13T20:00");
    expect(toDateTimeInput(null)).toBe("");
  });

  it("turns a datetime-local value into an API date-time without offset", () => {
    expect(fromDateTimeInput("2026-11-14T12:00")).toBe("2026-11-14T12:00:00");
    expect(fromDateTimeInput("")).toBeNull();
  });

  it("round-trips", () => {
    expect(fromDateTimeInput(toDateTimeInput("2026-11-13T20:00:00"))).toBe(
      "2026-11-13T20:00:00",
    );
  });
});
