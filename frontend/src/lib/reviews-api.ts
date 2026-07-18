/**
 * Product reviews — GET is public, POST/DELETE require the caller to be signed in (and, for
 * submission, to have a qualifying order for the product; the backend enforces that and returns
 * a FUNCTIONAL 403 otherwise, surfaced like any other API error via the axios interceptor).
 */
import { restApi } from '../axios-instance';

export interface Review {
  id: string;
  product_id: string;
  reviewer: string;
  stars: number;
  text?: string;
  created_at: string;
}

export interface ReviewSubmission {
  product_id: string;
  stars: number;
  text?: string;
}

export const fetchReviews = (productId: string): Promise<Review[]> =>
  restApi.get('/reviews', { params: { product_id: productId } }).then((res) => res.data ?? []);

export const submitReview = (submission: ReviewSubmission): Promise<Review> =>
  restApi.post('/reviews', submission).then((res) => res.data);

export const deleteReview = (reviewId: string): Promise<void> =>
  restApi.delete(`/reviews/${reviewId}`).then(() => undefined);
