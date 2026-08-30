import { useEffect, useMemo, useRef, useState } from 'react'
import type { LabelDefinition } from '../../features/datasets/versionsTypes'

interface LabelComboboxProps {
  options: LabelDefinition[]
  value: string
  onChange: (labelCode: string) => void
  placeholder?: string
  disabled?: boolean
  id?: string
  // Appended to the displayed text for the current `value` (e.g. a confidence percentage on a
  // still-pending model prediction) - shown only while the caller keeps passing it, so it
  // disappears the moment the caller stops (e.g. once the prediction is reviewed/resolved).
  // Ignored while the dropdown is open or once the user picks a fresh option, since at that point
  // the displayed text is driven by the user's own selection, not this synced `value`.
  valueSuffix?: string
}

/**
 * Searchable typeahead over a set of LabelDefinitions - text input + filtered option list with
 * keyboard nav. No combobox library exists anywhere in this codebase, so this is plain state +
 * .filter(), styled to match .form-input sizing (see index.css .combobox-*).
 */
export default function LabelCombobox({ options, value, onChange, placeholder, disabled, id, valueSuffix }: LabelComboboxProps) {
  const selectedOption = options.find((o) => o.labelCode === value) ?? null

  // Keep the displayed text in sync when the selection changes from outside (e.g. version switch,
  // or the initial mount/an async-loaded `options` list resolving a `value` that was already set
  // before options arrived). Re-syncs whenever either `value` or the text it resolves to changes,
  // rather than tracking `value` alone, so a same-`value` re-render after `options` finishes
  // loading still picks up the now-resolvable label text. Done during render (React's documented
  // "adjusting state when a prop changes" pattern) rather than in a useEffect, since this is pure
  // state derivation, not a subscription to an external system. Skipped while the dropdown is open
  // so it never clobbers what the user is actively typing.
  const resolvedQuery = selectedOption
    ? `${selectedOption.labelName} (${selectedOption.labelCode})${valueSuffix ?? ''}`
    : ''

  // `query`'s own initial value must be lazily derived from resolvedQuery, not a bare `''` -
  // otherwise a component that mounts fresh *after* `value`/`options` already resolve (e.g. a
  // PredictionCard that only renders once a filter toggle reveals it, well after its labelOptions
  // finished loading) seeds `syncedKey` from that same already-consistent state on its very first
  // render, so the mismatch check below never fires and `query` is stuck at `''` forever - visibly
  // an "already accepted/reviewed" combobox showing blank instead of its real label. A real,
  // previously-unnoticed gap in the fix this same effect's own comment already describes for the
  // sibling case (options resolving *after* mount).
  const [query, setQuery] = useState(() => resolvedQuery)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const syncKey = `${value}::${resolvedQuery}`
  const [syncedKey, setSyncedKey] = useState(syncKey)
  if (syncKey !== syncedKey && !open) {
    setSyncedKey(syncKey)
    setQuery(resolvedQuery)
  }

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!open) return options
    if (!q || (selectedOption && q === `${selectedOption.labelName} (${selectedOption.labelCode})`.toLowerCase())) {
      return options
    }
    return options.filter(
      (o) => o.labelCode.toLowerCase().includes(q) || o.labelName.toLowerCase().includes(q),
    )
  }, [options, query, open, selectedOption])

  function select(option: LabelDefinition) {
    onChange(option.labelCode)
    setQuery(`${option.labelName} (${option.labelCode})`)
    setOpen(false)
  }

  function clear() {
    onChange('')
    setQuery('')
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (!open && (e.key === 'ArrowDown' || e.key === 'Enter')) {
      setOpen(true)
      return
    }
    if (!open) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIndex((i) => Math.min(i + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (filtered[activeIndex]) select(filtered[activeIndex])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="combobox" ref={rootRef}>
      <input
        id={id}
        className="form-input combobox-input"
        placeholder={placeholder ?? 'Search labels…'}
        value={query}
        disabled={disabled}
        onFocus={() => setOpen(true)}
        onClick={() => setOpen(true)}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
          setActiveIndex(0)
          if (e.target.value.trim() === '') clear()
        }}
        onKeyDown={handleKeyDown}
      />
      {open && !disabled && (
        <div className="combobox-list">
          {filtered.length === 0 ? (
            <div className="combobox-empty">No matching labels</div>
          ) : (
            filtered.map((option, idx) => (
              <div
                key={option.labelCode}
                className={`combobox-option${idx === activeIndex ? ' active' : ''}`}
                onMouseDown={(e) => {
                  e.preventDefault()
                  select(option)
                }}
                onMouseEnter={() => setActiveIndex(idx)}
              >
                <span className="combobox-option-name">{option.labelName}</span>
                <span className="combobox-option-code">{option.labelCode}</span>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
