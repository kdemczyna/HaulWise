const MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

const LOCAL_DATE_TIME =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/;

const NO_BREAK_SPACE = String.fromCharCode(0xa0);

const integerFormat = new Intl.NumberFormat("en-GB", {
  maximumFractionDigits: 0,
});

/** Groups thousands with a no-break space: 3600 -> "3 600" (the design's number style). */
export function formatInteger(value) {
  return integerFormat.format(value).replaceAll(",", NO_BREAK_SPACE);
}

/** 3600 -> "3 600 kg". */
export function formatKg(value) {
  return `${formatInteger(value)}${NO_BREAK_SPACE}kg`;
}

/**
 * A local date-time from the API ("2026-11-13T20:00:00", Europe/Warsaw implied, D-10)
 * as "13 Nov, 20:00"; "-" when there is none. Parsed by hand, so the browser's
 * time zone never shifts it.
 */
export function formatDeadline(value) {
  const match = value ? LOCAL_DATE_TIME.exec(value) : null;
  if (!match) {
    return "-";
  }
  const [, , month, day, hours, minutes] = match;
  return `${Number(day)} ${MONTHS[Number(month) - 1]}, ${hours}:${minutes}`;
}

/** API date-time -> value of an `<input type="datetime-local">` ("2026-11-13T20:00"); "" for none. */
export function toDateTimeInput(value) {
  const match = value ? LOCAL_DATE_TIME.exec(value) : null;
  if (!match) {
    return "";
  }
  const [, year, month, day, hours, minutes] = match;
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

/**
 * `<input type="datetime-local">` value -> API date-time without offset
 * ("2026-11-13T20:00:00"); null when empty.
 */
export function fromDateTimeInput(value) {
  const match = value ? LOCAL_DATE_TIME.exec(value) : null;
  if (!match) {
    return null;
  }
  const [, year, month, day, hours, minutes] = match;
  return `${year}-${month}-${day}T${hours}:${minutes}:00`;
}
