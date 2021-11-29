import React from "react";
import { useForm } from "react-hook-form";
import { OrderCommand } from "../../types/types";

type Props = {
  onCheckout: (orderCommand: OrderCommand) => void;
};

const Checkout = ({ onCheckout }: Props) => {
  const { register, handleSubmit, errors, formState } = useForm({
    mode: "all",
  });

  const onSubmitForm = (orderCommand: OrderCommand) => {
    onCheckout(orderCommand);
  };

  return (
    <React.Fragment>
      <form onSubmit={handleSubmit(onSubmitForm)} className="container">
        <div className="form-group">
          <label htmlFor="cardNumber">Card number</label>
          <input
            className={
              "form-control col-5" + (errors.cardNumber ? " error" : "")
            }
            name="cardNumber"
            ref={register({
              required: true,
              minLength: 16,
              maxLength: 16,
              pattern: /^\[0-9]+$/,
            })}
          />
          {errors.cardNumber && errors.cardNumber.type === "required" && (
            <small className="error form-text">This field is required</small>
          )}
          {errors.cardNumber &&
            (errors.cardNumber.type === "pattern" ||
              errors.cardNumber.type === "minLength" ||
              errors.cardNumber.type === "maxLength") && (
              <small className="error form-text">
                Must be numeric with 16 digits
              </small>
            )}
        </div>

        <div className="form-group">
          <label htmlFor="expirationDate">Expiration Date</label>
          <input
            className={
              "form-control col-5" + (errors.expirationDate ? " error" : "")
            }
            name="expirationDate"
            ref={register({
              required: true,
              minLength: 5,
              maxLength: 7,
              pattern: /^(0[1-9]|1[0-2])\/?([0-9]{4}|[0-9]{2})$/,
            })}
          />
          {errors.expirationDate &&
            errors.expirationDate.type === "required" && (
              <small className="error form-text">This field is required</small>
            )}
          {errors.expirationDate &&
            (errors.expirationDate.type === "pattern" ||
              errors.expirationDate.type === "minLength" ||
              errors.expirationDate.type === "maxLength") && (
              <small className="error form-text">
                Must be of format mm/yy or mm/yyyy
              </small>
            )}
        </div>

        <div className="form-group">
          <label htmlFor="validationNumber">Validation Number</label>
          <input
            className={
              "form-control col-5" + (errors.validationNumber ? " error" : "")
            }
            name="validationNumber"
            ref={register({
              required: true,
              minLength: 3,
              maxLength: 3,
              pattern: /^\[0-9]+$/,
            })}
          />
          {errors.validationNumber &&
            errors.validationNumber.type === "required" && (
              <small className="error form-text">This field is required</small>
            )}
          {errors.validationNumber &&
            (errors.validationNumber.type === "pattern" ||
              errors.validationNumber.type === "minLength" ||
              errors.validationNumber.type === "maxLength") && (
              <small className="error form-text">
                Must be numeric with 3 digits
              </small>
            )}
        </div>

        <button
          type="submit"
          className={
            "btn " +
            (formState.isDirty && formState.isValid
              ? "btn-primary"
              : "btn-secondary")
          }
          disabled={!formState.isDirty || !formState.isValid}
        >
          Submit
        </button>
      </form>
    </React.Fragment>
  );
};

export default Checkout;

// class CheckoutOld extends Component {
//   state = {
//     formModel: {
//       cardNumber: {
//         isValid: false,
//         isTouched: false,
//         value: "",
//         validators: {
//           isRequired: { isValid: false, message: "PAN is required" },
//           isNumeric: { isValid: false, message: "PAN must be numeric" },
//           minLength: {
//             isValid: false,
//             minLength: 16,
//             message: "Validation number is of length 16",
//           },
//           maxLength: {
//             isValid: false,
//             maxLength: 16,
//             message: "Validation number is of length 16",
//           },
//         },
//       },
//       expirationDate: {
//         isValid: false,
//         isTouched: false,
//         value: "",
//         validators: {
//           isRequired: {
//             isValid: false,
//             message: "expiration date is required",
//           },
//           patternMatch: {
//             pattern: /^((0?[1-9]|1[012])[/]?[0-9]{2})*$/,
//             isValid: false,
//             message: "expiration date must respect format MM/yy",
//           },
//         },
//       },
//       validationNumber: {
//         isValid: false,
//         isTouched: false,
//         value: "",
//         validators: {
//           isRequired: {
//             isValid: false,
//             message: "Validation number is required",
//           },
//           isNumeric: {
//             isValid: false,
//             message: "Validation number must be numeric",
//           },
//           minLength: {
//             minLength: 3,
//             isValid: false,
//             message: "Validation number must be of length 3",
//           },
//           maxLength: {
//             maxLength: 3,
//             isValid: false,
//             message: "Validation number must be of length 3",
//           },
//         },
//       },
//     },
//   };

