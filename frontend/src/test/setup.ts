import '@testing-library/jest-dom';

// Polyfill ResizeObserver for Recharts ResponsiveContainer in tests
globalThis.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};
