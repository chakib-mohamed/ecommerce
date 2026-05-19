import { AnyAction, createSlice, PayloadAction } from "@reduxjs/toolkit";
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { authService, User } from "../../services";

type LocalDispatch = ThunkDispatch<unknown, undefined, AnyAction>;

interface LoginState {
  user: User | "anonymous" | null;
  loading: boolean;
  error: string | null;
}

const INITIAL_STATE: LoginState = {
  user: null,
  loading: false,
  error: null,
};

const loginSlice = createSlice({
  name: "login",
  initialState: INITIAL_STATE,
  reducers: {
    authenticationStart: (state) => {
      state.loading = true;
    },
    authenticationSuccess: (state, action: PayloadAction<{ user: User | "anonymous" }>) => {
      state.loading = false;
      state.user = action.payload.user;
    },
    authenticationFailure: (state, action: PayloadAction<{ error: string }>) => {
      state.loading = false;
      state.error = action.payload.error;
      state.user = "anonymous";
    },
    signupStart: (state) => {
      state.loading = true;
    },
    signupSuccess: (state, action: PayloadAction<{ user: User }>) => {
      state.loading = false;
      state.user = action.payload.user;
    },
    signupFailure: (state, action: PayloadAction<{ error: string }>) => {
      state.loading = false;
      state.error = action.payload.error;
    },
    logoutSuccess: (state, action: PayloadAction<{ user: "anonymous" }>) => {
      state.user = action.payload.user;
    },
    initAuthenticatedUser: (state, action: PayloadAction<{ user: User | "anonymous" }>) => {
      state.user = action.payload.user;
    },
    initAuthenticatedUserFailure: (state) => {
      state.loading = false;
      state.user = "anonymous";
    },
    resetState: () => INITIAL_STATE,
  },
});

export const authenticate = (email: string, password: string) => {
  return (dispatch: LocalDispatch) => {
    dispatch(loginSlice.actions.authenticationStart());
    authService
      .authenticate(email, password)
      .then((user) => {
        dispatch(
          loginSlice.actions.authenticationSuccess({
            user: { email: user.email, uid: user.uid },
          })
        );
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : String(error);
        dispatch(
          loginSlice.actions.authenticationFailure({ error: message })
        );
      });
  };
};

export const signup = (email: string, password: string) => {
  return (dispatch: LocalDispatch) => {
    dispatch(loginSlice.actions.signupStart());
    authService
      .signUp(email, password)
      .then((response) => {
        dispatch(loginSlice.actions.signupSuccess({ user: response as unknown as User }));
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : String(error);
        dispatch(loginSlice.actions.signupFailure({ error: message }));
      });
  };
};

export const logout = () => {
  return (dispatch: LocalDispatch) => {
    authService.logout().then(() => {
      dispatch(loginSlice.actions.logoutSuccess({ user: "anonymous" }));
      window.location.href = "/login";
    });
  };
};

export const checkAuthenticationState = () => {
  return (dispatch: LocalDispatch) => {
    authService.onAuthStateChanged(
      (user) => {
        const mappedUser = user ? { email: user.email, uid: user.uid } : "anonymous";
        dispatch(
          loginSlice.actions.authenticationSuccess({
            user: mappedUser,
          })
        );
      },
      () => dispatch(loginSlice.actions.initAuthenticatedUserFailure())
    );
  };
};

export default loginSlice.reducer;
export const { resetState } = loginSlice.actions;