//   handleChange = (e) => {
//     let { formModel, formIsValid } = onInputChange(e, {
//       ...this.state.formModel,
//     });
//     this.setState({ formModel, formIsValid });
//   };

//   onCheckout = (e) => {
//     e.preventDefault();
//     const orderCommand = {};
//     for (let element in this.state.formModel) {
//       orderCommand[element] = this.state.formModel[element].value;
//     }

//     let products = this.props.products.map((p) => {
//       return { productID: p.id, qty: p.qty };
//     });
//     orderCommand.products = products;
//     orderCommand.userID = this.props.user.uid;

//     this.props.onCheckout(orderCommand);
//   };

//   render() {
//     return (
//       <React.Fragment>
//         <form className="container">
//           <div className="form-group">
//             <label htmlFor="cardNumber">Card number</label>
//             <TextInput
//               name="cardNumber"
//               value={this.state.formModel.cardNumber.value}
//               onChangeHandler={this.handleChange}
//               isValid={
//                 !this.state.formModel.cardNumber.isTouched ||
//                 (this.state.formModel.cardNumber.validators.isRequired
//                   .isValid &&
//                   this.state.formModel.cardNumber.validators.isNumeric
//                     .isValid &&
//                   this.state.formModel.cardNumber.validators.minLength
//                     .isValid &&
//                   this.state.formModel.cardNumber.validators.maxLength.isValid)
//               }
//             ></TextInput>
//             <ValidationMessages element={this.state.formModel.cardNumber} />
//           </div>

//           <div className="form-group">
//             <label htmlFor="expirationDate">Expiration Date</label>
//             <TextInput
//               name="expirationDate"
//               value={this.state.formModel.expirationDate.value}
//               onChangeHandler={this.handleChange}
//               isValid={
//                 !this.state.formModel.expirationDate.isTouched ||
//                 (this.state.formModel.expirationDate.validators.isRequired
//                   .isValid &&
//                   this.state.formModel.expirationDate.validators.patternMatch
//                     .isValid)
//               }
//             ></TextInput>
//             <ValidationMessages element={this.state.formModel.expirationDate} />
//           </div>

//           <div className="form-group">
//             <label htmlFor="validationNumber">Validation Number</label>
//             <TextInput
//               name="validationNumber"
//               value={this.state.formModel.validationNumber.value}
//               onChangeHandler={this.handleChange}
//               isValid={
//                 !this.state.formModel.validationNumber.isTouched ||
//                 (this.state.formModel.validationNumber.validators.isRequired
//                   .isValid &&
//                   this.state.formModel.validationNumber.validators.isNumeric
//                     .isValid &&
//                   this.state.formModel.validationNumber.validators.minLength
//                     .isValid &&
//                   this.state.formModel.validationNumber.validators.maxLength
//                     .isValid)
//               }
//             ></TextInput>
//             <ValidationMessages
//               element={this.state.formModel.validationNumber}
//             />
//           </div>

//           <button
//             type="submit"
//             className={
//               "btn f-right " +
//               (this.state.formIsValid ? "btn-primary" : "btn-secondary")
//             }
//             disabled={!this.state.formIsValid}
//             onClick={this.onCheckout}
//           >
//             Submit
//           </button>
//         </form>
//       </React.Fragment>
//     );
//   }
// }

// const mapStateToProps = (state) => {
//   return {
//     products: state.cart.products,
//     isLoading: state.cart.isLoading,
//     displayCheckoutModal: state.cart.displayCheckoutModal,
//     displaySuccessMessage: state.cart.displaySuccessMessage,
//     user: state.login.user,
//   };
// };

// const mapDispatchToProps = (dispatch) => {
//   return {
//     onCheckout: (orderCommand) => dispatch(actions.checkout(orderCommand)),
//   };
// };

// export default connect(mapStateToProps, mapDispatchToProps)(Checkout);
