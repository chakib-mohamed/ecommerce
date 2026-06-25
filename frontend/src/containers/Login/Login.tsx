import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { useDispatch, useSelector } from "react-redux";
import { Navigate, useNavigate } from "react-router-dom";
import ApiErrorMessage from "../../components/UI/ApiErrorMessage/ApiErrorMessage";
import Button from "../../components/UI/Button/Button";
import { Input } from "../../components/UI/Field/Field";
import { AppDispatch, RootState } from "../../store";
import { authenticate, signup } from "../../store/Login/login-slice";

interface LoginFormInputs {
  name?: string;
  email: string;
  password: string;
}

type Mode = "login" | "signup";

const Login: React.FC = () => {
  const [mode, setMode] = useState<Mode>("login");
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  const { user, error, loading } = useSelector((state: RootState) => state.login);
  const isUserAuthenticated = user && user !== "anonymous";

  const {
    register,
    handleSubmit,
    formState: { isValid },
  } = useForm<LoginFormInputs>({ mode: "onChange" });

  if (isUserAuthenticated) {
    return <Navigate to="/" />;
  }

  const onSubmit = (data: LoginFormInputs) => {
    if (mode === "signup") dispatch(signup(data.email, data.password));
    else dispatch(authenticate(data.email, data.password));
  };

  return (
    <div className="min-h-screen grid place-items-center px-6 py-8 bg-paper">
      <div className="reveal w-full max-w-[400px]">
        <div className="flex justify-center mb-7">
          <div className="flex items-center gap-[9px]">
            <span className="w-7 h-7 rounded-full bg-ink inline-block" />
            <span className="font-serif text-[28px]">Cloud Shop</span>
          </div>
        </div>
        <h1 className="display text-[38px] text-center mb-2">
          {mode === "login" ? "Welcome back" : "Create account"}
        </h1>
        <p className="text-muted text-[15px] text-center mb-6">
          {mode === "login" ? "Log in to your account" : "Join Cloud Shop"}
        </p>

        <div className="flex gap-1 p-1 bg-paper-2 rounded-full mb-[22px]">
          {(["login", "signup"] as Mode[]).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={
                "flex-1 py-[9px] rounded-full border-0 cursor-pointer text-sm font-semibold " +
                (mode === m ? "bg-surface text-ink shadow-card" : "bg-transparent text-muted")
              }
            >
              {m === "login" ? "Log in" : "Sign up"}
            </button>
          ))}
        </div>

        <ApiErrorMessage error={error ? { message: error } : null} />

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3.5">
          {mode === "signup" && (
            <label className="flex flex-col gap-[7px]">
              <span className="text-[13px] font-semibold text-ink-2">Name</span>
              <Input placeholder="Your name" {...register("name")} />
            </label>
          )}
          <label className="flex flex-col gap-[7px]">
            <span className="text-[13px] font-semibold text-ink-2">Email</span>
            <Input
              type="email"
              placeholder="you@email.com"
              {...register("email", {
                required: true,
                pattern: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
              })}
            />
          </label>
          <label className="flex flex-col gap-[7px]">
            <span className="text-[13px] font-semibold text-ink-2">Password</span>
            <Input
              type="password"
              placeholder="••••••••"
              {...register("password", { required: true, minLength: 6 })}
            />
          </label>

          <Button
            type="submit"
            variant="primary"
            size="lg"
            block
            disabled={loading || !isValid}
            className="mt-1"
          >
            {loading ? "Processing…" : mode === "login" ? "Log in" : "Create account"}
          </Button>

          <div className="flex items-center gap-3 text-faint text-[13px] my-1">
            <span className="flex-grow border-t border-line" /> or{" "}
            <span className="flex-grow border-t border-line" />
          </div>

          <Button
            type="button"
            variant="quiet"
            className="mx-auto"
            onClick={() => navigate("/")}
          >
            Browse as guest →
          </Button>
        </form>
      </div>
    </div>
  );
};

export default Login;
