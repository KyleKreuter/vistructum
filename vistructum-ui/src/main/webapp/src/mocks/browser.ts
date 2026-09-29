import { setupWorker } from "msw/browser";
import { createElement } from "react";
import { createRoot } from "react-dom/client";
import { createDb } from "./db";
import { DevMenu } from "./DevMenu";
import { handlers, setMockSignedOut } from "./handlers";
import { loadSettings, type MockSettings } from "./settings";

function mountDevMenu(settings: MockSettings) {
  const host = document.createElement("div");
  host.id = "mock-dev-menu";
  document.body.append(host);
  createRoot(host).render(createElement(DevMenu, { settings }));
}

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
  mountDevMenu(settings);
}

export function mockSignIn() {
  setMockSignedOut(false);
  location.assign(`${import.meta.env.BASE_URL}findings?state=open`);
}
