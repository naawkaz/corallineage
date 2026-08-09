export function formatDate(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatCount(n: number): string {
  return n.toLocaleString('en-US')
}

export function shortHash(hash: string): string {
  return hash.length > 8 ? hash.slice(0, 8) : hash
}
