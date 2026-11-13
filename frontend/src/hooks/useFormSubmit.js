import { useState } from "react";
import { applyServerErrors } from "../utils/formErrors.js";

/**
 * Submit handler for a resource form: converts the form values with `toRequest`, awaits
 * `onSave` and maps a failure onto the fields (server field errors) and a form-level
 * message. While the promise runs `form.submitting` is true (@mantine/form tracks it).
 */
export function useFormSubmit(form, { onSave, toRequest, itemType }) {
  const [formError, setFormError] = useState(null);

  const handleSubmit = form.onSubmit(async (values) => {
    setFormError(null);
    try {
      await onSave(toRequest(values));
    } catch (error) {
      setFormError(applyServerErrors(form, error, itemType));
    }
  });

  return { formError, handleSubmit };
}
