import { AnyAction } from "redux";
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { service } from "../../services";

type LocalDispatch = ThunkDispatch<unknown, undefined, AnyAction>;

export const FETCH_ORDERS_START = "FETCH_ORDERS_START";
export const FETCH_ORDERS_SUCCESS = "FETCH_ORDERS_SUCCESS";
export const FETCH_ORDERS_FAIL = "FETCH_ORDERS_FAIL";

export const OPEN_DELETE_ORDER_MODAL = "OPEN_DELETE_ORDER_MODAL";
export const CLOSE_DELETE_ORDER_MODAL = "CLOSE_DELETE_ORDER_MODAL";
export const DELETE_ORDER_SUCCESS = "DELETE_ORDER_SUCCESS";
export const RESET_ORDERS_STATE = "RESET_ORDERS_STATE";

export const fetchOrdersStart = () => ({ type: FETCH_ORDERS_START });

export const fetchOrdersSuccess = (pagesCount: number, orders: unknown[]) => ({
  type: FETCH_ORDERS_SUCCESS,
  orders,
  pagesCount,
});

export const fetchOrdersFail = (error: unknown) => ({
  type: FETCH_ORDERS_FAIL,
  error,
});

export const fetchOrders = (userID: string, pageNumber: number, pageSize: number) => {
  return (dispatch: LocalDispatch) => {
    dispatch(fetchOrdersStart());
    service
      .fetchOrders(userID, pageNumber, pageSize)
      .then((result: unknown) => {
        const { x: pageCounts, y: orders } = result as { x: number; y: unknown[] };
        dispatch(fetchOrdersSuccess(pageCounts, orders));
      })
      .catch((error: unknown) => {
        dispatch(fetchOrdersFail(error));
      });
  };
};

export const openDeleteOrderModal = (orderID: string) => ({
  type: OPEN_DELETE_ORDER_MODAL,
  orderID,
});

export const closeDeleteOrderModal = () => ({ type: CLOSE_DELETE_ORDER_MODAL });

const deleteOrderSuccess = () => ({ type: DELETE_ORDER_SUCCESS });

export const deleteOrder = (orderID: string, userID: string) => {
  return (dispatch: LocalDispatch) => {
    service.deleteOrder(orderID).then(() => {
      dispatch(deleteOrderSuccess());
      dispatch(fetchOrders(userID, 1, 3));
    });
  };
};

export const resetState = () => ({ type: RESET_ORDERS_STATE });
