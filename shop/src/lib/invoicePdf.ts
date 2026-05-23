// Lazily wrap html2pdf.js so the ~300KB chunk only loads when the user
// actually clicks Share/Download — not on every catalog page hit.
//
// We render an offscreen-positioned clone of the live invoice so the PDF
// matches what the user sees, minus the toolbar (no-print) and any
// outline halo from the page chrome.

type Html2PdfWorker = {
  set: (opts: unknown) => Html2PdfWorker
  from: (src: HTMLElement) => Html2PdfWorker
  outputPdf: (kind: 'blob') => Promise<Blob>
  save: () => Promise<void>
}

type Html2PdfFn = (opts?: unknown) => Html2PdfWorker

let cached: Html2PdfFn | null = null

async function loadHtml2Pdf(): Promise<Html2PdfFn> {
  if (cached) return cached
  // Type-shimmed since the package ships no TS types of its own.
  const mod = (await import('html2pdf.js' as string)) as { default: Html2PdfFn } | Html2PdfFn
  cached = (typeof mod === 'function' ? mod : mod.default) as Html2PdfFn
  return cached
}

function pdfOptions(filename: string) {
  return {
    margin:       [10, 10, 14, 10] as [number, number, number, number],
    filename,
    image:        { type: 'jpeg' as const, quality: 0.95 },
    html2canvas:  { scale: 2, useCORS: true, backgroundColor: '#ffffff' },
    jsPDF:        { unit: 'mm' as const, format: 'a4' as const, orientation: 'portrait' as const },
    pagebreak:    { mode: ['avoid-all', 'css', 'legacy'] },
  }
}

export async function generateInvoicePdfBlob(
  source: HTMLElement,
  orderNumber: string,
): Promise<{ blob: Blob; filename: string }> {
  const html2pdf = await loadHtml2Pdf()
  const filename = `halilov-invoice-${orderNumber}.pdf`
  const blob = await html2pdf().set(pdfOptions(filename)).from(source).outputPdf('blob')
  return { blob, filename }
}

export async function downloadInvoicePdf(source: HTMLElement, orderNumber: string): Promise<void> {
  const html2pdf = await loadHtml2Pdf()
  const filename = `halilov-invoice-${orderNumber}.pdf`
  await html2pdf().set(pdfOptions(filename)).from(source).save()
}
