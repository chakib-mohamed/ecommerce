import React from "react";
import { connect } from "react-redux";
import { RootState } from "../../store";
import { User } from "../../services";

type GuardedProps = Record<string, unknown> & {
  currentUser: User | "anonymous" | null;
};

const Guard = (WrappedComponent: React.ComponentType<Record<string, unknown>>) => {
  const fun = (props: GuardedProps) => {
    console.log("current USer", props.currentUser);
    if (props.currentUser === null) {
      return null;
    }

    const component = <WrappedComponent {...props} />;
    if (props.currentUser === "anonymous") {
      // component = <Redirect to="/login"></Redirect>;
    }

    return component;
  };

  const mapStateToProps = (state: RootState) => {
    return {
      currentUser: state.login.user,
    };
  };

  return connect(mapStateToProps)(fun);
};

export default Guard;
