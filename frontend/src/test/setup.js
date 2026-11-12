import "@testing-library/jest-dom/vitest";

// jsdom lacks these browser APIs; Mantine needs them (see Mantine's Vitest guide).
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserverMock;

window.HTMLElement.prototype.scrollIntoView = () => {};
