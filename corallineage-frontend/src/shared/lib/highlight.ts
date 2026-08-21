const HIGHLIGHT_DURATION_MS = 1500

/**
 * Scrolls to the first matching row and briefly flashes every matching row (via the
 * `.row-highlight` CSS animation in index.css), used by the Overview stat rings' "Go" buttons
 * and cross-page highlight-on-arrival (see AnnotationJobs.tsx's `?highlight=` handling).
 */
export function scrollToAndHighlightAll(ids: string[]): void {
  if (ids.length === 0) return
  document.getElementById(ids[0])?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  for (const id of ids) {
    const el = document.getElementById(id)
    if (!el) continue
    el.classList.add('row-highlight')
    window.setTimeout(() => el.classList.remove('row-highlight'), HIGHLIGHT_DURATION_MS)
  }
}
