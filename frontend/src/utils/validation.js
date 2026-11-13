/**
 * @mantine/form validators mirroring the backend Bean Validation ranges (ARCHITECTURE 4 / 5),
 * for fast feedback. The server stays the authority; its field errors are shown too.
 */

const isBlank = (value) =>
  value === undefined || value === null || String(value).trim() === "";

/** Text of `min`..`max` characters after trimming (the backend strips text too). */
export function textLength(label, min, max) {
  return (value) => {
    if (isBlank(value)) {
      return `${label} is required`;
    }
    const length = String(value).trim().length;
    return length < min || length > max
      ? `${label} must be ${min} to ${max} characters`
      : null;
  };
}

/**
 * Number within [min, max]; `minExclusive` makes the lower bound exclusive,
 * `integer` rejects fractions (the backend rejects them as malformed).
 */
export function numberRange(
  label,
  min,
  max,
  { integer = false, minExclusive = false } = {},
) {
  return (value) => {
    if (isBlank(value)) {
      return `${label} is required`;
    }
    const number = typeof value === "number" ? value : Number(value);
    if (!Number.isFinite(number)) {
      return `${label} must be a number`;
    }
    if (integer && !Number.isInteger(number)) {
      return `${label} must be a whole number`;
    }
    const tooLow = minExclusive ? number <= min : number < min;
    if (tooLow || number > max) {
      return minExclusive
        ? `${label} must be greater than ${min} and at most ${max}`
        : `${label} must be between ${min} and ${max}`;
    }
    return null;
  };
}

/** A choice from a select (value "" means nothing chosen). */
export function requiredChoice(message) {
  return (value) => (isBlank(value) ? message : null);
}
