import React, { useState } from "react";
import { onInputChange } from "../../services/form-utils";
import { service } from "../../services";

import TextInput from "../../components/UI/TextInput/TextInput";
import ValidationMessages from "../../components/UI/ValidationMessages/ValidationMessages";

export const AddCategory = ({ onCategoryAdded }) => {
  const [formModel, setFormModel] = useState({
    label: {
      isValid: false,
      isTouched: false,
      value: "",
      validators: {
        isRequired: { isValid: false, message: "Label is required" },
      },
    },
  });
  const [formIsValid, setFormIsValid] = useState(false);
  const [displaySuccessMessage, setDisplaySuccessMessage] = useState(false);

  const handleChange = (e) => {
    let { formModel: newFormModel, formIsValid } = onInputChange(e, {
      ...formModel,
    });
    setFormIsValid(formIsValid);
    setFormModel(newFormModel);
  };

  const onSubmitForm = (e) => {
    e.preventDefault();
    const category = {};
    for (let element in formModel) {
      category[element] = formModel[element].value;
    }

    service.createCategory(category).then((_) => {
      setDisplaySuccessMessage(true);
      resetForm();
      onCategoryAdded();
    });
  };

  const resetForm = () => {
    let resetFormModel = { ...formModel };
    Object.keys(resetFormModel).forEach((key) => {
      resetFormModel[key].value = "";
    });
    setFormModel(resetFormModel);
    setFormIsValid(false);
  };

  return (
    <React.Fragment>
      {displaySuccessMessage && (
        <div
          className="container alert alert-success alert-dismissible fade show w-50"
          role="alert"
        >
          Category has been created successfully
          <button
            type="button"
            className="close"
            data-dismiss="alert"
            aria-label="Close"
            onClick={() => setDisplaySuccessMessage(false)}
          >
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
      )}

      <form onSubmit={onSubmitForm} className="container w-50">
        <div className="form-group">
          <label htmlFor="label">Label</label>
          <TextInput
            name="label"
            value={formModel.label.value}
            onChangeHandler={handleChange}
            className="col-5"
            isValid={
              !formModel.label.isTouched ||
              formModel.label.validators.isRequired.isValid
            }
          ></TextInput>
          <ValidationMessages element={formModel.label} />
        </div>

        <button
          type="submit"
          className={"btn " + (formIsValid ? "btn-primary" : "btn-secondary")}
          disabled={!formIsValid}
        >
          Add
        </button>
      </form>
    </React.Fragment>
  );
};

export default AddCategory;
