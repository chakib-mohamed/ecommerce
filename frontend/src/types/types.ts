export type Category = {
  value: string;
  name: string;
};

export type PromotionType = {
  label: string;
  product: string;
  percentageOff: number;
  activeFrom: string;
  activeTo: string;
};

// Shape returned by the promotions GET endpoint (richer than the create payload).
export type Promotion = {
  id: string;
  label: string;
  product?: {
    title: string;
  };
  percentageOff: number | string;
  activeFrom: string;
  activeTo: string;
};

export type Product = {
  id: string;
  title: string;
  productID: string;
  name: string;
  description: string;
  image: string;
  price: number;
  category?: string;
  promotions: PromotionType[];
};

/** One line of a placed order. Field names are the wire shape. */
export type OrderLine = {
  product_id: string;
  title: string;
  qty: number;
  price: number;
  percentage_off?: number;
};

/** Create payload for an order. The buyer is derived from the session, so no
 *  user or card fields are sent. */
export type OrderCommand = {
  products: OrderLine[];
};
