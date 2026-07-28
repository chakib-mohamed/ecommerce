import { useEffect, useState } from 'react';
import type { Analytics } from './analytics';
import { fetchAnalytics } from './analytics-api';

export interface AnalyticsState {
  data: Analytics | null;
  loading: boolean;
  /** True when the figures could not be loaded; the dashboard says so rather than showing zeros. */
  error: boolean;
}

/** Loads the dashboard's aggregated sales figures once, on mount. */
export const useAnalytics = (): AnalyticsState => {
  const [state, setState] = useState<AnalyticsState>({
    data: null,
    loading: true,
    error: false,
  });

  useEffect(() => {
    let cancelled = false;

    fetchAnalytics()
      .then((data) => {
        if (!cancelled) setState({ data, loading: false, error: false });
      })
      .catch(() => {
        if (!cancelled) setState({ data: null, loading: false, error: true });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return state;
};
