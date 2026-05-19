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

export type Product = {
  id: string;
  title: string;
  productID: string;
  name: string;
  description: string;
  image: string;
  price: number;
  promotions: PromotionType[];
};

export type OrderCommand = {
  cardNumber: string;
  expirationDate: string;
  validationNumber: string;
  products: { productID: string; qty: number }[];
  userID: string;
};
