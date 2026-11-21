/** Lee una cookie de una cadena con formato `document.cookie`. */
export function readCookie(name: string, cookieString: string): string | undefined {
  for (const pair of cookieString.split(';')) {
    const separator = pair.indexOf('=');
    if (separator === -1) {
      continue;
    }
    if (pair.slice(0, separator).trim() === name) {
      const raw = pair.slice(separator + 1).trim();
      try {
        return decodeURIComponent(raw);
      } catch {
        return raw;
      }
    }
  }
  return undefined;
}
