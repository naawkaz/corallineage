import { createContext, useContext } from 'react'

export interface JobPollingContextValue {
  // Increments every time JobPollingProvider observes any tracked training job or Unlabeled
  // import queue entry transition into a different status since the last poll tick. A list that
  // wants to self-refresh live (without migrating its own fetch/pagination/filter state onto
  // provider-owned data) adds this to its existing fetch effect's dependency array - the list
  // keeps fetching itself, it just now also re-fetches on every observed change, not only on
  // mount/page-change. Defaults to a stable 0 outside the `/sources/:sourceId/*` route tree
  // (JobPollingProvider isn't mounted there), so consuming this hook is always safe.
  refreshSignal: number
}

export const JobPollingContext = createContext<JobPollingContextValue>({ refreshSignal: 0 })

export function useJobPolling(): JobPollingContextValue {
  return useContext(JobPollingContext)
}
