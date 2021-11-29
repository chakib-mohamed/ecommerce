import React, { Component } from "react";

import { service } from "../../services";

import { parseInput, validateInput } from "../../services/form-utils";
import TextInput from "../../components/UI/TextInput/TextInput";

import ValidationMessages from "../../components/UI/ValidationMessages/ValidationMessages";

export default class ProductDetails extends Component {
  state = {
    product: null,
    isProductInStock: true,
    formIsValid: false,
    formModel: {
      quantity: {
        isValid: false,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: false, message: "QTY is required" },
          isNumeric: { isValid: false, message: "QTY must be numeric" },
        },
      },
    },
  };

  componentDidMount() {
    let productID = this.props.match.params.id;

    if (productID) {
      service.getProduct(productID).then((product) => {
        this.setState({ product: product });
      });
    }
  }

  handleChange = (e) => {
    e.persist();
    let formModel = { ...this.state.formModel };
    let i = formModel[e.target.name];
    i.value = i.type === "checkbox" ? e.target.checked : e.target.value;
    i.isTouched = true;
    parseInput(i);
    validateInput(i);
    let formIsValid = true;
    for (let inputIdentifier in formModel) {
      formIsValid = formModel[inputIdentifier].isValid && formIsValid;
    }
    this.setState({ formModel, formIsValid });
  };

  addToCart = (e) => {
    e.preventDefault();
    if (localStorage.getItem("CART")) {
      let cart = JSON.parse(localStorage.getItem("CART"));
      if (cart[this.state.product.id]) {
        cart[this.state.product.id] =
          parseInt(cart[this.state.product.id]) +
          parseInt(this.state.formModel.quantity.value);
      } else {
        cart[this.state.product.id] = this.state.formModel.quantity.value;
      }

      localStorage.setItem("CART", JSON.stringify(cart));
    } else {
      let product = {};
      product[this.state.product.id] = this.state.formModel.quantity.value;
      localStorage.setItem("CART", JSON.stringify(product));
    }
    this.props.history.push("/cart");
  };

  render() {
    return (
      <React.Fragment>
        {this.state.product != null ? (
          <div className="container">
            <div className="row">
              <div className="col-4">
                <div className="col-12">
                  <div>
                    <img
                      src={
                        "/assets/images/products/" + this.state.product.image
                      }
                      className={"card-img-top p-2 rounded "}
                      style={{ maxHeight: "200px" }}
                      alt={this.state.product.title}
                    />
                  </div>
                </div>
              </div>

              <div className="col-8">
                <div className="card">
                  <div className="card-header">Details</div>
                  <ul className="list-group list-group-flush">
                    <li className="list-group-item">
                      <p>{this.state.product.description}</p>
                    </li>
                    <li className="list-group-item">
                      Price: {this.state.product.price}
                    </li>
                    <li className="list-group-item">
                      ISBN: {this.state.product.id}
                    </li>
                    <li className="list-group-item">
                      <form onSubmit={this.addToCart}>
                        <div className="form-inline">
                          <div className="form-group" style={{ width: "15%" }}>
                            <TextInput
                              name="quantity"
                              className="form-control col-11"
                              value={this.state.formModel.quantity.value}
                              onChangeHandler={this.handleChange}
                              isValid={
                                !this.state.formModel.quantity.isTouched ||
                                this.state.formModel.quantity.validators
                                  .isRequired.isValid
                              }
                            ></TextInput>
                            <ValidationMessages
                              element={this.state.formModel.quantity}
                            />
                          </div>
                          <button
                            className={
                              "btn " +
                              (this.state.formIsValid
                                ? "btn-primary"
                                : "btn-secondary")
                            }
                            disabled={!this.state.formIsValid}
                            type="submit"
                          >
                            Add to Cart
                          </button>
                        </div>
                      </form>
                    </li>
                    {!this.state.isProductInStock ? (
                      <div>
                        <div>Out of stock</div>
                      </div>
                    ) : null}
                  </ul>
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </React.Fragment>
    );
  }
}
