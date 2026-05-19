import React, { useState } from "react";

interface PaginatorProps {
  pagesCount: number;
  onPaginate: (page: number) => void;
}

const Paginator: React.FC<PaginatorProps> = ({ pagesCount, onPaginate }) => {
  const [currentPage, setCurrentPage] = useState(1);

  if (pagesCount <= 1) return null;

  const onSelectPage = (page: number, event: React.MouseEvent) => {
    event.preventDefault();
    setCurrentPage(page);
    onPaginate(page);
  };

  const pages = [];
  for (let i = 1; i <= pagesCount; i++) {
    pages.push(i);
  }

  return (
    <nav className="flex justify-center mt-12 mb-8 animate-in fade-in slide-in-from-bottom duration-500">
      <ul className="flex items-center space-x-2 bg-white/50 backdrop-blur-md p-2 rounded-2xl border border-slate-100 shadow-xl">
        {/* Previous */}
        <li>
          <button
            onClick={(e) => onSelectPage(Math.max(1, currentPage - 1), e)}
            disabled={currentPage === 1}
            className="w-10 h-10 flex items-center justify-center rounded-xl text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-all disabled:opacity-30 disabled:hover:bg-transparent border-0 bg-transparent cursor-pointer"
            aria-label="Previous"
          >
            <i className="fa fa-chevron-left text-xs"></i>
          </button>
        </li>

        {/* Page Numbers */}
        {pages.map((page) => (
          <li key={page}>
            <button
              onClick={(e) => onSelectPage(page, e)}
              className={`w-10 h-10 flex items-center justify-center rounded-xl font-bold text-sm transition-all border-0 cursor-pointer
                ${currentPage === page 
                  ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/30 scale-110' 
                  : 'bg-transparent text-slate-500 hover:bg-slate-100 hover:text-slate-900 active:scale-95'}`}
            >
              {page}
            </button>
          </li>
        ))}

        {/* Next */}
        <li>
          <button
            onClick={(e) => onSelectPage(Math.min(pagesCount, currentPage + 1), e)}
            disabled={currentPage === pagesCount}
            className="w-10 h-10 flex items-center justify-center rounded-xl text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-all disabled:opacity-30 disabled:hover:bg-transparent border-0 bg-transparent cursor-pointer"
            aria-label="Next"
          >
            <i className="fa fa-chevron-right text-xs"></i>
          </button>
        </li>
      </ul>
    </nav>
  );
};

export default React.memo(Paginator);
