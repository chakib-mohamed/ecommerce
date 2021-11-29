import React from "react";
import NavBar from "../../components/NavBar/NavBar";

const Layout = (props) => {
  return (
    <React.Fragment>
      <NavBar />

      <main className="pt-5">{props.children}</main>

      {/* <Footer></Footer> */}
    </React.Fragment>
  );
};

export default Layout;
