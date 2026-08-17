import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthContext } from '../../shared/lib/authContextCore'
import { errorMessage } from '../../shared/api/client'

export default function Register() {
  const { register } = useAuthContext()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register(username.trim(), password, displayName.trim() || undefined)
      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <div
      style={{
        width: '100%',
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--canvas)',
        padding: 16,
      }}
    >
      <div className="card" style={{ width: 360, padding: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <img src="/logo.png" alt="" width={22} height={22} />
          <div style={{ fontWeight: 700, fontSize: 15, letterSpacing: 0.5 }}>CORALLINEAGE</div>
        </div>
        <div style={{ color: 'var(--text-secondary)', fontSize: 12, marginBottom: 20 }}>
          Create an account
        </div>

        {error && (
          <div className="alert alert-error" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <label className="form-label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              className="form-input"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
            <div className="form-hint">At least 3 characters, must be unique.</div>
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="displayName">
              Display name <span style={{ color: 'var(--text-muted)' }}>(optional)</span>
            </label>
            <input
              id="displayName"
              className="form-input"
              placeholder="Defaults to your username"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className="form-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <div className="form-hint">At least 6 characters.</div>
          </div>
          <button
            type="submit"
            className="btn btn-primary"
            style={{ width: '100%', marginTop: 4 }}
            disabled={submitting || username.trim().length < 3 || password.length < 6}
          >
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <div style={{ marginTop: 16, fontSize: 12, color: 'var(--text-secondary)', textAlign: 'center' }}>
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  )
}
