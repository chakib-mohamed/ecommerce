import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import Icon, { type IconName } from '../../components/UI/Icon/Icon';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  end?: boolean;
}

const LINKS: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: 'grid', end: true },
  { to: '/admin/products', label: 'Products', icon: 'search' },
  { to: '/admin/categories', label: 'Categories', icon: 'heart' },
];

/**
 * Back-office shell — sticky 220px sidebar + a scrolling `<Outlet/>` on a
 * paper-2 canvas. Sidebar holds the wordmark, the three admin sections, and a
 * "View storefront" escape hatch.
 */
export default function AdminLayout() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex bg-paper-2">
      <aside className="w-[220px] flex-shrink-0 border-r border-line bg-paper flex flex-col sticky top-0 h-screen overflow-y-auto">
        {/* wordmark */}
        <div className="px-5 pt-6 pb-5 border-b border-line">
          <div className="flex items-center gap-2.5">
            <span className="w-[22px] h-[22px] rounded-full bg-ink flex-shrink-0" />
            <div className="flex flex-col leading-[1.15] min-w-0">
              <span className="font-serif text-[15px] font-semibold whitespace-nowrap">Cloud Shop</span>
              <span className="eyebrow text-[10px] tracking-[0.08em] mt-0.5">Admin</span>
            </div>
          </div>
        </div>

        {/* nav */}
        <nav className="px-2.5 py-3 flex-1">
          {LINKS.map(({ to, label, icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                [
                  'flex items-center gap-2.5 w-full px-3 py-2.5 rounded-md text-sm mb-0.5 transition-colors duration-150',
                  isActive ? 'bg-paper-2 text-ink font-semibold' : 'text-muted font-normal hover:bg-paper-2',
                ].join(' ')
              }
            >
              <Icon name={icon} size={15} />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* footer link */}
        <div className="px-5 py-4 border-t border-line">
          <button
            onClick={() => navigate('/')}
            className="flex items-center gap-1.5 text-[13px] text-muted hover:text-ink bg-transparent border-0 p-0 cursor-pointer"
          >
            <Icon name="back" size={13} /> View storefront
          </button>
        </div>
      </aside>

      <main className="flex-1 flex min-w-0">
        <Outlet />
      </main>
    </div>
  );
}
