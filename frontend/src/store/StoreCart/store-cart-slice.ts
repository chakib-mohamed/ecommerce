import { createSlice, PayloadAction } from "@reduxjs/toolkit";

/**
 * Cloud Shop storefront cart — RTK slice modelling the cart (`{ id, qty }`
 * lines keyed by product id) plus the chrome open-state (cart drawer + search
 * overlay). A follow-up reconciles this with the real `/api` order flow.
 */
export interface CartLine {
  id: string;
  qty: number;
}

interface StoreCartState {
  items: CartLine[];
  drawerOpen: boolean;
  searchOpen: boolean;
}

const STORAGE_KEY = "CLOUD_CART";

const loadItems = (): CartLine[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as CartLine[]) : [];
  } catch {
    return [];
  }
};

const initialState: StoreCartState = {
  items: loadItems(),
  drawerOpen: false,
  searchOpen: false,
};

const find = (items: CartLine[], id: string) =>
  items.find((i) => i.id === id);

const storeCartSlice = createSlice({
  name: "storeCart",
  initialState,
  reducers: {
    addToCart: (
      state,
      action: PayloadAction<{ id: string; qty?: number }>
    ) => {
      const { id, qty = 1 } = action.payload;
      const line = find(state.items, id);
      if (line) line.qty += qty;
      else state.items.push({ id, qty });
    },
    setLineQty: (
      state,
      action: PayloadAction<{ id: string; qty: number }>
    ) => {
      const line = find(state.items, action.payload.id);
      if (line) line.qty = Math.max(1, action.payload.qty);
    },
    removeLine: (state, action: PayloadAction<{ id: string }>) => {
      state.items = state.items.filter((i) => i.id !== action.payload.id);
    },
    clearCart: (state) => {
      state.items = [];
    },
    openDrawer: (state) => {
      state.drawerOpen = true;
    },
    closeDrawer: (state) => {
      state.drawerOpen = false;
    },
    openSearch: (state) => {
      state.searchOpen = true;
    },
    closeSearch: (state) => {
      state.searchOpen = false;
    },
  },
});

export const persistStoreCart = (items: CartLine[]) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    /* ignore quota / unavailable storage */
  }
};

export const {
  addToCart,
  setLineQty,
  removeLine,
  clearCart,
  openDrawer,
  closeDrawer,
  openSearch,
  closeSearch,
} = storeCartSlice.actions;

export default storeCartSlice.reducer;
