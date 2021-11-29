import React, { Component } from "react";
import ValidationMessages from "../UI/ValidationMessages/ValidationMessages";
import TextInput from "../UI/TextInput/TextInput";
import Select from "../UI/Select/Select";
import TextareaInput from "../UI/TextareaInput/TextareaInput";
import { onInputChange } from "../../services/form-utils";

import { connect } from "react-redux";
import * as actions from "../../store/ManageProducts/actions";

class EditProduct extends Component {
  state = {
    formModel: {
      id: {
        value: "",
        isValid: true,
      },
      title: {
        isValid: true,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: true, message: "Title is required" },
        },
      },
      category: {
        isValid: true,
        isTouched: false,
        value: "",
        options: [{ value: "", name: "None" }],
        validators: {
          isRequired: { isValid: true, message: "Category is required" },
        },
      },
      description: {
        isValid: true,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: true, message: "Description is Required" },
          minLength: {
            minLength: 5,
            isValid: true,
            message: "Description at least 5 char",
          },
          maxLength: {
            maxLength: 100,
            isValid: true,
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
        isValid: true,
        isTouched: false,
        value: "",
        validators: {
          isRequired: { isValid: true, message: "Price is Required" },
          isDecimal: { isValid: true, message: "Price must be decimal" },
        },
      },
    },
    formIsValid: true,
  };

  componentDidMount() {
    this.props.onFetchCategories();
  }

  componentDidUpdate(prevProps) {
    if (this.props.product !== prevProps.product) {
      let formModel = { ...this.state.formModel };
      let product = this.props.product;
      formModel.id.value = this.props.productID;
      formModel.title.value = product.title;
      formModel.description.value = product.description;
      formModel.price.value = product.price;
      formModel.image.value = product.image;
      formModel.category.value = product.category;
      this.setState({ formModel: formModel });
    }

    if (
      this.state.formModel.category.options.length === 1 &&
      this.props.categories !== null
    ) {
      let formModel = { ...this.state.formModel };
      console.log("categories", this.props.categories);
      let options = [...formModel.category.options, ...this.props.categories];

      // let formModel = { ...this.state.formModel };
      //   formModel.category.options = [
      //     ...formModel.category.options,
      //     ...categories,
      //   ];

      //   this.setState({ formModel: formModel });

      // Object.keys(this.props.categories).forEach((key) => {
      //   options = [
      //     ...options,
      //     {
      //       value: key,
      //       name: this.props.categories[key],
      //     },
      //   ];
      // });
      formModel.category.options = options;

      this.setState({ formModel: formModel });
    }
  }

  onEditProduct = (e) => {
    e.preventDefault();
    const product = {};
    for (let element in this.state.formModel) {
      product[element] = this.state.formModel[element].value;
    }

    this.props.onUpdateProduct(product);
  };

  handleChange = (e) => {
    let { formModel, formIsValid } = onInputChange(e, {
      ...this.state.formModel,
    });
    this.setState({ formModel, formIsValid });
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
        <form className="container">
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
              "btn f-right " +
              (this.state.formIsValid ? "btn-primary" : "btn-secondary")
            }
            disabled={!this.state.formIsValid}
            onClick={this.onEditProduct}
          >
            Submit
          </button>
        </form>
      </React.Fragment>
    );
  }
}

const mapStateToProps = (state) => {
  return {
    productID: state.manageProducts.productID,
    product: state.manageProducts.product,
    categories: state.manageProducts.categories,
  };
};

const mapDispatchToProps = (dispatch) => {
  return {
    onFetchCategories: () => dispatch(actions.fetchCategories()),
    onUpdateProduct: (product) => dispatch(actions.updateProduct(product)),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(EditProduct);
