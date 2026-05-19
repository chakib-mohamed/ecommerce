import React from "react";

interface SpinnerProps {
  loading: boolean;
  className?: string;
}

const Spinner: React.FC<SpinnerProps> = ({ loading, className }) => {
  if (!loading) return null;

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center pointer-events-none ${className || ''}`}>
      {/* Backdrop */}
      <div className="absolute inset-0 bg-white/40 backdrop-blur-[2px]"></div>
      
      {/* Spinner Container */}
      <div className="relative flex flex-col items-center space-y-4 animate-in fade-in zoom-in duration-300">
        <div className="relative w-16 h-16">
          {/* Outer Ring */}
          <div className="absolute inset-0 border-4 border-slate-200 rounded-full"></div>
          {/* Animated Spinner Ring */}
          <div className="absolute inset-0 border-4 border-blue-600 border-t-transparent rounded-full animate-spin shadow-lg"></div>
        </div>
        
        <span className="text-sm font-black text-slate-900 tracking-widest uppercase italic animate-pulse">
          Loading...
        </span>
      </div>
    </div>
  );
};

export default React.memo(Spinner);
