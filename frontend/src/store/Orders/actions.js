import { service } from "../../services";

export const FETCH_ORDERS_START = "FETCH_ORDERS_START";
export const FETCH_ORDERS_SUCCESS = "FETCH_ORDERS_SUCCESS";
export const FETCH_ORDERS_FAIL = "FETCH_ORDERS_FAIL";

export const OPEN_DELETE_ORDER_MODAL = "OPEN_DELETE_ORDER_MODAL";
export const CLOSE_DELETE_ORDER_MODAL = "CLOSE_DELETE_ORDER_MODAL";
export const DELETE_ORDER_SUCCESS = "DELETE_ORDER_SUCCESS";
export const RESET_ORDERS_STATE = "RESET_ORDERS_STATE";

export const fetchOrdersStart = () => {
  return {
    type: FETCH_ORDERS_START,
  };
};

export const fetchOrdersSuccess = (pagesCount, orders) => {
  return {
    type: FETCH_ORDERS_SUCCESS,
    orders: orders,
    pagesCount: pagesCount,
  };
};

export const fetchOrdersFail = (error) => {
  return {
    type: FETCH_ORDERS_FAIL,
    error: error,
  };
};

export const fetchOrders = (userID, pageNumber, pageSize) => {
  return (dispatch) => {
    dispatch(fetchOrdersStart());
    service
      .fetchOrders(userID, pageNumber, pageSize)
      .then(({ x: pageCounts, y: orders }) => {
        dispatch(fetchOrdersSuccess(pageCounts, orders));
      })
      .catch((error) => {
        dispatch(fetchOrdersFail(error));
      });
  };
};

export const openDeleteOrderModal = (orderID) => {
  return {
    type: OPEN_DELETE_ORDER_MODAL,
    orderID: orderID,
  };
};

export const closeDeleteOrderModal = () => {
  return {
    type: CLOSE_DELETE_ORDER_MODAL,
  };
};

const deleteOrderSuccess = () => {
  return { type: DELETE_ORDER_SUCCESS };
};

export const deleteOrder = (orderID, userID) => {
  return (dispatch) => {
    service.deleteOrder(orderID).then((_) => {
      dispatch(deleteOrderSuccess());
      dispatch(fetchOrders(userID, 1, 3));
    });
  };
};

export const resetState = () => {
  return { type: RESET_ORDERS_STATE };
};
