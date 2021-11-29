import { createSlice } from "@reduxjs/toolkit";
import { authService } from "../../services";
// import { auth } from "../../services/firebase-config";

const INITIAL_STATE = {
  user: null,
  loading: false,
  error: null,
};

const loginSlice = createSlice({
  name: "login",
  initialState: INITIAL_STATE,
  reducers: {
    authenticationStart: (state, action) => {
      state.loading = true;
    },
    authenticationSuccess: (state, action) => {
      state.loading = false;
      state.user = action.payload.user;
    },
    authenticationFailure: (state, action) => {
      state.loading = false;
      state.error = action.payload.error;
      state.user = "anonymous";
    },
    signupStart: (state, action) => {
      state.loading = true;
    },
    signupSuccess: (state, action) => {
      state.loading = false;
      state.user = action.payload.user;
    },
    signupFailure: (state, action) => {
      state.loading = false;
      state.error = action.error;
    },
    logoutSuccess: (state, action) => {
      state.user = action.payload.user;
    },
    initAuthenticatedUser: (state, action) => {
      state.user = action.payload.user;
    },
    initAuthenticatedUserFailure: (state) => {
      state.loading = false;
      state.user = "anonymous";
    },
    resetState: (state) => INITIAL_STATE,
  },
});

export const authenticate = (email, password) => {
  return (dispatch) => {
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
      .catch((error) => {
        dispatch(
          loginSlice.actions.authenticationFailure({ error: error.message })
        );
      });
  };
};

export const signup = (email, password) => {
  return (dispatch) => {
    dispatch(loginSlice.actions.signupStart());
    authService
      .signUp(email, password)
      .then((tokenInfo) => {
        dispatch(loginSlice.actions.signupSuccess({ tokenInfo: tokenInfo }));
      })
      .catch((error) => {
        dispatch(loginSlice.actions.signupFailure({ error: error }));
      });
  };
};

export const logout = () => {
  return (dispatch) => {
    authService.logout().then((_) => {
      dispatch(loginSlice.actions.logoutSuccess({ user: "anonymous" }));
      window.location.href = "/login";
    });
  };
};

export const checkAuthenticationState = () => {
  return (dispatch) => {
    authService.onAuthStateChanged(
      (user) => {
        if (!user) {
          user = "anonymous";
        } else {
          user = { email: user.email, uid: user.uid };
        }
        dispatch(
          loginSlice.actions.authenticationSuccess({
            user: user,
          })
        );
      },
      (error) => dispatch(loginSlice.actions.initAuthenticatedUserFailure())
    );
  };
};

export default loginSlice.reducer;
export const { resetState } = loginSlice.actions;
