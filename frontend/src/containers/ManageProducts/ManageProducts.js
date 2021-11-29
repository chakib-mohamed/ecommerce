import React, { useEffect } from "react";
import { connect, useDispatch } from "react-redux";
import * as actions from "../../store/ManageProducts/actions";
import EditProduct from "../../components/EditProduct/EditProduct";
import Modal from "../../hoc/Modal/Modal";
import Guard from "../../hoc/Guard/Guard";

const ManageProducts = (props) => {
  const dispatch = useDispatch();
  useEffect(() => dispatch(actions.fetchProducts()), [dispatch]);

  return (
    <div className="container">
      <table className="table table-striped">
        <thead>
          <tr>
            <th scope="col">Title</th>
            <th scope="col">Category</th>
            <th scope="col">Price</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {console.log(props.products)}
          {props.products.map((product) => {
            return (
              <tr key={product.id}>
                <td>{product.title}</td>
                <td>{product.category}</td>
                <td>{product.price}</td>
                <td>
                  <i
                    style={{ cursor: "pointer" }}
                    className="fa fa-edit mr-2"
                    onClick={() => props.onOpenUpdateProductModal(product.id)}
                  ></i>
                  <i
                    style={{ cursor: "pointer" }}
                    className="fa fa-trash"
                    onClick={() => props.onOpenDeleteProductModal(product.id)}
                  ></i>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {props.displayEditModal ? (
        <Modal
          displayModal={props.displayEditModal}
          closeModalHandler={props.onCloseUpdateProductModal}
          className="w-50"
          disableActionSection
          title=" "
        >
          <EditProduct />
        </Modal>
      ) : null}

      <Modal
        displayModal={props.displayDeleteModalConfirmation}
        closeModalHandler={props.onCloseDeleteProductModal}
        submitHandler={() => props.onDeleteProduct(props.productID)}
        className="w-25"
      >
        Are you sure what are you about to do ?
      </Modal>
    </div>
  );
};

const mapStateToProps = (state) => {
  return {
    products: state.manageProducts.products,
    displayEditModal: state.manageProducts.displayEditModal,
    displayDeleteModalConfirmation:
      state.manageProducts.displayDeleteModalConfirmation,
    productID: state.manageProducts.productID,
  };
};

const mapDispatchToProps = (dispatch) => {
  return {
    onOpenUpdateProductModal: (productID) =>
      dispatch(actions.openUpdateProductModal(productID)),
    onCloseUpdateProductModal: () =>
      dispatch(actions.closeUpdateProductModal()),
    onUpdateProduct: (product) => dispatch(actions.updateProduct(product)),
    onOpenDeleteProductModal: (productID) =>
      dispatch(actions.openDeleteProductModal(productID)),
    onCloseDeleteProductModal: () =>
      dispatch(actions.closeDeleteProductModal()),
    onDeleteProduct: (productID) => dispatch(actions.deleteProduct(productID)),
  };
};

export default Guard(
  connect(mapStateToProps, mapDispatchToProps)(ManageProducts)
);
