import React, { useState } from "react";

const Paginator = ({ pagesCount, onPaginate }) => {
  const [currentPage, setCurrentPage] = useState(1);

  let nbrPages = pagesCount; // Math.floor(count / pageSize);

  const onSelectPage = (page, event) => {
    event.preventDefault();
    setCurrentPage(page);
    onPaginate(page);
  };

  let pages = [];
  for (let index = 1; index <= nbrPages; index++) {
    pages.push(
      <li
        key={index}
        onClick={(e) => onSelectPage(index, e)}
        className={"page-item" + (currentPage === index ? " active" : "")}
      >
        <a className="page-link" href="/">
          {index}
        </a>
      </li>
    );
  }

  return (
    <React.Fragment>
      {nbrPages > 1 ? (
        <nav>
          <ul className="pagination justify-content-center">
            <li onClick={(e) => onSelectPage(1, e)} className="page-item">
              <a className="page-link" href="/" aria-label="Previous">
                <span aria-hidden="true">&laquo;</span>
              </a>
            </li>

            {pages}

            <li
              onClick={(e) => onSelectPage(nbrPages, e)}
              className="page-item"
            >
              <a className="page-link" href="/" aria-label="Next">
                <span aria-hidden="true">&raquo;</span>
              </a>
            </li>
          </ul>
        </nav>
      ) : null}
    </React.Fragment>
  );
};

export default React.memo(Paginator);
