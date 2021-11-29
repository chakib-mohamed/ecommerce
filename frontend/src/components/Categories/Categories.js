import React, { useState } from "react";
import Modal from "../../hoc/Modal/Modal";
import { service } from "../../services";

const Categories = ({ categories, onCategoryDeleted }) => {
  const [dispalyDeleteModal, setDispalyDeleteModal] = useState(false);
  const [categoryID, setCategoryID] = useState(null);

  const onDeleteCategory = () => {
    service.deleteCategory(categoryID).then((_) => {
      setDispalyDeleteModal(false);
      onCategoryDeleted();
    });
  };

  return (
    <div>
      <div className="container">
        <table className="table table-striped">
          <thead>
            <tr>
              <th scope="col">Label</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => {
              return (
                <tr key={category.id}>
                  <td>{category.label}</td>
                  <td>
                    <i
                      style={{ cursor: "pointer" }}
                      className="fa fa-trash"
                      onClick={() => {
                        setCategoryID(category.id);
                        setDispalyDeleteModal(true);
                      }}
                    ></i>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <Modal
          displayModal={dispalyDeleteModal}
          closeModalHandler={() => setDispalyDeleteModal(false)}
          submitHandler={() => onDeleteCategory()}
          className="w-25"
        >
          Are you sure what are you about to do ?
        </Modal>
      </div>
    </div>
  );
};

export default Categories;
