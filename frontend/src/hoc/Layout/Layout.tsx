import React from "react";
import NavBar from "../../components/NavBar/NavBar";

const Layout: React.FC<React.PropsWithChildren> = ({ children }) => {
  return (
    <div className="min-h-screen bg-slate-50/50 flex flex-col selection:bg-blue-100 selection:text-blue-900">
      <NavBar />

      <main className="flex-grow pt-20 pb-20 animate-in fade-in duration-700">
        <div className="relative">
          <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-96 bg-gradient-to-b from-blue-50/50 to-transparent -z-10 rounded-full blur-3xl opacity-50"></div>
          
          {children}
        </div>
      </main>

      <footer className="py-12 border-t border-slate-100 bg-white">
        <div className="max-w-7xl mx-auto px-4 text-center space-y-4">
          <p className="text-xs font-black text-slate-400 uppercase tracking-[0.3em] italic">
            &copy; 2026 Commerce
          </p>
          <div className="flex justify-center space-x-6 text-slate-300">
            <i className="fa-brands fa-instagram hover:text-blue-600 transition-colors cursor-pointer"></i>
            <i className="fa-brands fa-twitter hover:text-blue-400 transition-colors cursor-pointer"></i>
            <i className="fa-brands fa-github hover:text-slate-900 transition-colors cursor-pointer"></i>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default Layout;
