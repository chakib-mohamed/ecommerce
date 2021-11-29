import { isAfter, parseISO } from "date-fns";
import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "react-toastify";
import { service } from "../../services";
import { Product, PromotionType } from "../../types/types";

type Props = {
  onPromotionAdded: () => void;
};

export const AddPromotion = ({ onPromotionAdded }: Props) => {
  const {
    register,
    handleSubmit,
    errors,
    reset: resetForm,
    formState,
  } = useForm({
    mode: "all",
  });
  const [displaySuccessMessage, setDisplaySuccessMessage] = useState(false);
  const [products, setProducts] = useState<{ name: string; value: string }[]>(
    []
  );

  useEffect(() => {
    service.fetchProducts().then((products) => {
      let productOptions = products.map((p: Product) => ({
        name: p.title,
        value: p.id,
      }));

      setProducts(productOptions);
    });
  }, []);

  const onSubmitForm = (promotion: PromotionType) => {
    if (isAfter(parseISO(promotion.activeFrom), parseISO(promotion.activeTo))) {
      toast.warn("The active to date must be greater than active from date");
      return;
    }
    if (isAfter(new Date(), parseISO(promotion.activeTo))) {
      toast.warn("The date of end of the promotion must be in the future");
      return;
    }
    service.createPromotion(promotion).then((_) => {
      setDisplaySuccessMessage(true);
      resetForm();
      onPromotionAdded();
    });
  };

  return (
    <React.Fragment>
      {displaySuccessMessage && (
        <div
          className="container alert alert-success alert-dismissible fade show w-50"
          role="alert"
        >
          Promotion has been created successfully
          <button
            type="button"
            className="close"
            data-dismiss="alert"
            aria-label="Close"
            onClick={() => setDisplaySuccessMessage(false)}
          >
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmitForm)} className="container w-50">
        <div className="form-group">
          <label htmlFor="label">Label</label>
          <input
            className={"form-control col-5" + (errors.label ? " error" : "")}
            name="label"
            ref={register({ required: true })}
          />
          {errors.label && (
            <small className="error form-text">This field is required</small>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="product">Product</label>
          <select
            name="product"
            className={"form-control col-5" + (errors.product ? " error" : "")}
            ref={register({ required: true })}
          >
            <option value="">Select a product</option>
            {products &&
              products.map((product) => (
                <option key={product.value} value={product.value}>
                  {product.name}
                </option>
              ))}
          </select>
          {errors.product && (
            <small className="error form-text">This field is required</small>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="activeFrom">Activated from</label>
          <input
            name="activeFrom"
            type="date"
            className={
              "form-control col-5" + (errors.activeFrom ? " error" : "")
            }
            ref={register({ required: true })}
          ></input>
          {errors.activeFrom && (
            <small className="error form-text">This field is required</small>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="activeTo">Activated To</label>
          <input
            name="activeTo"
            type="date"
            className={"form-control col-5" + (errors.activeTo ? " error" : "")}
            ref={register({ required: true })}
          ></input>
          {errors.activeTo && (
            <small className="error form-text">This field is required</small>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="percentageOff">Percentage Off</label>
          <input
            name="percentageOff"
            className={
              "form-control col-3" + (errors.percentageOff ? " error" : "")
            }
            ref={register({ required: true, pattern: /^\d+(\.\d+)?$/ })}
          ></input>
          {errors.percentageOff?.type === "required" && (
            <small className="error form-text">This field is required</small>
          )}
          {errors.percentageOff?.type === "pattern" && (
            <small className="error form-text">
              This field must be a number
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
          Add
        </button>
      </form>
    </React.Fragment>
  );
};

export default AddPromotion;
