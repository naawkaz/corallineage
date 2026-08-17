import { api } from '../../shared/api/client'
import type { AnnotationEvent, EventFilters } from '../events/eventsTypes'
import type { Page } from '../datasets/versionsTypes'

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
