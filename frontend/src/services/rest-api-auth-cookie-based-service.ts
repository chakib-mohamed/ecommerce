import { restApi } from "../axios-instance";

export interface User {
  email: string;
  uid: string;
}

const ACCESS_TOKEN_KEY = "access_token";

export const authenticate = (email: string, password: string): Promise<User> => {
  return restApi
    .post("/users/authenticate", { email, password })
    .then((resp) => {
      const token = resp.data?.access_token as string | undefined;
      if (token) {
        localStorage.setItem(ACCESS_TOKEN_KEY, token);
      }
      return getAuthenticatedUser();
    });
};

export const signUp = (email: string, password: string) => {
  return restApi.post("/users", { email, password });
};

export const logout = (): Promise<unknown> => {
  return restApi
    .post("/gateway/revoke-token", { token: "dummy" })
    .finally(() => removeAccessToken());
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
  localStorage.removeItem(ACCESS_TOKEN_KEY);
};

export const getAccessToken = (): string | null => {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
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
