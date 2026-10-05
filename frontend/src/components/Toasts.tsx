import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'

type ToastKind = 'info' | 'refresh' | 'error'

interface Toast {
  id: number
  message: string
  kind: ToastKind
}

interface ToastApi {
  show: (message: string, kind?: ToastKind) => void
}

const ToastContext = createContext<ToastApi>({ show: () => {} })

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const show = useCallback((message: string, kind: ToastKind = 'info') => {
    const id = nextId.current++
    setToasts((list) => [...list.slice(-3), { id, message, kind }])
    window.setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), kind === 'refresh' ? 6000 : 3500)
  }, [])

  const value = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.kind}`}>
            {toast.kind === 'refresh' && <span aria-hidden="true">☕ </span>}
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  return useContext(ToastContext)
}
