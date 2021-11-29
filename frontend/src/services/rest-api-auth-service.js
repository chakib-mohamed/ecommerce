import { restApi } from "../axios-instance";
import jwt from "jwt-decode";

const ACCESS_TOKEN = "accessToken";

export const authenticate = (email, password) => {
  return restApi
    .post("/users/authenticate", { email, password })
    .then((response) => {
      localStorage.setItem(ACCESS_TOKEN, response.data.accessToken);
    })
    .then((_) => getAuthenticatedUser());
};

export const signUp = (email, password) => {
  return restApi.post("/users", { email, password });
};

export const logout = () => {
  restApi
    .post("/gateway/revoke-token", { token: getAccessToken() })
    .finally((_) => removeAccessToken());

  return new Promise((resolve) => resolve());
};

export const removeAccessToken = () => {
  localStorage.removeItem(ACCESS_TOKEN);
};

export const isUserAuthenticated = () => {
  let accessToken = localStorage.getItem(ACCESS_TOKEN);
  if (!accessToken || isTokenExpired(accessToken)) {
    return false;
  }
  return true;
};

const isTokenExpired = (accessToken) => {
  let decodedToken = jwt(accessToken);
  return Date.now() >= decodedToken.exp * 1000;
};

const getAuthenticatedUser = () => {
  let accessToken = localStorage.getItem(ACCESS_TOKEN);
  if (!accessToken || isTokenExpired(accessToken)) {
    return new Promise((resolve) => resolve(null));
  }

  let decodedToken = jwt(accessToken);

  return restApi.get(`/users/${decodedToken.sub}`).then((resp) => {
    return { email: resp.data.email, uid: resp.data.id };
  });
};

export const getAccessToken = () => {
  return localStorage.getItem(ACCESS_TOKEN);
};

export const onAuthStateChanged = (successCallback) => {
  getAuthenticatedUser().then(
    (user) => {
      successCallback(user);
    },
    (error) => {
      console.log(error);
    }
  );
};
