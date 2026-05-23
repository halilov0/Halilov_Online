import { useEffect, useState } from 'react'
import { Icon } from './Icon'
import { useToast } from './Toast'

type Props = {
  open: boolean
  onClose: () => void
  url: string
  /** Title for the Web Share API + email subject. */
  title: string
  /** Pre-filled message body for text-based targets. URL gets appended. */
  message: string
}

type Platform = {
  key: string
  label: string
  brand: string
  icon: React.ReactNode
  href?: (ctx: { url: string; text: string; encodedUrl: string; encodedText: string; encodedTitle: string }) => string
  onClick?: (ctx: { url: string; text: string; title: string }) => void | Promise<void>
}

// Brand glyphs are inline so the share sheet doesn't pull a runtime icon font.
const PLATFORMS: Platform[] = [
  {
    key: 'whatsapp',
    label: 'WhatsApp',
    brand: '#25D366',
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
        <path d="M19.05 4.91A10 10 0 0 0 4.42 18.5L3 21l2.55-.67A10 10 0 1 0 19.05 4.91zM12 19.27a7.27 7.27 0 0 1-3.7-1l-.27-.16-2.13.56.57-2.08-.17-.27a7.27 7.27 0 1 1 5.7 2.95zm4.06-5.42c-.22-.11-1.31-.65-1.51-.72s-.35-.11-.5.11-.57.72-.7.87-.26.17-.48.06a6 6 0 0 1-3-2.62c-.23-.39.23-.36.66-1.21a.4.4 0 0 0 0-.39c0-.11-.5-1.2-.69-1.65s-.36-.37-.5-.38h-.43a.82.82 0 0 0-.6.28A2.5 2.5 0 0 0 7.5 10.4a4.36 4.36 0 0 0 .92 2.32 9.95 9.95 0 0 0 3.83 3.39c.54.23 1 .37 1.3.47a3.13 3.13 0 0 0 1.43.09 2.35 2.35 0 0 0 1.54-1.09 1.92 1.92 0 0 0 .13-1.09c-.06-.1-.21-.16-.43-.27z"/>
      </svg>
    ),
    href: ({ encodedText }) => `https://wa.me/?text=${encodedText}`,
  },
  {
    key: 'telegram',
    label: 'Telegram',
    brand: '#229ED9',
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
        <path d="M21.5 4.4 18.6 19c-.2.9-.8 1.1-1.6.7l-4.4-3.2-2.1 2c-.2.2-.4.4-.9.4l.3-4.5 8.2-7.4c.4-.3-.1-.5-.6-.2l-10.1 6.3-4.3-1.4c-.9-.3-.9-.9.2-1.4l16.8-6.5c.8-.3 1.5.2 1.4 1.6z"/>
      </svg>
    ),
    href: ({ encodedUrl, encodedText }) => `https://t.me/share/url?url=${encodedUrl}&text=${encodedText}`,
  },
  {
    key: 'email',
    label: 'אימייל',
    brand: '#4a5568',
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="m3 7 9 6 9-6" />
      </svg>
    ),
    href: ({ encodedTitle, encodedText }) => `mailto:?subject=${encodedTitle}&body=${encodedText}`,
  },
  {
    key: 'sms',
    label: 'SMS',
    brand: '#34c759',
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 12a8 8 0 0 1-12.4 6.7L4 20l1.3-4.6A8 8 0 1 1 21 12z" />
      </svg>
    ),
    // RFC 5724: body is the only widely-supported field. iOS prefers &, Android takes either.
    href: ({ encodedText }) => `sms:?body=${encodedText}`,
  },
  {
    key: 'copy',
    label: 'העתק קישור',
    brand: '#6b7280',
    icon: <Icon name="link" size={22} />,
    async onClick({ url }) {
      try {
        await navigator.clipboard.writeText(url)
        useToast.getState().push('הקישור הועתק')
      } catch {
        useToast.getState().push('לא ניתן להעתיק')
      }
    },
  },
]

export function ShareMenu({ open, onClose, url, title, message }: Props) {
  const [hasNative, setHasNative] = useState(false)

  // navigator.share is only useful on mobile / Safari — feature-detect at
  // mount so SSR-style first paints don't render an unreachable button.
  useEffect(() => { setHasNative(typeof navigator !== 'undefined' && 'share' in navigator) }, [])

  // Dismiss on Escape — the rest is handled by the backdrop click.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  const fullText = `${message} ${url}`
  const ctx = {
    url,
    text: fullText,
    title,
    encodedUrl: encodeURIComponent(url),
    encodedText: encodeURIComponent(fullText),
    encodedTitle: encodeURIComponent(title),
  }

  async function nativeShare() {
    try {
      await navigator.share({ title, text: message, url })
      onClose()
    } catch {
      /* user cancelled */
    }
  }

  return (
    <div className="cls-share-backdrop" onClick={onClose}>
      <div className="cls-share-sheet" onClick={e => e.stopPropagation()} role="dialog" aria-label="שיתוף">
        <div className="cls-share-head">
          <h3>שיתוף חשבונית</h3>
          <button className="close" onClick={onClose} aria-label="סגור">
            <Icon name="x" size={16} stroke={2.2} />
          </button>
        </div>
        <div className="cls-share-grid">
          {hasNative && (
            <button type="button" className="cls-share-tile" onClick={nativeShare}>
              <span className="ico" style={{ background: 'var(--ink)', color: '#fff' }}>
                <Icon name="share" size={20} />
              </span>
              <span className="lbl">עוד…</span>
            </button>
          )}
          {PLATFORMS.map(p => {
            const handle = async () => {
              if (p.onClick) await p.onClick(ctx)
              else if (p.href) {
                const href = p.href(ctx)
                // mailto/sms must open in same tab to trigger the OS handler.
                if (href.startsWith('mailto:') || href.startsWith('sms:')) {
                  window.location.href = href
                } else {
                  window.open(href, '_blank', 'noopener,noreferrer')
                }
              }
              if (p.key === 'copy') return  // keep menu open after toast
              onClose()
            }
            return (
              <button type="button" key={p.key} className="cls-share-tile" onClick={handle}>
                <span className="ico" style={{ background: p.brand, color: '#fff' }}>
                  {p.icon}
                </span>
                <span className="lbl">{p.label}</span>
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
