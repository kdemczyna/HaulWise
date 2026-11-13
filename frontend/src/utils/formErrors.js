import { errorMessage } from "../api/client.js";

/**
 * Puts the server's field errors (ApiError.fieldErrors) on the matching form fields and
 * returns the message for the form-level alert: a hint when fields were marked, otherwise
 * the server message (e.g. a 409 "A truck with plate 'WX 1001A' already exists.").
 * Messages of field errors for fields the form does not show are added to the alert text
 * (without the API field name, which means nothing to the user).
 */
export function applyServerErrors(form, error, itemType, outcome = "saved") {
  const fieldErrors = error?.fieldErrors ?? {};
  const values = form.getValues();
  const onForm = {};
  const elsewhere = [];
  for (const [field, message] of Object.entries(fieldErrors)) {
    if (field in values) {
      onForm[field] = message;
    } else {
      elsewhere.push(message);
    }
  }
  form.setErrors(onForm);

  if (Object.keys(onForm).length === 0) {
    return elsewhere.length > 0 ? elsewhere.join("; ") : errorMessage(error);
  }
  const hint = `The ${itemType} could not be ${outcome}. Fix the highlighted fields.`;
  return elsewhere.length > 0 ? `${hint} ${elsewhere.join("; ")}` : hint;
}
