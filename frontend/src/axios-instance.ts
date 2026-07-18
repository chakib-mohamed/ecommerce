import axios, { AxiosError } from "axios";
import { toast } from "react-toastify";
import { authService } from "./services";

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

    if (!isGetCurrentUserRequest(err)) {
      toast.error(message);
    }

    throw err;
  }
);
