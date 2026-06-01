import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { useDispatch, useSelector } from "react-redux";
import { Navigate } from "react-router-dom";
import ApiErrorMessage from "../../components/UI/ApiErrorMessage/ApiErrorMessage";
import { AppDispatch, RootState } from "../../store";
import { authenticate, signup } from "../../store/Login/login-slice";

interface LoginFormInputs {
  email: string;
  password: string;
}

const Login: React.FC = () => {
  const [isSignUp, setIsSignUp] = useState(false);
  const dispatch = useDispatch<AppDispatch>();

  const { user, error, loading } = useSelector((state: RootState) => state.login);
  const isUserAuthenticated = user && user !== "anonymous";

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<LoginFormInputs>({
    mode: "onBlur",
  });

  if (isUserAuthenticated) {
    return <Navigate to="/" />;
  }

  const onSubmit = (data: LoginFormInputs) => {
    if (isSignUp) {
      dispatch(signup(data.email, data.password));
    } else {
      dispatch(authenticate(data.email, data.password));
    }
  };

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4 py-12 sm:px-6 lg:px-8 bg-slate-50/50">
      <div className="max-w-md w-full space-y-8 relative">
        {/* Background Decorative Element */}
        <div className="absolute -top-20 -left-20 w-64 h-64 bg-blue-100 rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob"></div>
        <div className="absolute -bottom-20 -right-20 w-64 h-64 bg-indigo-100 rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob animation-delay-2000"></div>

        <div className="relative bg-white/80 backdrop-blur-2xl p-10 rounded-[2.5rem] shadow-2xl border border-white/20 transition-all duration-500 hover:shadow-blue-500/10 hover:border-blue-500/20">
          <div className="text-center space-y-2 mb-10">
            <div className="inline-flex items-center justify-center p-4 bg-gradient-to-tr from-blue-600 to-indigo-600 rounded-3xl shadow-xl shadow-blue-200 mb-4 transform transition-transform hover:scale-110 active:scale-95 duration-300 cursor-pointer">
              <i className="fa fa-cloud text-3xl text-white"></i>
            </div>
            <h2 className="text-3xl font-black text-slate-900 tracking-tight">
              {isSignUp ? "Join Cloud Shop" : "Welcome Back"}
            </h2>
            <p className="text-slate-500 font-medium">
              {isSignUp ? "Create an account to start shopping" : "Please enter your details to sign in"}
            </p>
          </div>

          <ApiErrorMessage error={error ? { message: error } : null} />

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div className="space-y-1">
              <label className="block text-sm font-bold text-slate-700 ml-1">
                Email Address
              </label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <i className="fa fa-envelope"></i>
                </div>
                <input
                  type="email"
                  {...register("email", { 
                    required: "Email is required",
                    pattern: {
                      value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                      message: "Invalid email address"
                    }
                  })}
                  className={`block w-full pl-11 pr-4 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all duration-300 outline-none
                    ${errors.email ? 'border-red-100 focus:border-red-500 bg-red-50/30' : 'border-transparent focus:border-blue-500 focus:bg-white shadow-sm focus:shadow-blue-100'}`}
                  placeholder="name@example.com"
                />
              </div>
              {errors.email && (
                <p className="mt-1 text-xs font-bold text-red-500 ml-1 italic animate-in fade-in slide-in-from-left-2 transition-all">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div className="space-y-1">
              <label className="block text-sm font-bold text-slate-700 ml-1">
                Password
              </label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <i className="fa fa-lock"></i>
                </div>
                <input
                  type="password"
                  {...register("password", { 
                    required: "Password is required",
                    minLength: {
                      value: 6,
                      message: "Password must be at least 6 characters"
                    }
                  })}
                  className={`block w-full pl-11 pr-4 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all duration-300 outline-none
                    ${errors.password ? 'border-red-100 focus:border-red-500 bg-red-50/30' : 'border-transparent focus:border-blue-500 focus:bg-white shadow-sm focus:shadow-blue-100'}`}
                  placeholder="••••••••"
                />
              </div>
              {errors.password && (
                <p className="mt-1 text-xs font-bold text-red-500 ml-1 italic animate-in fade-in slide-in-from-left-2 transition-all">
                  {errors.password.message}
                </p>
              )}
            </div>

            <div className="flex items-center justify-between pt-2">
              <button
                type="button"
                onClick={() => setIsSignUp(!isSignUp)}
                className="text-sm font-bold text-blue-600 hover:text-blue-700 transition-colors bg-transparent border-0 cursor-pointer"
              >
                {isSignUp ? "Already have an account? Sign in" : "Don't have an account? Sign up"}
              </button>
            </div>

            <button
              type="submit"
              disabled={loading || !isValid}
              className={`w-full py-4 rounded-2xl text-white font-bold text-lg shadow-lg transform transition-all duration-300
                ${loading || !isValid 
                  ? 'bg-slate-300 cursor-not-allowed border-0' 
                  : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 hover:shadow-blue-500/25 active:scale-[0.98] border-0 cursor-pointer shadow-blue-500/20'}`}
            >
              {loading ? (
                <div className="flex items-center justify-center space-x-2">
                  <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                  <span>Processing...</span>
                </div>
              ) : (
                isSignUp ? "Create Account" : "Sign In"
              )}
            </button>
          </form>

          {/* Social Login Divider (Simulation) */}
          <div className="relative my-10">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-slate-100"></div>
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-4 bg-white text-slate-400 font-medium">Or continue with</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <button className="flex items-center justify-center space-x-2 px-4 py-3 bg-white border-2 border-slate-50 rounded-2xl hover:bg-slate-50 hover:border-slate-100 transition-all duration-300 cursor-pointer shadow-sm active:scale-95">
              <i className="fa fa-google text-red-500"></i>
              <span className="text-sm font-bold text-slate-700">Google</span>
            </button>
            <button className="flex items-center justify-center space-x-2 px-4 py-3 bg-white border-2 border-slate-50 rounded-2xl hover:bg-slate-50 hover:border-slate-100 transition-all duration-300 cursor-pointer shadow-sm active:scale-95">
              <i className="fa fa-facebook text-blue-600"></i>
              <span className="text-sm font-bold text-slate-700">Facebook</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
