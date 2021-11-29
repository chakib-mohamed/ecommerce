import React from "react";
import { Redirect } from "react-router-dom";
import { connect } from "react-redux";

const Guard = (WrappedComponent) => {
  const fun = (props) => {
    console.log("current USer", props.currentUser);
    if (props.currentUser === null) {
      return null;
    }

    let component = <WrappedComponent />;
    if (props.currentUser === "anonymous") {
      // component = <Redirect to="/login"></Redirect>;
    }

    return component;
  };

  const mapStateToProps = (state) => {
    return {
      currentUser: state.login.user,
    };
  };

  return connect(mapStateToProps)(fun);
};

export default Guard;
