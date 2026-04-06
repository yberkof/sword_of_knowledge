/** Base for REST calls. With Vite dev proxy, leave empty so `/api/*` hits the proxy. */
const rawBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "";

export const API_BASE_URL = rawBase;

/** Build absolute URL for REST (path must start with `/api/...`). */
export function apiUrl(path: string): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE_URL}${p}`;
}

/**
 * Socket.IO server origin (no path). In Vite dev, defaults to `window.location.origin` so `/socket.io` is proxied;
 * override with `VITE_SOCKET_URL` when the game server is on another host.
 */
export function getSocketUrl(): string {
  const a = (import.meta.env.VITE_SOCKET_URL as string | undefined)?.replace(/\/$/, "");
  if (a) return a;
  if (API_BASE_URL) return API_BASE_URL;
  if (typeof window !== "undefined") return window.location.origin;
  return "http://localhost:3000";
}
