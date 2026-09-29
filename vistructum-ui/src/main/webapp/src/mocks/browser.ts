import { setupWorker } from "msw/browser";
import { createDb } from "./db";
import { handlers, setMockSignedOut } from "./handlers";
import { loadSettings } from "./settings";

export async function startMocks() {
  const params = new URLSearchParams(location.search);
  const mode = params.get("mock");
  if (mode === "signed-out") setMockSignedOut(true);
  if (mode === "signed-in") setMockSignedOut(false);
  const settings = loadSettings(location.search);
  document.cookie = "vistructum_csrf=mock-csrf-token; path=/review; SameSite=Strict";
  const db = await createDb(settings.scenario);
  const worker = setupWorker(...handlers(db));
  await worker.start({
    serviceWorker: { url: `${import.meta.env.BASE_URL}mockServiceWorker.js`, options: { scope: import.meta.env.BASE_URL } },
    onUnhandledRequest: "bypass",
    quiet: true,
  });
}

export function mockSignIn() {
  setMockSignedOut(false);
  location.assign(`${import.meta.env.BASE_URL}findings?state=open`);
}
