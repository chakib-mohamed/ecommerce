import React from "react";

function RadioInput({ name, value, isValid, options, onChangeHandler }) {
  return (
    <React.Fragment>
      {options &&
        options.map((option) => (
          <div key={option.value}>
            <input
              id={option.name}
              name={name}
              type="radio"
              value={option.value}
              checked={value === option.value}
              onChange={onChangeHandler}
              className="form-control"
            />
            <label htmlFor={option.name}>{option.name}</label>
          </div>
        ))}
    </React.Fragment>
  );
}

export default React.memo(RadioInput);
