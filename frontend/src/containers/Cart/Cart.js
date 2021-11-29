import React, { Component } from "react";
import * as actions from "../../store/Cart/actions";

import { onInputChange } from "../../services/form-utils";
import { connect } from "react-redux";

import TextInput from "../../components/UI/TextInput/TextInput";
import Checkout from "../../components/Checkout/Checkout";
import Modal from "../../hoc/Modal/Modal";
import ValidationMessages from "../../components/UI/ValidationMessages/ValidationMessages";
import { isAfter, parseISO } from "date-fns";

class Cart extends Component {
  state = {
    formModel: null,
    formIsValid: true,
  };

  componentDidMount() {
    this.props.onLoadProducts();
  }

  componentDidUpdate(prevProps) {
    if (this.props.products !== prevProps.products) {
      if (this.props.products?.length > 0) {
        let formModel = {
          ...this.state.formModel,
        };

        this.props.products.forEach((product) => {
          formModel["quantity" + product.id] = {
            isValid: true,
            isTouched: false,
            value: product.qty,
            productID: product.id,
            validators: {
              isRequired: { isValid: false, message: "QTY is required" },
              isNumeric: { isValid: false, message: "QTY must be numeric" },
            },
          };
        });

        this.setState({
          formModel: formModel,
        });
      }
    }
  }

  componentWillUnmount() {
    this.props.onResetState();
  }

  handleChange = (e) => {
    let { formModel, formIsValid } = onInputChange(e, {
      ...this.state.formModel,
    });
    this.setState({ formModel, formIsValid }, () => {
      if (this.state.formModel[e.target.name].isValid) {
        this.props.onUpdateQty(
          this.state.formModel[e.target.name].productID,
          this.state.formModel[e.target.name].value
        );
      }
    });
  };

  getNbreTotalItems = () => {
    if (this.state.formModel) {
      let totPrice = Object.keys(this.state.formModel)
        .map((qte) => parseInt(this.state.formModel[qte].value))
        .reduce((x, y) => x + y, 0);

      return isNaN(totPrice) ? 0 : totPrice;
    }

    return 0;
  };

  getTotalPrice = () => {
    let sum = this.props.products
      .map(
        (p) =>
          parseFloat(
            p.promotion
              ? p.price * (1 - p.promotion.percentageOff / 100)
              : p.price
          ) * parseInt(this.state.formModel["quantity" + p.id].value)
      )
      .reduce((x, y) => x + y, 0)
      .toFixed(2);

    return isNaN(sum) ? 0 : sum;
  };

  remove = (product) => {
    this.props.onRemoveProductFromCart(product.id);

    let formModel = { ...this.state.formModel };
    delete formModel["quantity" + product.id];
    this.setState({ formModel: formModel });
  };

  onCheckout = (orderCommand) => {
    let products = this.props.products.map((p) => {
      return { productID: p.id, qty: p.qty };
    });
    orderCommand.products = products;
    orderCommand.userID = this.props.user.uid;

    this.props.onCheckout(orderCommand);
  };

  aPromotionIsActive = (promotions) => {
    return promotions.some((promotion) => this.promotionIsActive(promotion));
  };

  promotionIsActive = (promotion) => {
    const currentDate = new Date();
    return (
      isAfter(currentDate, parseISO(promotion.activeFrom)) &&
      isAfter(parseISO(promotion.activeTo), currentDate)
    );
  };

