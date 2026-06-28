import React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import CartDrawer from "../../components/storefront/CartDrawer/CartDrawer";
import Footer from "../../components/storefront/Footer/Footer";
import Header from "../../components/storefront/Header/Header";
import SearchOverlay from "../../components/storefront/SearchOverlay/SearchOverlay";
import Icon from "../../components/UI/Icon/Icon";
import Logo from "../../components/UI/Logo/Logo";

const Layout: React.FC<React.PropsWithChildren> = ({ children }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  // Auth screen owns the full viewport (its own centered brand) — no chrome.
  if (pathname === "/login") {
    return <>{children}</>;
  }

  // Back-office owns the full viewport via its own sidebar shell — no storefront chrome.
  if (pathname.startsWith("/admin")) {
    return <>{children}</>;
  }

  // Checkout / confirmation use a minimal secure header, no nav/footer/drawer.
  if (pathname.startsWith("/checkout") || pathname.startsWith("/confirm")) {
    return (
      <div className="min-h-screen flex flex-col">
        <div className="border-b border-line">
          <div className="max-w-[980px] mx-auto px-6 py-[18px] flex items-center justify-between">
            <Logo onClick={() => navigate("/")} />
            <span className="flex items-center gap-[7px] text-[13px] text-muted">
              <Icon name="lock" size={15} /> Secure checkout
            </span>
          </div>
        </div>
        <main className="flex-grow">{children}</main>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <main className="flex-grow">{children}</main>
      <Footer />
      <CartDrawer />
      <SearchOverlay />
    </div>
  );
};

export default Layout;
