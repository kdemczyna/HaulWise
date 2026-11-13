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

const plnFormat = new Intl.NumberFormat("en-GB", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const oneDecimalFormat = new Intl.NumberFormat("en-GB", {
  maximumFractionDigits: 1,
});

function groupThousands(formatted) {
  return formatted.replaceAll(",", NO_BREAK_SPACE);
}

/**
 * Plan numbers arrive already rounded by the backend (PLN 2 dp, km and litres 1 dp),
 * so these formatters use the same fraction digits and never round them differently.
 * 9849.88 -> "9 849.88 PLN".
 */
export function formatPln(value) {
  return `${groupThousands(plnFormat.format(value))}${NO_BREAK_SPACE}PLN`;
}

/** 3750 -> "3 750 km", 38.5 -> "38.5 km". */
export function formatKm(value) {
  return `${groupThousands(oneDecimalFormat.format(value))}${NO_BREAK_SPACE}km`;
}

/** 1164.1 -> "1 164.1 L". */
export function formatLitres(value) {
  return `${groupThousands(oneDecimalFormat.format(value))}${NO_BREAK_SPACE}L`;
}

/** Whole minutes as the backend's delta sentence spells them: "41 min", "1 h 15 min", "2 h". */
export function formatDuration(minutes) {
  const total = Math.round(minutes);
  if (total < 60) {
    return `${total} min`;
  }
  const hours = Math.floor(total / 60);
  const rest = total % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** Prefixes a formatted absolute value with its sign: (85, formatKm) -> "+85 km", (-12, formatKm) -> "-12 km". */
export function formatSigned(value, format) {
  if (value === 0) {
    return format(0);
  }
  return `${value > 0 ? "+" : "-"}${format(Math.abs(value))}`;
}

/** Minutes from one API local date-time to another (positive when `to` is later); null if either is missing. */
export function minutesBetween(from, to) {
  const start = toUtcMillis(from);
  const end = toUtcMillis(to);
  if (start === null || end === null) {
    return null;
  }
  return Math.round((end - start) / 60000);
}

/** Wall-clock fields as UTC milliseconds, so differences ignore the browser's time zone. */
function toUtcMillis(value) {
  const match = value ? LOCAL_DATE_TIME.exec(value) : null;
  if (!match) {
    return null;
  }
  const [, year, month, day, hours, minutes] = match.map(Number);
  return Date.UTC(year, month - 1, day, hours, minutes);
}

/** Any API local date-time (arrival, departure) in the same short form as a deadline. */
export const formatDateTime = formatDeadline;
