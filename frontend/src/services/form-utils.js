export const onInputChange = (event, formModel) => {
  event.persist();
  let i = { ...formModel[event.target.name] };
  i.value = i.type === "checkbox" ? event.target.checked : event.target.value;
  i.isTouched = true;
  parseInput(i);
  validateInput(i);
  formModel[event.target.name] = i;
  let formIsValid = true;
  for (let inputIdentifier in formModel) {
    formIsValid = formModel[inputIdentifier].isValid && formIsValid;
  }

  return { formModel: formModel, formIsValid: formIsValid };
};

export const parseInput = (input) =>
  (input.value = input.parseFun ? input.parseFun(input.value) : input.value);

export const validateInput = (input) => {
  let isValid = checkValidity(input.value, input.validators);
  input.isValid = isValid;
};

const checkValidity = (value, rules) => {
  let isInputValid = true;
  if (!rules) {
    return true;
  }

  if (rules.isRequired) {
    const isValid = value.trim() !== "";
    rules.isRequired.isValid = isValid;
    isInputValid = isValid && isInputValid;
  }

  if (rules.minLength) {
    const isValid = value.length >= rules.minLength.minLength;
    rules.minLength.isValid = isValid;
    isInputValid = isValid && isInputValid;
  }

  if (rules.maxLength) {
    const isValid = value.length <= rules.maxLength.maxLength;
    rules.maxLength.isValid = isValid;
    isInputValid = isValid && isInputValid;
  }

  if (rules.isEmail) {
    const pattern = /[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?/;
    const isValid = pattern.test(value);
    rules.isEmail.isValid = isValid;
    isInputValid = isValid && isInputValid;
  }

  if (rules.isNumeric) {
    const pattern = /^\d+$/;
    const isValid = pattern.test(value);
    rules.isNumeric.isValid = isValid;

    isInputValid = isValid && isInputValid;
  }
  if (rules.isDecimal) {
    const pattern = /^\d+(\.\d+)?$/;
    const isValid = pattern.test(value);
    rules.isDecimal.isValid = isValid;

    isInputValid = isValid && isInputValid;
  }
  if (rules.patternMatch) {
    const isValid = rules.patternMatch.pattern.test(value);
    rules.patternMatch.isValid = isValid;

    isInputValid = isValid && isInputValid;
  }

  return isInputValid;
};
