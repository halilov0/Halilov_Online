import type { ReactElement } from 'react'

type IconName =
  | 'search' | 'cart' | 'user' | 'heart' | 'plus' | 'minus' | 'chev'
  | 'leaf' | 'arrow' | 'bag' | 'check' | 'truck' | 'secure' | 'pkg' | 'x' | 'menu'
  | 'pin' | 'phone' | 'bolt' | 'grid' | 'chev-d' | 'share' | 'link'

const PATHS: Record<IconName, ReactElement> = {
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3-3" /></>,
  cart:   <><path d="M3 5h2l2.5 11h11L21 8H6" /><circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" /></>,
  user:   <><circle cx="12" cy="8" r="4" /><path d="M4 21c1.5-4 5-6 8-6s6.5 2 8 6" /></>,
  heart:  <><path d="M12 20s-7-4.5-9-9a5 5 0 0 1 9-3 5 5 0 0 1 9 3c-2 4.5-9 9-9 9z" /></>,
  plus:   <><path d="M12 5v14M5 12h14" /></>,
  minus:  <><path d="M5 12h14" /></>,
  chev:   <><path d="m9 6 6 6-6 6" /></>,
  leaf:   <><path d="M5 19c0-7 6-13 14-13 0 8-6 14-14 14z" /><path d="M5 19 14 10" /></>,
  arrow:  <><path d="M5 12h14M13 5l7 7-7 7" /></>,
  bag:    <><path d="M6 7h12l-1 13H7zM9 7a3 3 0 0 1 6 0" /></>,
  check:  <><path d="m5 12 5 5 9-11" /></>,
  truck:  <><path d="M2 7h11v9H2zM13 11h5l3 3v2h-8" /><circle cx="6" cy="18" r="1.6" /><circle cx="17" cy="18" r="1.6" /></>,
  secure: <><path d="M12 3 4 6v6c0 5 4 8 8 9 4-1 8-4 8-9V6z" /><path d="m9 12 2 2 4-5" /></>,
  pkg:    <><path d="M3 7 12 3l9 4-9 4z" /><path d="M3 7v10l9 4 9-4V7" /><path d="M12 11v10" /></>,
  x:      <><path d="M6 6l12 12M18 6 6 18" /></>,
  menu:   <><path d="M4 6h16M4 12h16M4 18h16" /></>,
  pin:    <><path d="M12 21s7-7 7-12a7 7 0 0 0-14 0c0 5 7 12 7 12z" /><circle cx="12" cy="9" r="2.5" /></>,
  phone:  <><path d="M5 4h4l2 5-3 2a12 12 0 0 0 5 5l2-3 5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" /></>,
  bolt:   <><path d="M13 2 4 14h7l-1 8 9-12h-7z" /></>,
  grid:   <><rect x="4" y="4" width="6" height="6" rx="1" /><rect x="14" y="4" width="6" height="6" rx="1" /><rect x="4" y="14" width="6" height="6" rx="1" /><rect x="14" y="14" width="6" height="6" rx="1" /></>,
  'chev-d': <><path d="m6 9 6 6 6-6" /></>,
  share:  <><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="6" r="2.5" /><circle cx="18" cy="18" r="2.5" /><path d="m8 11 8-4M8 13l8 4" /></>,
  link:   <><path d="M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 1 0-5.66-5.66l-1.5 1.5" /><path d="M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 1 0 5.66 5.66l1.5-1.5" /></>,
}

export function Icon({ name, size = 18, stroke = 1.6 }: { name: IconName; size?: number; stroke?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
         stroke="currentColor" strokeWidth={stroke}
         strokeLinecap="round" strokeLinejoin="round">
      {PATHS[name]}
    </svg>
  )
}

export type { IconName }
