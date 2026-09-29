import { FindingList } from "@/components/app/FindingList";
import { PageHeader } from "@/components/app/States";

export default function FindingsPage() {
  return (
    <FindingList
      header={(total) => (
        <PageHeader
          title="Findings"
          description={total === undefined ? "Loading…" : `${total.toLocaleString("en-GB")} ${total === 1 ? "finding" : "findings"} match the filter`}
        />
      )}
    />
  );
}
