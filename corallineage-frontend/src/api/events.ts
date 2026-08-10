import { api } from './client'
import type { AnnotationEvent, EventFilters } from '../types/event'
import type { Page } from '../types/version'

export async function fetchEvents(
  filters: EventFilters,
  page: number,
  size: number,
): Promise<Page<AnnotationEvent>> {
  const { data } = await api.get<Page<AnnotationEvent>>('/events', {
    params: { ...filters, page, size },
  })
  return data
}
