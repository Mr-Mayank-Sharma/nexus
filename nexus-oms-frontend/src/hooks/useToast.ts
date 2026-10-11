import { createContext, useContext } from 'react'

export interface Toast {
  id: string
  type: 'success' | 'error' | 'info' | 'warning'
  title: string
  message?: string
  /** Alias of message. */
  description?: string
  duration?: number
}

export type ToastVariant = Toast['type'] | 'default'

export interface ToastOptions {
  title: string
  description?: string
  variant?: ToastVariant
  duration?: number
}

interface ToastContextType {
  toasts: Toast[]
  addToast: (toast: Omit<Toast, 'id'>) => void
  removeToast: (id: string) => void
  /**
   * Convenience wrapper over addToast. Two call shapes are in use across the app:
   * toast('Saved', 'success') and toast({ title, description, variant }).
   */
  toast: (options: ToastOptions | string, variant?: ToastVariant) => void
}

export const ToastContext = createContext<ToastContextType | undefined>(undefined)

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used within ToastProvider')
  return context
}
