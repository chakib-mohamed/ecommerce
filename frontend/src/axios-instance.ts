import axios, { AxiosError } from "axios";
import { toast } from "react-toastify";
import { authService } from "./services";

declare module "axios" {
  export interface AxiosRequestConfig {
    /**
     * Statuses this call handles itself, so the generic error toast is skipped for them.
     *
     * The error is still thrown - this suppresses the message, not the failure. For a response
     * the caller expects and has something better to say about, the default toast is worse than
     * nothing: it reports a problem beside a screen saying everything worked.
     */
    silentStatuses?: number[];
  }
}

export const restApi = axios.create({
  baseURL: "/api/",
  withCredentials: true,
});

restApi.interceptors.request.use((config) => {
  const token = authService.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const isGetCurrentUserRequest = (error: AxiosError) => {
  return error?.request?.responseURL?.includes("/api/users/current");
};

restApi.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    let message = "An error occurred, response status: ";
    if (err.response) {
      switch (err.response.status) {
        case 401:
          if (authService.getAccessToken()) {
            authService.removeAccessToken();
          }
          if (!isGetCurrentUserRequest(err)) {
            window.location.href = "/login";
          }
          break;

        default:
          message += err.response.status;
          break;
      }
    }

    const handledByCaller =
      err.response != null && (err.config?.silentStatuses ?? []).includes(err.response.status);

    if (!isGetCurrentUserRequest(err) && !handledByCaller) {
      toast.error(message);
    }

    throw err;
  }
);
