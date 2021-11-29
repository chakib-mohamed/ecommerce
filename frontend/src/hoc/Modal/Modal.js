import React from "react";

export default function Modal({
  displayModal,
  title,
  className,
  closeModalHandler,
  children,
  submitHandler,
  disableActionSection,
}) {
  return (
    <React.Fragment>
      <div
        className={"modal fade" + (displayModal ? " show d-block" : " d-none")}
        data-backdrop="static"
        tabIndex="-1"
        role="dialog"
        aria-labelledby="staticBackdropLabel"
        aria-hidden={!displayModal}
        aria-modal={displayModal}
      >
        <div
          className={"modal-dialog " + (className ? className : "")}
          style={{ maxWidth: "none" }}
          role="document"
        >
          <div className="modal-content">
            {title ? (
              <div className="modal-header">
                <h5 className="modal-title" id="staticBackdropLabel">
                  {title}
                </h5>
                <button
                  type="button"
                  className="close"
                  data-dismiss="modal"
                  aria-label="Close"
                  onClick={closeModalHandler}
                >
                  <span aria-hidden="true">&times;</span>
                </button>
              </div>
            ) : null}

            <div className="modal-body">{children}</div>

            {disableActionSection ? null : (
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  data-dismiss="modal"
                  onClick={closeModalHandler}
                >
                  Close
                </button>
                <button
                  onClick={submitHandler}
                  type="button"
                  className="btn btn-primary"
                >
                  Submit
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
      <div
        className={
          "modal-backdrop fade" + (displayModal ? " show d-block" : " d-none")
        }
      ></div>
    </React.Fragment>
  );
}
