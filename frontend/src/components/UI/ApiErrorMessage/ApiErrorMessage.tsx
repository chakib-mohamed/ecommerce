import React, { useEffect, useState } from "react";
import Icon from "../Icon/Icon";

interface ApiErrorMessageProps {
  error: {
    code?: string;
    message?: string;
  } | null;
}

const ApiErrorMessage: React.FC<ApiErrorMessageProps> = ({ error }) => {
  const [display, setDisplay] = useState(true);
  const [message, setMessage] = useState<string | undefined>();

  useEffect(() => {
    if (error) {
      setDisplay(true); // Reset display when new error appears
      switch (error.code) {
        case "auth/user-not-found":
        case "auth/wrong-password":
          setMessage("Login/Password incorrect");
          break;
        case "auth/email-already-in-use":
          setMessage(error.message);
          break;
        default:
          setMessage(error.message);
      }
    } else {
      setMessage(undefined);
    }
  }, [error]);

  if (!message || !display) return null;

  return (
    <div className="max-w-md mx-auto mb-6 p-4 bg-red-50 border border-red-100 rounded-2xl flex items-center justify-between animate-in fade-in slide-in-from-top duration-500 shadow-lg shadow-red-500/5">
      <div className="flex items-center space-x-3">
        <div className="bg-red-500 p-2 rounded-full text-white shadow-lg shadow-red-200 grid place-items-center">
          <svg
            width={16}
            height={16}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.8}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M12 3 2 20h20L12 3z" />
            <path d="M12 10v4M12 17h.01" />
          </svg>
        </div>
        <p className="text-red-800 font-bold text-sm tracking-tight">{message}</p>
      </div>
      <button 
        onClick={() => setDisplay(false)} 
        className="text-red-400 hover:text-red-600 bg-transparent border-0 cursor-pointer p-1 transition-colors"
        aria-label="Dismiss error"
      >
        <Icon name="close" size={16} />
      </button>
    </div>
  );
};

export default React.memo(ApiErrorMessage);
