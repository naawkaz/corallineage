import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/react/24/outline'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  totalElements?: number
  pageSize?: number
}

const MAX_VISIBLE = 5

function visiblePageNumbers(page: number, totalPages: number): (number | 'ellipsis')[] {
  if (totalPages <= MAX_VISIBLE) {
    return Array.from({ length: totalPages }, (_, i) => i)
  }
  const half = Math.floor(MAX_VISIBLE / 2)
  let start = Math.max(0, page - half)
  const end = Math.min(totalPages, start + MAX_VISIBLE)
  start = Math.max(0, end - MAX_VISIBLE)

  const pages: (number | 'ellipsis')[] = []
  if (start > 0) {
    pages.push(0)
    if (start > 1) pages.push('ellipsis')
  }
  for (let i = start; i < end; i++) pages.push(i)
  if (end < totalPages) {
    if (end < totalPages - 1) pages.push('ellipsis')
    pages.push(totalPages - 1)
  }
  return pages
}

/** Shared pagination control: sits top-right of its section ~5 page numbers with ellipsis truncation. */
export default function Pagination({ page, totalPages, onPageChange, totalElements, pageSize }: PaginationProps) {
  if (totalPages <= 0) return null
  const pages = visiblePageNumbers(page, totalPages)

  return (
    <div className="pagination-top">
      {totalElements != null && pageSize != null && (
        <span className="pagination-summary">
          {page * pageSize + 1}-{Math.min((page + 1) * pageSize, totalElements)} of {totalElements}
        </span>
      )}
      <button className="page-btn" disabled={page === 0} onClick={() => onPageChange(page - 1)} aria-label="Previous page">
        <ChevronLeftIcon style={{ width: 13, height: 13 }} />
      </button>
      {pages.map((n, i) =>
        n === 'ellipsis' ? (
          <span key={`e${i}`} className="page-ellipsis">
            ...
          </span>
        ) : (
          <button
            key={n}
            className={`page-btn${n === page ? ' active' : ''}`}
            onClick={() => onPageChange(n)}
          >
            {n + 1}
          </button>
        ),
      )}
      <button
        className="page-btn"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        aria-label="Next page"
      >
        <ChevronRightIcon style={{ width: 13, height: 13 }} />
      </button>
    </div>
  )
}
