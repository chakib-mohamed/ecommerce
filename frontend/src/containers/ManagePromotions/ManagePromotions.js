import React, { useEffect, useState } from "react";
import AddPromotion from "../../components/AddPromotion/AddPromotion";
import Promotions from "../../components/Promotions/Promotions";
import Guard from "../../hoc/Guard/Guard";
import { service } from "../../services";

export const ManagePromotions = () => {
  const [promotions, setPromotions] = useState(null);

  useEffect(() => {
    fetchPromotions();
  }, []);

  const reloadPromotions = () => {
    fetchPromotions();
  };

  const fetchPromotions = () => {
    service.fetchPromotions().then((promotions) => {
      setPromotions(promotions);
    });
  };

  return (
    <React.Fragment>
      <h4 className="container w-50">Add a new promotion :</h4>
      <AddPromotion onPromotionAdded={reloadPromotions} />

      <h4 className="container w-50 mt-4">List of promotions:</h4>
      {promotions && (
        <Promotions
          promotions={promotions}
          onPromotionDeleted={reloadPromotions}
        />
      )}
    </React.Fragment>
  );
};

export default Guard(ManagePromotions);
