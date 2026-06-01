import { updateObject } from "../../services/utils";
import * as actions from "./actions";

type LooseAction = { type: string } & Record<string, unknown>;

interface OrdersState {
  orders: unknown[];
  pagesCount: number | null;
  orderID: string | null;
  loading: boolean;
  displayDeleteModalConfirmation: boolean;
}

const initialState: OrdersState = {
  orders: [],
  pagesCount: null,
  orderID: null,
  loading: false,
  displayDeleteModalConfirmation: false,
};

const reducer = (state = initialState, action: LooseAction): OrdersState => {
  switch (action.type) {
    case actions.FETCH_ORDERS_START:
      return updateObject(state, { loading: true });
    case actions.FETCH_ORDERS_SUCCESS:
      return updateObject(state, {
        orders: action.orders as unknown[],
        pagesCount: action.pagesCount as number,
        loading: false,
      });
    case actions.FETCH_ORDERS_FAIL:
      return updateObject(state, { loading: false });

    case actions.OPEN_DELETE_ORDER_MODAL:
      return updateObject(state, {
        displayDeleteModalConfirmation: true,
        orderID: action.orderID as string,
      });
    case actions.CLOSE_DELETE_ORDER_MODAL:
      return updateObject(state, {
        displayDeleteModalConfirmation: false,
      });
    case actions.DELETE_ORDER_SUCCESS:
      return updateObject(state, { displayDeleteModalConfirmation: false });

    case actions.RESET_ORDERS_STATE:
      return { ...initialState };

    default:
      return state;
  }
};

export default reducer;
