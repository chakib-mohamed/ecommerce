import React from "react";

function Select({ name, value, isValid, options, onChangeHandler, className }) {
  return (
    <React.Fragment>
      <select
        id={name}
        name={name}
        value={value}
        onChange={onChangeHandler}
        className={
          "form-control" +
          (!isValid ? " error" : "") +
          (className ? " " + className : "")
        }
      >
        {options &&
          options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.name}
            </option>
          ))}
      </select>
    </React.Fragment>
  );
}

export default React.memo(Select);
