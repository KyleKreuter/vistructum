import { Link } from "react-router";
import { EmptyState } from "@/components/app/States";

export default function NotFoundPage() {
  return (
    <EmptyState title="Page not found" className="mt-10">
      <Link to="/findings" className="underline">
        Go to the findings
      </Link>
    </EmptyState>
  );
}
