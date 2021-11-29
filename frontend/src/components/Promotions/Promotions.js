import React, { useState } from "react";
import Modal from "../../hoc/Modal/Modal";
import { service } from "../../services";

const Promotions = ({ promotions, onPromotionDeleted }) => {
  const [dispalyDeleteModal, setDispalyDeleteModal] = useState(false);
  const [promotionID, setPromotionID] = useState(null);

  const onDeletePromotion = () => {
    service.deletePromotion(promotionID).then((_) => {
      setDispalyDeleteModal(false);
      onPromotionDeleted();
    });
  };

  return (
    <div>
      <div className="container">
        <table className="table table-striped">
          <thead>
            <tr>
              <th scope="col">Label</th>
              <th scope="col">Product</th>
              <th scope="col">Percentage Off</th>
              <th scope="col">Active From</th>
              <th scope="col">To</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {promotions.map((promotion) => {
              console.log(promotion);
              return (
                <tr key={promotion.id}>
                  <td>{promotion.label}</td>
                  <td>{promotion?.product?.title}</td>
                  <td>{promotion.percentageOff}</td>
                  <td>{promotion.activeFrom}</td>
                  <td>{promotion.activeTo}</td>
                  <td>
                    <i
                      style={{ cursor: "pointer" }}
                      className="fa fa-trash"
                      onClick={() => {
                        setPromotionID(promotion.id);
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
          submitHandler={() => onDeletePromotion()}
          className="w-25"
        >
          Are you sure what are you about to do ?
        </Modal>
      </div>
    </div>
  );
};

export default Promotions;