  render() {
    return (
      <React.Fragment>
        {this.props.displaySuccessMessage ? (
          <div
            className="container alert alert-success alert-dismissible fade show w-50"
            role="alert"
          >
            Checkout OK
            <button
              type="button"
              className="close"
              data-dismiss="alert"
              aria-label="Close"
              onClick={this.props.onCloseSuccessMessage}
            >
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
        ) : null}
        {this.props.products?.length > 0 && this.state.formModel ? (
          <div className="card w-75 mx-auto">
            <div className="card-header">Shoping cart</div>
            <ul className="list-group list-group-flush">
              {this.props.products.map((product) => (
                <li key={product.id} className="list-group-item">
                  <div className="row">
                    <div className="col-4">
                      <img
                        src={"/assets/images/products/" + product.image}
                        className={"card-img-top p-2 rounded "}
                        style={{ maxHeight: "200px" }}
                        alt={product.title}
                      />
                    </div>
                    <div className="col-5">
                      <div>
                        <label>Name: </label> {product.title}
                      </div>
                      <div>
                        <label>ISBN: </label> {product.id}
                      </div>
                      <div>
                        <label>Price: </label>
                        {!(
                          product.promotions &&
                          this.aPromotionIsActive(product.promotions)
                        ) ? (
                          <span> {product.price} $</span>
                        ) : (
                          <React.Fragment>
                            <span
                              className="mx-2"
                              style={{ textDecoration: "line-through" }}
                            >
                              {product.price}
                            </span>
                            <span>
                              {product.price *
                                (1 -
                                  product.promotions
                                    .filter((p) => this.promotionIsActive(p))
                                    .map((p) => p.percentageOff / 100)
                                    .reduce((x, y) => x + y, 0))}{" "}
                              $
                            </span>
                          </React.Fragment>
                        )}
                      </div>
                    </div>
                    <div className="col-3">
                      <div>
                        <label>Qty: </label>
                        <TextInput
                          name={"quantity" + product.id}
                          className="col-3 ml-2 d-inline"
                          value={
                            this.state.formModel["quantity" + product.id].value
                          }
                          onChangeHandler={this.handleChange}
                          isValid={
                            !this.state.formModel["quantity" + product.id]
                              .isTouched ||
                            this.state.formModel["quantity" + product.id]
                              .validators.isRequired.isValid
                          }
                        ></TextInput>
                        <ValidationMessages
                          element={
                            this.state.formModel["quantity" + product.id]
                          }
                        />

                        <button
                          className="btn btn-primary ml-2"
                          onClick={() => this.remove(product)}
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
            <div className="card-footer">
              <div>Nbr Items : {this.getNbreTotalItems()}</div>
              <div>Total price : {this.getTotalPrice()}</div>

              {this.props.isUserAuthenticated ? (
                <button
                  className={
                    "btn float-right " +
                    (this.state.formIsValid && this.getNbreTotalItems() > 0
                      ? "btn-primary"
                      : "btn-secondary")
                  }
                  disabled={
                    !this.state.formIsValid || this.getNbreTotalItems() === 0
                  }
                  onClick={this.props.onOpenCheckoutModal}
                >
                  Checkout
                </button>
              ) : null}
            </div>
          </div>
        ) : !this.props.isLoading ? (
          <div
            className="container alert alert-warning alert-dismissible fade show w-50"
            role="alert"
          >
            No product on cart
          </div>
        ) : null}
        {this.props.displayCheckoutModal && (
          <Modal
            displayModal={this.props.displayCheckoutModal}
            closeModalHandler={this.props.onCloseCheckoutModal}
            className="w-50"
            disableActionSection
            title=" "
          >
            <Checkout onCheckout={this.onCheckout} />
          </Modal>
        )}
      </React.Fragment>
    );
  }
}

const mapStateToProps = (state) => {
  return {
    products: state.cart.products,
    isLoading: state.cart.isLoading,
    displayCheckoutModal: state.cart.displayCheckoutModal,
    displaySuccessMessage: state.cart.displaySuccessMessage,
    isUserAuthenticated: state.login.user && state.login.user !== "anonymous",
  };
};

const mapDispatchToProps = (dispatch) => {
  return {
    onLoadProducts: () => dispatch(actions.loadProducts()),
    onRemoveProductFromCart: (productID) =>
      dispatch(actions.removeProductFromCart(productID)),
    onOpenCheckoutModal: () => dispatch(actions.openCheckouModal()),
    onCloseCheckoutModal: () => dispatch(actions.closeCheckouModal()),
    onResetState: () => dispatch(actions.resetState()),
    onCloseSuccessMessage: () => dispatch(actions.closeSuccessMessage()),
    onUpdateQty: (productID, qty) =>
      dispatch(actions.updateQty(productID, qty)),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(Cart);
