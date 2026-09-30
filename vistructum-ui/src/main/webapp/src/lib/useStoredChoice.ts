import { useState } from "react";

function readChoice(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function storeChoice(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    return;
  }
}

export function useStoredChoice<T extends string>(key: string, options: readonly T[], fallback: T): [T, (value: string) => void] {
  const [stored, setStored] = useState(() => readChoice(key));
  const choice = options.find((option) => option === stored) ?? fallback;
  const choose = (value: string) => {
    setStored(value);
    storeChoice(key, value);
  };
  return [choice, choose];
}
