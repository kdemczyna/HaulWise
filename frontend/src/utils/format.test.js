import {
  formatDeadline,
  formatDuration,
  formatInteger,
  formatKg,
  formatKm,
  formatPln,
  formatSigned,
  minutesBetween,
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

describe("plan number formatters", () => {
  it("keeps the backend's fraction digits", () => {
    expect(formatPln(9849.88)).toBe(`9${NBSP}849.88${NBSP}PLN`);
    expect(formatPln(2632.5)).toBe(`2${NBSP}632.50${NBSP}PLN`);
    expect(formatKm(3750)).toBe(`3${NBSP}750${NBSP}km`);
    expect(formatKm(38.5)).toBe(`38.5${NBSP}km`);
  });

  it.each([
    [41, "41 min"],
    [60, "1 h"],
    [75, "1 h 15 min"],
    [678, "11 h 18 min"],
  ])("formats %i minutes as %s", (minutes, text) => {
    expect(formatDuration(minutes)).toBe(text);
  });

  it("prefixes the sign", () => {
    expect(formatSigned(75, formatDuration)).toBe("+1 h 15 min");
    expect(formatSigned(-12, String)).toBe("-12");
    expect(formatSigned(0, String)).toBe("0");
  });

  it("counts minutes between local date-times across midnight", () => {
    expect(minutesBetween("2026-11-14T12:00:00", "2026-11-14T23:18:00")).toBe(
      678,
    );
    expect(minutesBetween("2026-11-14T23:30", "2026-11-15T00:15")).toBe(45);
    expect(minutesBetween(null, "2026-11-15T00:15")).toBeNull();
  });
});
