export type PromotionType = {
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
  cardNumber: number;
  expirationDate: string;
  validationNumber: number;
  products: { productID: number; qty: number }[];
  userID: string;
};
