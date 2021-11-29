import React from "react";
import { NavLink } from "react-router-dom";
import classes from "./Product.module.css";
import { isAfter, parseISO } from "date-fns";
import { Product, PromotionType } from "../../../types/types";

type Props = {
  product: Product;
};

const productComponent = ({ product }: Props) => {
  const aPromotionIsActive = (promotions: PromotionType[]) => {
    return promotions.some((promotion) => promotionIsActive(promotion));
  };

  const promotionIsActive = (promotion: PromotionType) => {
    const currentDate = new Date();
    return (
      isAfter(currentDate, parseISO(promotion.activeFrom)) &&
      isAfter(parseISO(promotion.activeTo), currentDate)
    );
  };

  return (
    <NavLink to={"product/" + product.productID}>
      <div className={[classes.Product, "card"].join(" ")}>
        <img
          src={"/assets/images/products/" + product.image}
          className={"card-img-top p-2 rounded " + classes.image}
          alt=""
        />
        <div className="card-body">
          <h5 className="card-title">{product.name}</h5>
          <p className="card-text">{product.description}</p>
        </div>
        <div className="card-footer bg-transparent">
          {product.promotions &&
          product.promotions.length > 0 &&
          aPromotionIsActive(product.promotions) ? (
            <React.Fragment>
              <span className="mr-2" style={{ textDecoration: "line-through" }}>
                {product.price}
              </span>
              <span>
                {product.price *
                  (1 -
                    product.promotions
                      .filter((p) => promotionIsActive(p))
                      .map((p) => p.percentageOff / 100)
                      .reduce((x, y) => x + y, 0))}{" "}
                $
              </span>
            </React.Fragment>
          ) : (
            <span>{product.price} $ </span>
          )}
        </div>
      </div>
    </NavLink>
  );
};

export default productComponent;
