import type { FindingDetail } from "@/api/types";

export type Integration = "coreprotect" | "libertybans";

export function findingIntegrations(finding: Pick<FindingDetail, "rolledBackAt">, blockLog: boolean, punishments: string | null): Integration[] {
  const integrations: Integration[] = [];
  if (blockLog || finding.rolledBackAt) integrations.push("coreprotect");
  if (punishments) integrations.push("libertybans");
  return integrations;
}
