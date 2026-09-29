import { KeyRound, Terminal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Logo } from "./Logo";
import { ThemeToggle } from "./ThemeToggle";

type SignInReason = "signed-out" | "session-expired" | "link-expired";

const messages: Record<SignInReason, string | null> = {
  "signed-out": null,
  "session-expired": "Your session has expired.",
  "link-expired": "That sign-in link has expired or was already used.",
};

export function SignInPage({ reason }: { reason: SignInReason }) {
  const notice = messages[reason];
  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background p-4">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="flex w-full max-w-md flex-col items-center gap-6">
      <Logo scale={2} />
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <KeyRound className="size-5 text-primary" /> Sign in with /vis web in game
          </CardTitle>
          <CardDescription>Vistructum Review is opened from inside Minecraft. There is no password.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 text-sm">
          {notice && <p className="rounded-md border border-open/40 bg-open/10 px-3 py-2 text-foreground">{notice}</p>}
          <ol className="list-decimal space-y-2 pl-5 text-muted-foreground">
            <li>Join the server with a staff account.</li>
            <li>
              Run <code className="rounded bg-muted px-1.5 py-0.5 text-foreground">/vis web</code> in chat.
            </li>
            <li>Click the link in chat. It signs you in for 12 hours.</li>
          </ol>
          <p className="flex items-start gap-2 text-muted-foreground">
            <Terminal className="mt-0.5 size-4 shrink-0" />
            <span>
              <code className="text-foreground">/vis evidence &lt;id&gt;</code> opens a finding directly.
            </span>
          </p>
          {__MOCK__ && (
            <Button
              className="w-full"
              onClick={() => {
                void import("@/mocks/browser").then(({ mockSignIn }) => mockSignIn());
              }}
            >
              Sign in (mock mode)
            </Button>
          )}
        </CardContent>
      </Card>
      </div>
    </div>
  );
}
