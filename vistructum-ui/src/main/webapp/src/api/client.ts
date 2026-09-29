export const apiBase = `${import.meta.env.BASE_URL}api`;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string) {
    super(`${status} ${code}`);
    this.status = status;
    this.code = code;
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

export function csrfToken(): string {
  const match = document.cookie.split(";").map((part) => part.trim()).find((part) => part.startsWith("vistructum_csrf="));
  return match ? decodeURIComponent(match.slice("vistructum_csrf=".length)) : "";
}

async function errorCode(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === "object" && "error" in body && typeof body.error === "string") return body.error;
  } catch {
    return response.statusText || "internal";
  }
  return response.statusText || "internal";
}

type Listener = () => void;
const unauthorizedListeners = new Set<Listener>();

export function onUnauthorized(listener: Listener): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

export async function request<T>(path: string, init: RequestInit = {}, options: { public?: boolean } = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (method !== "GET" && method !== "HEAD") headers.set("X-Vistructum-Csrf", csrfToken());
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(`${apiBase}${path}`, { ...init, method, headers, credentials: "same-origin" });
  if (!response.ok) {
    const error = new ApiError(response.status, await errorCode(response));
    if (response.status === 401 && !options.public) unauthorizedListeners.forEach((listener) => listener());
    throw error;
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const urls = {
  thumbnail: (id: number) => `${apiBase}/findings/${id}/thumbnail.png`,
  face: (uuid: string) => `${apiBase}/players/${uuid}/face.png`,
  skin: (uuid: string) => `${apiBase}/players/${uuid}/skin.png`,
  publicSkin: (token: string, uuid: string) => `${apiBase}/public/${encodeURIComponent(token)}/skins/${uuid}.png`,
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "not_found":
        return "Not found.";
      case "forbidden":
        return "You do not have permission for this action.";
      case "csrf":
        return "Your session token is out of date. Reload the page.";
      case "unavailable":
        return "Not available right now.";
      case "not_shareable":
        return "Only confirmed findings with evidence can be shared.";
      case "bad_request":
        return "The request was rejected.";
      case "unauthorized":
        return "Your session has expired.";
      default:
        return `Server error (${error.status}).`;
    }
  }
  if (error instanceof TypeError) return "The server cannot be reached.";
  return error instanceof Error ? error.message : "Unknown error.";
}
