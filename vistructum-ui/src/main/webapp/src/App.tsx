import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { lazy, Suspense, useState, type ReactNode } from "react";
import { createBrowserRouter, Navigate, RouterProvider, useLocation } from "react-router";
import { retryDelayMs, shouldRetry } from "@/api/queries";
import { AuthGate } from "@/components/app/AuthGate";
import { PageSpinner } from "@/components/app/States";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

const FindingsPage = lazy(() => import("@/pages/FindingsPage"));
const FindingDetailPage = lazy(() => import("@/pages/FindingDetailPage"));
const StatsPage = lazy(() => import("@/pages/StatsPage"));
const StatusPage = lazy(() => import("@/pages/StatusPage"));
const PlayerPage = lazy(() => import("@/pages/PlayerPage"));
const ActivityPage = lazy(() => import("@/pages/ActivityPage"));
const PublicEvidencePage = lazy(() => import("@/pages/PublicEvidencePage"));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"));

function RootRedirect() {
  const location = useLocation();
  const expired = new URLSearchParams(location.search).get("login") === "expired";
  return <Navigate to={expired ? "/findings?state=open&login=expired" : "/findings?state=open"} replace />;
}

function page(element: ReactNode) {
  return <Suspense fallback={<PageSpinner />}>{element}</Suspense>;
}

const router = createBrowserRouter(
  [
    { path: "/e/:token", element: page(<PublicEvidencePage />) },
    {
      element: <AuthGate />,
      children: [
        { index: true, element: <RootRedirect /> },
        { path: "findings", element: page(<FindingsPage />) },
        { path: "findings/:id", element: page(<FindingDetailPage />) },
        { path: "findings/:id/evidence", element: page(<FindingDetailPage />) },
        { path: "stats", element: page(<StatsPage />) },
        { path: "status", element: page(<StatusPage />) },
        { path: "players/:uuid", element: page(<PlayerPage />) },
        { path: "activity", element: page(<ActivityPage />) },
        { path: "*", element: page(<NotFoundPage />) },
      ],
    },
  ],
  { basename: import.meta.env.BASE_URL.replace(/\/$/, "") },
);

export function App() {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { refetchOnWindowFocus: false, staleTime: 15_000, retry: shouldRetry, retryDelay: retryDelayMs },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <TooltipProvider delayDuration={300}>
        <RouterProvider router={router} />
        <Toaster position="bottom-right" />
      </TooltipProvider>
    </QueryClientProvider>
  );
}
