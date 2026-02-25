export function load<T>(k: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(k);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function save<T>(k: string, v: T) {
  localStorage.setItem(k, JSON.stringify(v));
}