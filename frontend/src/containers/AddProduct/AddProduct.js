import React, { Component } from "react";
import { onInputChange } from "../../services/form-utils";

import TextInput from "../../components/UI/TextInput/TextInput";
import Select from "../../components/UI/Select/Select";
import TextareaInput from "../../components/UI/TextareaInput/TextareaInput";
import ValidationMessages from "../../components/UI/ValidationMessages/ValidationMessages";

import { service } from "../../services";
import Guard from "../../hoc/Guard/Guard";

class AddProduct extends Component {
  state = {
    formModel: {
      title: {
        isValid: false,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: false, message: "Title is required" },
        },
      },
      category: {
        isValid: false,
        isTouched: false,
        value: "",
        options: [{ value: "", name: "None" }],
        validators: {
          isRequired: { isValid: false, message: "Category is required" },
        },
      },
      description: {
        isValid: false,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: false, message: "Description is Required" },
          minLength: {
            minLength: 5,
            isValid: false,
            message: "Description at least 5 char",
          },
          maxLength: {
            maxLength: 100,
            isValid: false,
            message: "Description max 100 char",
          },
        },
      },
      image: {
        isValid: true,
        isTouched: false,
        value: "",
      },
      price: {
        isValid: false,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: false, message: "Price is Required" },
          isDecimal: { isValid: false, message: "Price must be decimal" },
        },
      },
    },
    formIsValid: false,
    displaySuccessMessage: false,
  };

  componentDidMount() {
    service
      .fetchCategories()
      .then((categories) => {
        let formModel = { ...this.state.formModel };
        formModel.category.options = [
          ...formModel.category.options,
          ...categories,
        ];

        this.setState({ formModel: formModel });
      })
      .catch((error) => {
        this.setState({ error: true });
      });
  }

  handleChange = (e) => {
    let { formModel, formIsValid } = onInputChange(e, {
      ...this.state.formModel,
    });
    this.setState({ formModel, formIsValid });
  };

  onSubmitForm = (e) => {
    e.preventDefault();
    const product = {};
    for (let element in this.state.formModel) {
      product[element] = this.state.formModel[element].value;
    }

    service.createProduct(product).then((response) => {
      if (response.status === 201 || response.status === 200) {
        this.setState({ displaySuccessMessage: true, formIsValid: false });
        this.resetForm();
      }
    });
  };

  resetForm = () => {
    let formModel = { ...this.state.formModel };
    Object.keys(formModel).forEach((key) => {
      formModel[key].value = "";
    });
    this.setState({ formModel: formModel });
  };

  render() {
    return (
      <React.Fragment>
        {this.state.displaySuccessMessage ? (
          <div
            className="container alert alert-success alert-dismissible fade show w-50"
            role="alert"
          >
            Product has been created successfully
            <button
              type="button"
              className="close"
              data-dismiss="alert"
              aria-label="Close"
              onClick={() => this.setState({ displaySuccessMessage: false })}
            >
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
        ) : null}

        <form onSubmit={this.onSubmitForm} className="container w-50">
          <div className="form-group">
            <label htmlFor="title">Title</label>
            <TextInput
              name="title"
              value={this.state.formModel.title.value}
              onChangeHandler={this.handleChange}
              isValid={
                !this.state.formModel.title.isTouched ||
                this.state.formModel.title.validators.isRequired.isValid
              }
            ></TextInput>
            <ValidationMessages element={this.state.formModel.title} />
          </div>

          <div className="form-group">
            <label htmlFor="description">Description</label>
            <TextareaInput
              name="description"
              value={this.state.formModel.description.value}
              onChangeHandler={this.handleChange}
              isValid={
                !this.state.formModel.description.isTouched ||
                (this.state.formModel.description.validators.isRequired
                  .isValid &&
                  this.state.formModel.description.validators.minLength
                    .isValid &&
                  this.state.formModel.description.validators.maxLength.isValid)
              }
            ></TextareaInput>
            <ValidationMessages element={this.state.formModel.description} />
          </div>

          <div className="form-group">
            <label htmlFor="category">Category</label>
            <Select
              name="category"
              value={this.state.formModel.category.value}
              options={this.state.formModel.category.options}
              onChangeHandler={this.handleChange}
              className="col-5"
              isValid={
                !this.state.formModel.category.isTouched ||
                this.state.formModel.category.validators.isRequired.isValid
              }
            ></Select>
            <ValidationMessages element={this.state.formModel.category} />
          </div>

          <div className="form-group">
            <label htmlFor="image">Image</label>
            <TextInput
              name="image"
              className="col-5"
              value={this.state.formModel.image.value}
              isValid="true"
              onChangeHandler={this.handleChange}
            ></TextInput>
          </div>

          <div className="form-group">
            <label htmlFor="price">Price</label>
            <TextInput
              name="price"
              value={this.state.formModel.price.value}
              onChangeHandler={this.handleChange}
              className="col-2"
              isValid={
                !this.state.formModel.price.isTouched ||
                (this.state.formModel.price.validators.isRequired.isValid &&
                  this.state.formModel.price.validators.isDecimal.isValid)
              }
            ></TextInput>
            <ValidationMessages element={this.state.formModel.price} />
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
      </React.Fragment>
    );
  }
}

export default Guard(AddProduct);
