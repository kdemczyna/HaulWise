import {
  Button,
  Card,
  Modal,
  NavLink,
  Notification,
  Paper,
  createTheme,
} from "@mantine/core";

/**
 * Design tokens (visual design approved by the user on 2026-11-13, ARCHITECTURE section 8).
 * Every colour in the UI comes from here: Mantine palettes, `theme.other` and the
 * `--hw-*` CSS variables set by `cssVariablesResolver`.
 */
const tokens = {
  brand: "#1D5FD1",
  brandDark: "#163F8C",
  brandLight: "#EAF1FD",
  priority: "#B54708",
  page: "#F5F6F8",
  surface: "#FFFFFF",
  border: "#E3E6EA",
  text: "#15181C",
  muted: "#5B6470",
  success: "#067647",
  danger: "#B42318",
};

/** Shade 6 is the base colour, shade 7 the hover / dark variant, shade 0 the light background. */
const brand = [
  tokens.brandLight,
  "#D4E3FB",
  "#A9C6F6",
  "#7DA8F0",
  "#5189E7",
  "#3171DC",
  tokens.brand,
  tokens.brandDark,
  "#11316D",
  "#0B214A",
];

const priority = [
  "#FFF6ED",
  "#FEEBD6",
  "#FDD3AA",
  "#FBB370",
  "#F79239",
  "#E8740F",
  tokens.priority,
  "#93370D",
  "#7A2E0E",
  "#5C220A",
];

export const theme = createTheme({
  colors: { brand, priority },
  primaryColor: "brand",
  primaryShade: 6,
  black: tokens.text,
  fontFamily:
    'Inter, system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif',
  headings: {
    fontWeight: "700",
    sizes: {
      h1: { fontSize: "1.75rem", lineHeight: "1.3" },
    },
  },
  defaultRadius: "md",
  radius: { md: "8px" },
  focusRing: "auto",
  other: {
    tokens,
    priorityTags: {
      URGENT: { background: "#FEE4E2", color: "#B42318" },
      HIGH: { background: "#FEF0C7", color: "#93370D" },
      NORMAL: { background: "#F2F4F7", color: "#475467" },
    },
  },
  components: {
    Button: Button.extend({
      vars: (_theme, props) =>
        props.size === undefined || props.size === "sm"
          ? { root: { "--button-height": "40px" } }
          : { root: {} },
    }),
    Card: Card.extend({ defaultProps: { radius: 10 } }),
    Paper: Paper.extend({ defaultProps: { radius: 10 } }),
    NavLink: NavLink.extend({ classNames: { root: "hw-nav-link" } }),
    // Icon-only close buttons need an accessible name.
    Modal: Modal.extend({
      defaultProps: { closeButtonProps: { "aria-label": "Close dialog" } },
    }),
    Notification: Notification.extend({
      defaultProps: {
        closeButtonProps: { "aria-label": "Close notification" },
      },
    }),
  },
});

/** Exposes the tokens as CSS variables and maps Mantine's text, muted and border colours onto them. */
export const cssVariablesResolver = () => ({
  variables: {
    "--hw-color-brand": tokens.brand,
    "--hw-color-brand-dark": tokens.brandDark,
    "--hw-color-brand-light": tokens.brandLight,
    "--hw-color-page": tokens.page,
    "--hw-color-surface": tokens.surface,
    "--hw-color-border": tokens.border,
    "--hw-color-text": tokens.text,
    "--hw-color-muted": tokens.muted,
    "--hw-color-success": tokens.success,
    "--hw-color-danger": tokens.danger,
  },
  light: {
    "--mantine-color-text": tokens.text,
    "--mantine-color-dimmed": tokens.muted,
    "--mantine-color-default-border": tokens.border,
    "--mantine-color-error": tokens.danger,
  },
  dark: {},
});
