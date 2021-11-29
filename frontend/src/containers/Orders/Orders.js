import { format } from "date-fns";
import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import Paginator from "../../components/UI/Paginator/Paginator";
import Spinner from "../../components/UI/Spinner/Spinner";
import Guard from "../../hoc/Guard/Guard";
import Modal from "../../hoc/Modal/Modal";
import * as actions from "../../store/Orders/actions";

const Orders = () => {
  let pageSize = 3;

  const orders = useSelector((state) => state.orders.orders);
  const pagesCount = useSelector((state) => state.orders.pagesCount);
  const orderID = useSelector((state) => state.orders.orderID);
  const user = useSelector((state) => state.login.user);
  const loading = useSelector((state) => state.orders.loading);
  const displayDeleteModalConfirmation = useSelector(
    (state) => state.orders.displayDeleteModalConfirmation
  );

  const dispatch = useDispatch();

  useEffect(() => {
    if (user) {
      dispatch(actions.fetchOrders(user.uid, 1, pageSize));
    }
    return () => dispatch(actions.resetState());
  }, [user, pageSize, dispatch]);

  const reloadOrders = (currentPage) => {
    dispatch(actions.fetchOrders(user.uid, currentPage, pageSize));
  };

  return (
    <React.Fragment>
      <div className="container position-relative">
        <Spinner loading={loading}></Spinner>
        <React.Fragment>
          <table className="table table-striped">
            <thead>
              <tr>
                <th scope="col">Products</th>
                <th scope="col">Date</th>
                <th scope="col">Price</th>
                <th scope="col">Status</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders &&
                orders.map((order) => {
                  return (
                    <tr key={order.id}>
                      <td>
                        {order.products.map((p) => (
                          <React.Fragment key={p.productID}>
                            <div>{p.title}</div>
                            <div>qty : {p.qty}</div>
                          </React.Fragment>
                        ))}
                      </td>
                      <td>
                        {format(
                          new Date(order.creationDate),
                          "dd/MM/yyyy HH:mm:ss"
                        )}
                      </td>
                      <td>{order.price}</td>
                      <td>{order.status}</td>
                      <td>
                        {order.status === "INITIATED" ? (
                          <i
                            style={{ cursor: "pointer" }}
                            className="fa fa-trash"
                            onClick={() =>
                              dispatch(actions.openDeleteOrderModal(order.id))
                            }
                          ></i>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
          <Paginator
            pagesCount={pagesCount}
            onPaginate={reloadOrders}
          ></Paginator>
        </React.Fragment>
      </div>

      <Modal
        displayModal={displayDeleteModalConfirmation}
        closeModalHandler={() => dispatch(actions.closeDeleteOrderModal())}
        submitHandler={() => dispatch(actions.deleteOrder(orderID, user.uid))}
        className="w-25"
      >
        Are you sure you want to delete this order ?
      </Modal>
    </React.Fragment>
  );
};

export default Guard(Orders);
