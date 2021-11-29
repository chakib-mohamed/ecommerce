import * as React from "react";

function TextInput({ name, type, value, isValid, onChangeHandler, className }) {
  return (
    <React.Fragment>
      <input
        id={name}
        name={name}
        type={type ? type : "text"}
        value={value || ""}
        onChange={onChangeHandler}
        className={
          "form-control" +
          (!isValid ? " error" : "") +
          (className ? " " + className : "")
        }
      />
    </React.Fragment>
  );
}

export default React.memo(TextInput);
