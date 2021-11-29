import { restApi } from "../axios-instance";

export const authenticate = (email, password) => {
  return restApi
    .post("/users/authenticate", { email, password })
    .then((_) => getAuthenticatedUser());
};

export const signUp = (email, password) => {
  return restApi.post("/users", { email, password });
};

export const logout = () => {
  return restApi.post("/gateway/revoke-token", { token: "dummy" });
};

export const handleTimeout = () => {
  logout().then((_) => {
    window.location.href = "/session-timeout";
  });
};

// export const isUserAuthenticated = () => {
//   let accessToken = localStorage.getItem(ACCESS_TOKEN);
//   if (!accessToken || isTokenExpired(accessToken)) {
//     return false;
//   }
//   return true;
// };

const getAuthenticatedUser = () => {
  return restApi.get(`/users/current`).then((resp) => {
    return { email: resp.data.email, uid: resp.data.id };
  });
};

export const removeAccessToken = () => {
  // Not implemented
};

export const getAccessToken = () => {
  return null;
};

export const onAuthStateChanged = (successCallback, failureCallBack) => {
  getAuthenticatedUser().then(
    (user) => {
      successCallback(user);
    },
    (error) => {
      failureCallBack(error);
    }
  );
};
