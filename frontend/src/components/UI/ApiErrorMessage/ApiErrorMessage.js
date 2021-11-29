import React, { useEffect, useState } from "react";

const ApiErrorMessage = ({ error }) => {
  const [display, setDisplay] = useState(true);
  const [message, setMessage] = useState();

  useEffect(() => {
    if (error) {
      switch (error.code) {
        case "auth/user-not-found":
        case "auth/wrong-password":
          setMessage("Login/Password incorrect");
          break;

        case "auth/email-already-in-use":
          setMessage(error.message);
          break;
        default:
          setMessage(error.message);
      }
    }
  }, [error]);

  const dismiss = () => {
    setDisplay(false);
  };

  return (
    <React.Fragment>
      {message && display ? (
        <div
          className="container alert alert-danger alert-dismissible fade show"
          role="alert"
        >
          {message}
          <button
            type="button"
            className="close"
            data-dismiss="alert"
            aria-label="Close"
            onClick={dismiss}
          >
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
      ) : null}
    </React.Fragment>
  );
};

export default React.memo(ApiErrorMessage);
