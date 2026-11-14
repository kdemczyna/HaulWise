import { AppShell, Burger, Group, NavLink, em } from "@mantine/core";
import { useDisclosure, useMediaQuery } from "@mantine/hooks";
import { useEffect, useRef } from "react";
import { Link, Outlet, matchPath, useLocation } from "react-router";

const NAVBAR_BREAKPOINT = "sm";
/** Just below Mantine's `sm` breakpoint (48em), where the navbar becomes a burger menu. */
const MOBILE_QUERY = `(max-width: ${em(767.98)})`;

const NAV_ITEMS = [
  {
    label: "Plan a run",
    to: "/",
    isActive: (pathname) =>
      pathname === "/" || matchPath("/plans/:id", pathname) !== null,
  },
  {
    label: "Recent plans",
    to: "/plans",
    // Only the list itself: a single plan (/plans/:id) stays under "Plan a run".
    isActive: (pathname) => matchPath("/plans", pathname) !== null,
  },
  { label: "Shipments", to: "/shipments" },
  { label: "Trucks", to: "/trucks" },
  { label: "Warehouses", to: "/warehouses" },
  { label: "Settings", to: "/settings" },
];

function isItemActive(item, pathname) {
  return item.isActive
    ? item.isActive(pathname)
    : pathname === item.to || pathname.startsWith(`${item.to}/`);
}

/**
 * Moves focus to the new page's h1 after client-side navigation (not on the first load).
 * A lazy page shows its Suspense fallback first, so the h1 is awaited until it appears.
 */
function useFocusHeadingOnNavigation(pathname, mainRef) {
  const previousPathname = useRef(pathname);
  useEffect(() => {
    const main = mainRef.current;
    if (previousPathname.current === pathname || !main) {
      return undefined;
    }
    previousPathname.current = pathname;
    const focusHeading = () => {
      const heading = main.querySelector("h1");
      heading?.focus();
      return Boolean(heading);
    };
    if (focusHeading()) {
      return undefined;
    }
    const observer = new MutationObserver(() => {
      if (focusHeading()) {
        observer.disconnect();
      }
    });
    observer.observe(main, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [pathname, mainRef]);
}

/** App shell: header with the brand, collapsible navigation (burger below `sm`) and the routed page. */
export function AppLayout() {
  const [navbarOpened, { toggle, close }] = useDisclosure(false);
  const isMobile = useMediaQuery(MOBILE_QUERY);
  const { pathname } = useLocation();
  const mainRef = useRef(null);
  useFocusHeadingOnNavigation(pathname, mainRef);

  return (
    <AppShell
      className="hw-shell"
      header={{ height: 60 }}
      navbar={{
        width: 220,
        breakpoint: NAVBAR_BREAKPOINT,
        collapsed: { mobile: !navbarOpened },
      }}
      padding="md"
    >
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <AppShell.Header>
        <Group h="100%" px="md" gap="sm">
          <Burger
            opened={navbarOpened}
            onClick={toggle}
            hiddenFrom={NAVBAR_BREAKPOINT}
            size="sm"
            aria-label={navbarOpened ? "Close navigation" : "Open navigation"}
            aria-expanded={navbarOpened}
            aria-controls="main-navigation"
          />
          <Link to="/" className="hw-brand">
            <span className="hw-brand-mark" aria-hidden="true">
              H
            </span>
            HaulWise
          </Link>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar
        p="xs"
        id="main-navigation"
        aria-label="Main navigation"
        inert={isMobile && !navbarOpened}
      >
        {NAV_ITEMS.map((item) => {
          const active = isItemActive(item, pathname);
          return (
            <NavLink
              key={item.to}
              component={Link}
              to={item.to}
              label={item.label}
              active={active}
              aria-current={active ? "page" : undefined}
              onClick={close}
            />
          );
        })}
      </AppShell.Navbar>
      <AppShell.Main
        id="main-content"
        className="hw-shell-main"
        tabIndex={-1}
        ref={mainRef}
      >
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
