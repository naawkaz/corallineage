import type { TriggerReason } from './types/version'
import type { EventType } from './types/event'
import type { ChangeType } from './types/ontology'

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

export const EVENT_TYPES: { value: EventType; label: string; badge: string }[] = [
  { value: 'DATASET_VERSION_CREATED', label: 'Dataset version created', badge: 'badge-blue' },
  { value: 'DATASET_IMPORT_COMPLETED', label: 'Dataset import completed', badge: 'badge-blue' },
  { value: 'IMAGE_ADDED', label: 'Image added', badge: 'badge-blue' },
  { value: 'IMAGE_REMOVED', label: 'Image removed', badge: 'badge-red' },
  { value: 'ANNOTATIONS_MODIFIED', label: 'Annotations modified', badge: 'badge-gray' },
  { value: 'TRAINING_JOB_STARTED', label: 'Training job started', badge: 'badge-amber' },
  { value: 'MODEL_TRAINED', label: 'Model trained', badge: 'badge-green' },
  { value: 'MODEL_STATUS_CHANGED', label: 'Model status changed', badge: 'badge-gray' },
  { value: 'ANNOTATION_JOB_STARTED', label: 'Annotation job started', badge: 'badge-amber' },
  { value: 'MODEL_APPLIED', label: 'Model applied', badge: 'badge-green' },
  { value: 'HUMAN_CORRECTION', label: 'Human correction', badge: 'badge-amber' },
  { value: 'ONTOLOGY_CHANGE_DETECTED', label: 'Ontology change detected', badge: 'badge-red' },
  { value: 'DIRTY_FLAGS_RAISED', label: 'Dirty flags raised', badge: 'badge-red' },
  { value: 'DIRTY_FLAGS_RESOLVED', label: 'Dirty flags resolved', badge: 'badge-green' },
  { value: 'REPORT_GENERATED', label: 'Report generated', badge: 'badge-gray' },
]

export function eventTypeLabel(value: EventType): string {
  return EVENT_TYPES.find((t) => t.value === value)?.label ?? value
}

export function eventTypeBadge(value: EventType): string {
  return EVENT_TYPES.find((t) => t.value === value)?.badge ?? 'badge-gray'
}

export const CHANGE_TYPES: { value: ChangeType; label: string; badge: string }[] = [
  { value: 'LABEL_ADDED', label: 'Label added', badge: 'badge-blue' },
  { value: 'LABEL_DEPRECATED', label: 'Label deprecated', badge: 'badge-gray' },
  { value: 'LABEL_SPLIT', label: 'Label split', badge: 'badge-amber' },
  { value: 'LABEL_RENAMED', label: 'Label renamed', badge: 'badge-amber' },
]

export function changeTypeLabel(value: ChangeType): string {
  return CHANGE_TYPES.find((t) => t.value === value)?.label ?? value
}

export function changeTypeBadge(value: ChangeType): string {
  return CHANGE_TYPES.find((t) => t.value === value)?.badge ?? 'badge-gray'
}
