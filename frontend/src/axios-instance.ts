import axios, { AxiosError } from "axios";
import { toast } from "react-toastify";
import { authService } from "./services";

const instance = axios.create({
  baseURL: "https://ecommerce-41f9c.firebaseio.com/",
});

export const restApi = axios.create({
  baseURL: "http://localhost:8080/api/",
  withCredentials: true,
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

export default instance;
