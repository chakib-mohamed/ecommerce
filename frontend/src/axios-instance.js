import axios from "axios";
import { toast } from "react-toastify";
import { authService } from "./services";

const instance = axios.create({
  baseURL: "https://ecommerce-41f9c.firebaseio.com/",
});

export const restApi = axios.create({
  baseURL: "http://localhost:8080/api/",
  withCredentials: true,
});

// restApi.interceptors.request.use((req) => {
//   if (authService.getAccessToken()) {
//     req.headers.authorization = "Bearer " + authService.getAccessToken();
//   }

//   return req;
// });

restApi.interceptors.response.use(
  (res) => res,
  (err) => {
    let message = "An error occured, response status: ";
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

const isGetCurrentUserRequest = (error) => {
  return error?.request?.responseURL?.includes("/api/users/current");
};

export default instance;
