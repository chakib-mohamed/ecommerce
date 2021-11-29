import React, { Component } from "react";
import { onInputChange } from "../../services/form-utils";
import TextInput from "../../components/UI/TextInput/TextInput";
import ValidationMessages from "../../components/UI/ValidationMessages/ValidationMessages";
import { connect } from "react-redux";
import Spinner from "../../components/UI/Spinner/Spinner";
import ApiErrorMessage from "../../components/UI/ApiErrorMessage/ApiErrorMessage";
import CheckboxInput from "../../components/UI/CheckboxInput/CheckboxInput";
import { Redirect } from "react-router-dom";

import {
  authenticate,
  signup,
  resetState,
} from "../../store/Login/login-slice";

class Login extends Component {
  state = {
    formModel: {
      email: {
        isValid: false,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: false, message: "Login is required" },
        },
      },
      password: {
        isValid: false,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: false, message: "Password is required" },
        },
      },
      isSignUp: {
        type: "checkbox",
        isValid: true,
        isTouched: false,
        value: false,
      },
    },
    formIsValid: false,
  };

  handleChange = (e) => {
    let { formModel, formIsValid } = onInputChange(e, {
      ...this.state.formModel,
    });
    this.setState({ formModel, formIsValid });
  };

  onSubmitForm = (e) => {
    e.preventDefault();
    if (this.state.formModel.isSignUp.value) {
      this.props.onSignUp(
        this.state.formModel.email.value,
        this.state.formModel.password.value
      );
    } else {
      this.props.onLogin(
        this.state.formModel.email.value,
        this.state.formModel.password.value
      );
    }
  };

  componentWillUnmount() {
    // this.props.onClear();
  }

  render() {
    if (this.props.isUserAuthenticated) {
      return <Redirect to="/" />;
    }

    return (
      <div className="container w-50 position-relative">
        <ApiErrorMessage error={this.props.error}></ApiErrorMessage>

        <form onSubmit={this.onSubmitForm}>
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <TextInput
              name="email"
              className="col-9"
              value={this.state.formModel.email.value}
              onChangeHandler={this.handleChange}
              isValid={
                !this.state.formModel.email.isTouched ||
                this.state.formModel.email.validators.isRequired.isValid
              }
            ></TextInput>
            <ValidationMessages element={this.state.formModel.email} />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <TextInput
              name="password"
              type="password"
              className="col-9"
              value={this.state.formModel.password.value}
              onChangeHandler={this.handleChange}
              isValid={
                !this.state.formModel.password.isTouched ||
                this.state.formModel.password.validators.isRequired.isValid
              }
            ></TextInput>
            <ValidationMessages element={this.state.formModel.password} />
          </div>

          <div className="form-group form-check">
            <CheckboxInput
              name="isSignUp"
              className="form-check-input"
              value={this.state.formModel.isSignUp.value}
              onChangeHandler={this.handleChange}
            ></CheckboxInput>
            <label className="form-check-label" htmlFor="isSignUp">
              Switch to Sign up mode ?
            </label>
          </div>

          <button
            type="submit"
            className={
              "btn " +
              (this.state.formIsValid ? "btn-primary" : "btn-secondary")
            }
            disabled={!this.state.formIsValid}
          >
            Submit
          </button>
        </form>

        <Spinner loading={this.props.loading}></Spinner>
      </div>
    );
  }
}

const mapStateToProps = (state) => ({
  user: state.login.user,
  isUserAuthenticated: state.login.user && state.login.user !== "anonymous",
  error: state.login.error,
  loading: state.login.loading,
});

const mapDispatchToProps = (dispatch) => {
  return {
    onLogin: (email, password) => dispatch(authenticate(email, password)),
    onSignUp: (email, password) => dispatch(signup(email, password)),
    onClear: () => dispatch(resetState()),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(Login);
