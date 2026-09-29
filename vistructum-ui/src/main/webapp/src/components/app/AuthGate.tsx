import { useEffect, useState } from "react";
import { Outlet, useSearchParams } from "react-router";
import { toast } from "sonner";
import { isUnauthorized, onUnauthorized } from "@/api/client";
import { useMe } from "@/api/queries";
import { AppShell } from "./AppShell";
import { SignInPage } from "./SignInPage";
import { ErrorState, PageSpinner } from "./States";

export function AuthGate() {
  const me = useMe();
  const [expired, setExpired] = useState(false);
  const [params, setParams] = useSearchParams();
  const loginExpired = params.get("login") === "expired";

  useEffect(() => onUnauthorized(() => setExpired(true)), []);

  useEffect(() => {
    if (loginExpired && me.data) {
      toast.warning("That sign-in link has expired. You are still signed in.");
      const next = new URLSearchParams(params);
      next.delete("login");
      setParams(next, { replace: true });
    }
  }, [loginExpired, me.data, params, setParams]);

  if (me.isPending) return <PageSpinner className="min-h-screen" />;
  if (expired || isUnauthorized(me.error)) return <SignInPage reason={loginExpired ? "link-expired" : expired && me.data ? "session-expired" : "signed-out"} />;
  if (me.error || !me.data) return <ErrorState error={me.error} onRetry={() => void me.refetch()} className="m-8" />;
  return (
    <AppShell me={me.data}>
      <Outlet />
    </AppShell>
  );
}
