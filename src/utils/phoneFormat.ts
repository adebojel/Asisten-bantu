export function toInternational(raw: string | null | undefined): string {
  if (!raw) return '';
  const trimmed = raw.trim();
  const plus = trimmed.startsWith('+');
  const d = trimmed.replace(/\D/g, '');
  if (!d) return '';
  if (plus) return d;
  if (d.startsWith('00')) return d.substring(2);
  if (d.startsWith('0')) return '62' + d.substring(1);
  if (d.startsWith('62')) return d;
  if (d.startsWith('8')) return '62' + d;
  return d;
}
