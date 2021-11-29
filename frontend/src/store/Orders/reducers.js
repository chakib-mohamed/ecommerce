import * as actions from "./actions";
import { updateObject } from "../../services/utils";

const initialState = {
  orders: [],
  pagesCount: null,
  orderID: null,
  loading: false,
  displayDeleteModalConfirmation: false,
};

const fetchOrdersStart = (state) => {
  return updateObject(state, { loading: true });
};

const fetchOrdersSuccess = (state, action) => {
  return updateObject(state, {
    orders: action.orders,
    pagesCount: action.pagesCount,
    loading: false,
  });
};

const fetchOrdersFail = (state, action) => {
  return updateObject(state, { loading: false });
};

const openDeleteOrderModal = (state, action) => {
  return updateObject(state, {
    displayDeleteModalConfirmation: true,
    orderID: action.orderID,
  });
};

const closeDeleteOrderModal = (state, action) => {
  return updateObject(state, {
    displayDeleteModalConfirmation: false,
  });
};

const deleteOrderSuccess = (state, action) => {
  return updateObject(state, { displayDeleteModalConfirmation: false });
};

const reducer = (state = initialState, action) => {
  switch (action.type) {
    case actions.FETCH_ORDERS_START:
      return fetchOrdersStart(state, action);
    case actions.FETCH_ORDERS_SUCCESS:
      return fetchOrdersSuccess(state, action);
    case actions.FETCH_ORDERS_FAIL:
      return fetchOrdersFail(state, action);

    case actions.OPEN_DELETE_ORDER_MODAL:
      return openDeleteOrderModal(state, action);
    case actions.CLOSE_DELETE_ORDER_MODAL:
      return closeDeleteOrderModal(state, action);
    case actions.DELETE_ORDER_SUCCESS:
      return deleteOrderSuccess(state, action);

    case actions.RESET_ORDERS_STATE:
      return { ...initialState };

    default:
      return state;
  }
};

export default reducer;
