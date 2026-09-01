import { useEffect, useMemo, useRef, useState } from 'react'
import type { LabelDefinition } from '../../features/datasets/versionsTypes'

interface LabelComboboxProps {
  options: LabelDefinition[]
  value: string
  onChange: (labelCode: string) => void
  placeholder?: string
  disabled?: boolean
  id?: string
  // Appended to the displayed text for the current `value`, e.g. a confidence percentage.
  // Ignored while the dropdown is open or after the user picks an option.
  valueSuffix?: string
}

/**
 * Searchable typeahead over a set of LabelDefinitions: text input plus a filtered option list
 * with keyboard navigation. Styled to match .form-input sizing (.combobox-* in index.css).
 */
export default function LabelCombobox({ options, value, onChange, placeholder, disabled, id, valueSuffix }: LabelComboboxProps) {
  const selectedOption = options.find((o) => o.labelCode === value) ?? null

  // Keep the displayed text in sync when the selection changes from outside, including a
  // same-`value` re-render after async `options` finish loading and the label becomes
  // resolvable. Derived during render, and skipped while the dropdown is open so it never
  // clobbers what the user is typing.
  const resolvedQuery = selectedOption
    ? `${selectedOption.labelName} (${selectedOption.labelCode})${valueSuffix ?? ''}`
    : ''

  // Seed `query` lazily from resolvedQuery, not `''`: a component that mounts after `value` and
  // `options` already resolve seeds `syncedKey` from consistent state, so the mismatch check
  // below never fires and `query` would otherwise stay `''` forever.
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
        placeholder={placeholder ?? 'Search labels...'}
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
