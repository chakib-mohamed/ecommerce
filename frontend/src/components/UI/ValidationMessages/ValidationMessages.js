import React from "react";

const ValidationMessages = (props) => {
  let toRender = null;
  if (props.element.isTouched && props.element.validators) {
    toRender = Object.keys(props.element.validators).map((key, index) => {
      let v = props.element.validators[key];
      if (!v.isValid) {
        return (
          <small key={index} className="error form-text">
            {v.message}
          </small>
        );
      }

      return null;
    });
  }

  return <React.Fragment>{toRender}</React.Fragment>;
};

export default React.memo(ValidationMessages);
