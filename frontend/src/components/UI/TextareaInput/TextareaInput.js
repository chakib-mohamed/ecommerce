import React from "react";

function TextareaInput({ name, value, isValid, onChangeHandler }) {
  return (
    <React.Fragment>
      <textarea
        id={name}
        name={name}
        value={value || ""}
        onChange={onChangeHandler}
        className={"form-control" + (!isValid ? " error" : "")}
      ></textarea>
    </React.Fragment>
  );
}

export default React.memo(TextareaInput);
