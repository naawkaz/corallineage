import type { TriggerReason } from './types/version'

export const CURRENT_USER = 'researcher_01'

export const PAGE_SIZE = 5

export const TRIGGER_REASONS: { value: TriggerReason; label: string; badge: string }[] = [
  { value: 'IMAGES_ADDED', label: 'Images added', badge: 'badge-blue' },
  { value: 'IMAGES_REMOVED', label: 'Images removed', badge: 'badge-red' },
  { value: 'ANNOTATIONS_MODIFIED', label: 'Annotations modified', badge: 'badge-gray' },
  { value: 'LABEL_DEFINITION_CHANGED', label: 'Label changed', badge: 'badge-amber' },
  { value: 'MANUAL_SNAPSHOT', label: 'Manual snapshot', badge: 'badge-gray' },
]

export function triggerReasonLabel(value: TriggerReason): string {
  return TRIGGER_REASONS.find((t) => t.value === value)?.label ?? value
}

export function triggerReasonBadge(value: TriggerReason): string {
  return TRIGGER_REASONS.find((t) => t.value === value)?.badge ?? 'badge-gray'
}
