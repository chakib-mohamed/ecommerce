import { restApi } from "../axios-instance";

export interface User {
  email: string;
  uid: string;
}

export const authenticate = (email: string, password: string): Promise<User> => {
  return restApi
    .post("/users/authenticate", { email, password })
    .then((_) => getAuthenticatedUser());
};

export const signUp = (email: string, password: string) => {
  return restApi.post("/users", { email, password });
};

export const logout = (): Promise<unknown> => {
  return restApi.post("/gateway/revoke-token", { token: "dummy" });
};

export const handleTimeout = () => {
  logout().then((_) => {
    window.location.href = "/session-timeout";
  });
};

const getAuthenticatedUser = (): Promise<User> => {
  return restApi.get(`/users/current`).then((resp) => {
    return { email: resp.data.email, uid: resp.data.id };
  });
};

export const removeAccessToken = () => {
  // Not implemented
};

export const getAccessToken = (): string | null => {
  return null;
};

export const onAuthStateChanged = (
  successCallback: (user: User) => void, 
  failureCallBack: (error: unknown) => void
) => {
  getAuthenticatedUser().then(
    (user) => {
      successCallback(user);
    },
    (error) => {
      failureCallBack(error);
    }
  );
};
