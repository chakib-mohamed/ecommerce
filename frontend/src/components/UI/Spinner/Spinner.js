import React from "react";
import classes from "./Spinner.module.css";

const Spinner = ({ loading, className }) => {
  return (
    <React.Fragment>
      {loading ? (
        <div className={classes.spinner + (className ? className : "")}>
          <div className={classes.loading}>
            <div className="spinner-border text-primary" role="status">
              <span className="sr-only">Loading...</span>
            </div>
          </div>
          <div
            style={{ opacity: 0.25 }}
            className="modal-backdrop show d-block position-absolute w-100 h-100"
          ></div>
        </div>
      ) : null}
    </React.Fragment>
  );
};

export default React.memo(Spinner);
