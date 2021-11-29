import { auth } from "./firebase-config";

export const authenticate = (email, password) => {
  return auth.signInWithEmailAndPassword(email, password);
};

export const signUp = (email, password) => {
  return auth.createUserWithEmailAndPassword(email, password);
};

export const logout = () => {
  return auth.signOut();
};

export const isUserAuthenticated = () => {
  return auth.currentUser != null;
};
