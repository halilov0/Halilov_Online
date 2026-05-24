# Google Search Console — URL Inspection Plan

State for the daily routine that tells Idan which URLs to submit via GSC's
"בדיקת דף" (URL inspection) → "בקש הוספה לאינדקס" form. The routine fires
daily at 12:00 Asia/Jerusalem, reads this file, diffs against the live
sitemap at https://halilov.co.il/sitemap.xml, and posts today's batch.

Each day = ~5 URLs. GSC manual quota is ~10-12/day, so the buffer leaves
room for new-product alerts on top of the planned batch.

## Status

- **Today's date** (most recent run, YYYY-MM-DD): _none yet_
- **Plan completed:** no
- **Mode after plan completes:** new-product alerts only

## Already submitted

- 2026-05-24 — https://halilov.co.il/ (manually submitted by Idan on first GSC setup)

## Plan (one batch per day)

### Day 1 — Trussardi + premium luggage
_status: pending_
- https://halilov.co.il/p/trussardi-black-leather-messenger
- https://halilov.co.il/p/trussardi-brown-briefcase
- https://halilov.co.il/p/trussardi-small-crossbody
- https://halilov.co.il/p/swiss-alpin-90l-hiking
- https://halilov.co.il/p/silver-hardshell-cabin

### Day 2 — Swiss tech / Cabin luggage
_status: pending_
- https://halilov.co.il/p/swiss-digital-usb-backpack
- https://halilov.co.il/p/swiss-soft-rolling-laptop
- https://halilov.co.il/p/swiss-laptop-rolling-case
- https://halilov.co.il/p/cabin-flux-blue-travel
- https://halilov.co.il/p/cabin-flux-yellow-travel

### Day 3 — Mingxin line
_status: pending_
- https://halilov.co.il/p/mingxin-9810-laptop-bag
- https://halilov.co.il/p/mingxin-9318-classic-bag
- https://halilov.co.il/p/mingxin-8305-flap-bag
- https://halilov.co.il/p/mingxin-8302-vertical-bag
- https://halilov.co.il/p/mingxin-217-horizontal-bag

### Day 4 — Remaining bags + sets
_status: pending_
- https://halilov.co.il/p/weshengda-black-rolling-tote
- https://halilov.co.il/p/weshengda-beige-rolling-tote
- https://halilov.co.il/p/grey-lilac-tote
- https://halilov.co.il/p/since-1979-9015-bag
- https://halilov.co.il/p/navy-4pc-luggage-set

### Day 5 — Info pages with search intent
_status: pending_
- https://halilov.co.il/shipping
- https://halilov.co.il/contact
- https://halilov.co.il/about
- https://halilov.co.il/returns
- https://halilov.co.il/faq

### Day 6 — Stub/seed products (low priority)
_status: pending_
- https://halilov.co.il/p/bread-white
- https://halilov.co.il/p/hummus-tehina
- https://halilov.co.il/p/milk-1l
- https://halilov.co.il/p/dish-soap

## Skipped on purpose (do NOT submit)

These are in the sitemap so Google discovers them, but they don't justify a
manual indexing request:

- https://halilov.co.il/terms — legal, no search intent
- https://halilov.co.il/privacy — legal, no search intent
- https://halilov.co.il/accessibility — legal, no search intent
- https://halilov.co.il/p/water-6pack — stub/seed product
- https://halilov.co.il/p/cola-1.5l — stub/seed product

## New-product alerts (post-plan mode)

When all 6 days are done, the routine switches to delta mode: each run it
appends here any product URL found in the live sitemap that isn't already
under "Already submitted" or in this list, then notifies Idan.

_(no new products discovered yet)_
