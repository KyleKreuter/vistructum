import { booleanOf, isRecord, numberOf, stringOf, stripNamespace } from "./json";

export interface ModelPlacement {
  model: string;
  x: number;
  y: number;
  uvlock: boolean;
}

function placementOf(value: unknown): ModelPlacement | null {
  const entry = Array.isArray(value) ? value[0] : value;
  if (!isRecord(entry)) return null;
  const model = stringOf(entry.model);
  if (!model) return null;
  return { model: stripNamespace(model), x: numberOf(entry.x, 0), y: numberOf(entry.y, 0), uvlock: booleanOf(entry.uvlock, false) };
}

export function variantMatches(key: string, properties: Record<string, string>): boolean {
  if (key === "" || key === "normal") return true;
  return key.split(",").every((pair) => {
    const [name, value] = pair.split("=");
    return properties[name?.trim() ?? ""] === value?.trim();
  });
}

function valueMatches(expected: string, actual: string | undefined): boolean {
  if (actual === undefined) return false;
  const negated = expected.startsWith("!");
  const options = (negated ? expected.slice(1) : expected).split("|");
  return options.includes(actual) !== negated;
}

export function conditionMatches(condition: unknown, properties: Record<string, string>): boolean {
  if (condition === undefined) return true;
  if (!isRecord(condition)) return false;
  if (Array.isArray(condition.OR)) return condition.OR.some((part) => conditionMatches(part, properties));
  if (Array.isArray(condition.AND)) return condition.AND.every((part) => conditionMatches(part, properties));
  return Object.entries(condition).every(([name, expected]) => valueMatches(String(expected), properties[name]));
}

export function resolveBlockstate(definition: unknown, properties: Record<string, string>): ModelPlacement[] {
  if (!isRecord(definition)) return [];
  if (isRecord(definition.variants)) {
    const entries = Object.entries(definition.variants);
    const match = entries.find(([key]) => variantMatches(key, properties)) ?? entries[0];
    const placement = match ? placementOf(match[1]) : null;
    return placement ? [placement] : [];
  }
  if (Array.isArray(definition.multipart)) {
    const result: ModelPlacement[] = [];
    for (const part of definition.multipart) {
      if (!isRecord(part) || !conditionMatches(part.when, properties)) continue;
      const placement = placementOf(part.apply);
      if (placement) result.push(placement);
    }
    return result;
  }
  return [];
}
