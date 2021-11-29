import React from "react";

const CheckboxInput = ({
  name,
  value,
  isValid,
  onChangeHandler,
  className,
}) => {
  return (
    <React.Fragment>
      <input
        id={name}
        name={name}
        type="checkbox"
        onChange={onChangeHandler}
        className={className}
      />
    </React.Fragment>
  );
};

export default React.memo(CheckboxInput);
