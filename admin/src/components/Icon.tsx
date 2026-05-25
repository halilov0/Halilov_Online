import type { ReactElement } from 'react'

type IconName =
  | 'search' | 'plus' | 'minus' | 'chev' | 'chevDown' | 'check' | 'x'
  | 'dash' | 'orders' | 'box' | 'tag' | 'users' | 'megaphone' | 'chart' | 'cog'
  | 'leaf' | 'truck' | 'arrow' | 'download' | 'upload' | 'edit' | 'trash' | 'percent'
  | 'history' | 'logout'

const PATHS: Record<IconName, ReactElement> = {
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3-3" /></>,
  plus:   <><path d="M12 5v14M5 12h14" /></>,
  minus:  <><path d="M5 12h14" /></>,
  chev:   <><path d="m9 6 6 6-6 6" /></>,
  chevDown: <><path d="m6 9 6 6 6-6" /></>,
  check:  <><path d="m5 12 5 5 9-11" /></>,
  x:      <><path d="M6 6l12 12M18 6 6 18" /></>,
  dash:   <><path d="M3 12 12 4l9 8" /><path d="M5 10v10h14V10" /></>,
  orders: <><path d="M3 7h18v13H3z" /><path d="M8 7V5a4 4 0 0 1 8 0v2" /></>,
  box:    <><path d="M3 7 12 3l9 4-9 4z" /><path d="M3 7v10l9 4 9-4V7" /><path d="M12 11v10" /></>,
  tag:    <><path d="M3 12 12 3h9v9l-9 9z" /><circle cx="16.5" cy="7.5" r="1.2" /></>,
  users:  <><circle cx="9" cy="8" r="3.5" /><path d="M3 20c1-3.5 3.5-5.5 6-5.5s5 2 6 5.5" /><circle cx="17" cy="9" r="2.5" /><path d="M15.5 14.5c2.5.5 4.5 2 5.5 5.5" /></>,
  megaphone: <><path d="M3 11v3l11 5V6z" /><path d="M14 8v9" /><path d="M18 9c1.5 1 1.5 4 0 5" /></>,
  chart:  <><path d="M4 19h16" /><path d="M7 16V9" /><path d="M11 16V5" /><path d="M15 16v-6" /><path d="M19 16v-9" /></>,
  cog:    <><circle cx="12" cy="12" r="3" /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1" /></>,
  leaf:   <><path d="M5 19c0-7 6-13 14-13 0 8-6 14-14 14z" /><path d="M5 19 14 10" /></>,
  truck:  <><path d="M2 7h11v9H2zM13 11h5l3 3v2h-8" /><circle cx="6" cy="18" r="1.6" /><circle cx="17" cy="18" r="1.6" /></>,
  arrow:  <><path d="M5 12h14M13 5l7 7-7 7" /></>,
  download: <><path d="M12 4v12" /><path d="m7 11 5 5 5-5" /><path d="M5 20h14" /></>,
  upload: <><path d="M12 20V8" /><path d="m7 13 5-5 5 5" /><path d="M5 4h14" /></>,
  edit:   <><path d="M4 20h4l11-11-4-4L4 16z" /></>,
  trash:  <><path d="M5 7h14" /><path d="M10 7V5a2 2 0 0 1 4 0v2" /><path d="M6 7v13h12V7" /><path d="M10 11v6M14 11v6" /></>,
  percent: <><circle cx="7.5" cy="7.5" r="2.2" /><circle cx="16.5" cy="16.5" r="2.2" /><path d="M19 5 5 19" /></>,
  history: <><path d="M3 12a9 9 0 1 0 3-6.7" /><path d="M3 4v5h5" /><path d="M12 7v5l3 2" /></>,
  logout: <><path d="M15 4h4v16h-4" /><path d="M10 8l-4 4 4 4" /><path d="M6 12h11" /></>,
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
