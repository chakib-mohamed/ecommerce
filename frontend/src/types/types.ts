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

export type OrderCommand = {
  cardNumber: string;
  expirationDate: string;
  validationNumber: string;
  products: { productID: string; qty: number }[];
  userID: string;
};
