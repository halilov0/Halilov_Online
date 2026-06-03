package com.halilov.online.notification;

import java.util.Locale;

import com.halilov.online.order.Address;
import com.halilov.online.order.Order;
import com.halilov.online.order.OrderItem;

public final class OrderEmailBuilder {

    private OrderEmailBuilder() {}

    public static String subject(Order order) {
        return "אישור הזמנה " + order.getOrderNumber() + " - חלילוב אונליין";
    }

    public static String html(Order order, Address shipping, String customerName, String siteBaseUrl,
                              boolean manualReceiptNotice) {
        StringBuilder rows = new StringBuilder();
        for (OrderItem oi : order.getItems()) {
            rows.append("<tr>")
                .append("<td style=\"padding:8px;border-bottom:1px solid #eee;text-align:right\">").append(escape(oi.getNameHe())).append("<br><span style=\"color:#888;font-size:12px\">").append(escape(oi.getSku())).append("</span></td>")
                .append("<td style=\"padding:8px;border-bottom:1px solid #eee;text-align:center\">").append(oi.getQuantity()).append("</td>")
                .append("<td style=\"padding:8px;border-bottom:1px solid #eee;text-align:left;font-family:monospace\">").append(money(oi.getLineTotalAgorot())).append("</td>")
                .append("</tr>");
        }

        String address = shipping == null ? "" :
            escape(shipping.getFullName()) + "<br>" +
            escape(shipping.getStreet()) + " " + escape(nullToEmpty(shipping.getHouseNo())) +
            (shipping.getApartment() != null && !shipping.getApartment().isBlank() ? "/" + escape(shipping.getApartment()) : "") + "<br>" +
            escape(shipping.getCity()) + (shipping.getPostalCode() != null ? " " + escape(shipping.getPostalCode()) : "") + "<br>" +
            escape(shipping.getPhone());

        String ctaUrl = buildOrderUrl(siteBaseUrl, order);

        return "<!doctype html><html dir=\"rtl\" lang=\"he\"><body style=\"margin:0;padding:0;background:#f6f6f6;font-family:Arial,sans-serif;color:#0f1014;direction:rtl;text-align:right\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"background:#f6f6f6;padding:24px 0;direction:rtl\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"max-width:600px;background:#fff;border:1px solid #eee;border-radius:8px;overflow:hidden;direction:rtl\">"
            + "<tr><td style=\"background:#0f1014;color:#fff;padding:20px;text-align:center;direction:rtl\"><h1 style=\"margin:0;font-size:22px;direction:rtl\">חלילוב אונליין</h1></td></tr>"
            + "<tr><td style=\"padding:24px;direction:rtl;text-align:right\">"
            + "<h2 style=\"margin:0 0 8px 0;font-size:20px;direction:rtl;text-align:right\">תודה על ההזמנה, " + escape(customerName) + "!</h2>"
            + "<p style=\"margin:0 0 16px 0;color:#555;direction:rtl;text-align:right\">קיבלנו את התשלום וההזמנה שלך בעיבוד.</p>"
            + (manualReceiptNotice
                ? "<p style=\"margin:0 0 16px 0;color:#555;direction:rtl;text-align:right\">הקבלה הרשמית תישלח אליך במייל בנפרד תוך יום עסקים.</p>"
                : "")
            + "<p style=\"margin:0 0 16px 0;direction:rtl;text-align:right\"><strong>מספר הזמנה:</strong> <span style=\"font-family:monospace\">" + escape(order.getOrderNumber()) + "</span></p>"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"border-collapse:collapse;margin:16px 0;direction:rtl\">"
            + "<thead><tr style=\"background:#fafafa\">"
            + "<th style=\"padding:8px;text-align:right;font-size:13px;color:#666\">פריט</th>"
            + "<th style=\"padding:8px;text-align:center;font-size:13px;color:#666\">כמות</th>"
            + "<th style=\"padding:8px;text-align:left;font-size:13px;color:#666\">סכום</th>"
            + "</tr></thead><tbody>" + rows + "</tbody></table>"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"border-collapse:collapse;margin-top:8px;direction:rtl\">"
            + summaryRow("סכום ביניים", money(order.getSubtotalAgorot()))
            + (order.getDiscountAgorot() > 0
                ? summaryRow("הנחה" + (order.getCouponCode() != null ? " (" + escape(order.getCouponCode()) + ")" : ""),
                             "-" + money(order.getDiscountAgorot()))
                : "")
            + summaryRow("משלוח", order.getShippingAgorot() == 0 ? "חינם" : money(order.getShippingAgorot()))
            + "<tr><td style=\"padding:12px 8px;border-top:2px solid #0f1014;font-weight:bold;font-size:16px;text-align:right\">סה\"כ</td>"
            + "<td style=\"padding:12px 8px;border-top:2px solid #0f1014;font-weight:bold;font-size:16px;text-align:left;font-family:monospace\">" + money(order.getTotalAgorot()) + "</td></tr>"
            + "</table>"
            + (address.isEmpty() ? "" :
                "<div style=\"margin-top:24px;padding:16px;background:#fafafa;border-radius:6px;direction:rtl;text-align:right\">"
                + "<div style=\"font-size:13px;color:#666;margin-bottom:6px\">כתובת למשלוח</div>"
                + "<div style=\"line-height:1.6\">" + address + "</div>"
                + "</div>")
            + (ctaUrl.isEmpty() ? "" :
                "<div style=\"margin-top:24px;text-align:center\">"
                + "<a href=\"" + escape(ctaUrl) + "\" style=\"display:inline-block;background:#0f1014;color:#fff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600\">לצפייה בהזמנה</a>"
                + "</div>")
            + "<p style=\"margin-top:24px;font-size:12px;color:#999;text-align:center\">תודה שקנית בחלילוב אונליין.</p>"
            + "</td></tr></table>"
            + "</td></tr></table></body></html>";
    }

    public static String cancelSubject(Order order) {
        return "ביטול הזמנה " + order.getOrderNumber() + " - חלילוב אונליין";
    }

    public static String cancelHtml(Order order, String customerName, boolean wasPaid, String siteBaseUrl) {
        String body = wasPaid
            ? "ההזמנה שלך בוטלה. הזיכוי במלוא הסכום של "
              + money(order.getTotalAgorot())
              + " יזוכה לאמצעי התשלום שלך תוך 3-7 ימי עסקים."
            : "ההזמנה שלך בוטלה לפני התשלום. לא חויבת.";
        return statusEmail(
            "ההזמנה בוטלה",
            customerName,
            body,
            order,
            siteBaseUrl
        );
    }

    public static String refundSubject(Order order) {
        return "החזר תשלום להזמנה " + order.getOrderNumber() + " - חלילוב אונליין";
    }

    public static String refundHtml(Order order, String customerName, int amountAgorot, String siteBaseUrl) {
        boolean partial = amountAgorot < order.getTotalAgorot();
        String body = (partial ? "בוצע החזר חלקי בסך " : "בוצע החזר מלא בסך ")
            + money(amountAgorot)
            + ". הסכום יזוכה לאמצעי התשלום שלך תוך 3-7 ימי עסקים.";
        return statusEmail(
            partial ? "החזר חלקי" : "החזר תשלום",
            customerName,
            body,
            order,
            siteBaseUrl
        );
    }

    private static String statusEmail(String heading, String customerName, String body, Order order, String siteBaseUrl) {
        String ctaUrl = buildOrderUrl(siteBaseUrl, order);
        return "<!doctype html><html dir=\"rtl\" lang=\"he\"><body style=\"margin:0;padding:0;background:#f6f6f6;font-family:Arial,sans-serif;color:#0f1014;direction:rtl;text-align:right\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"background:#f6f6f6;padding:24px 0;direction:rtl\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"max-width:600px;background:#fff;border:1px solid #eee;border-radius:8px;overflow:hidden;direction:rtl\">"
            + "<tr><td style=\"background:#0f1014;color:#fff;padding:20px;text-align:center;direction:rtl\"><h1 style=\"margin:0;font-size:22px;direction:rtl\">חלילוב אונליין</h1></td></tr>"
            + "<tr><td style=\"padding:24px;direction:rtl;text-align:right\">"
            + "<h2 style=\"margin:0 0 8px 0;font-size:20px;direction:rtl;text-align:right\">" + escape(heading) + ", " + escape(customerName) + "</h2>"
            + "<p style=\"margin:0 0 16px 0;color:#555;direction:rtl;text-align:right\">" + escape(body) + "</p>"
            + "<p style=\"margin:0 0 16px 0;direction:rtl;text-align:right\"><strong>מספר הזמנה:</strong> <span style=\"font-family:monospace\">" + escape(order.getOrderNumber()) + "</span></p>"
            + (ctaUrl.isEmpty() ? "" :
                "<div style=\"margin-top:24px;text-align:center\">"
                + "<a href=\"" + escape(ctaUrl) + "\" style=\"display:inline-block;background:#0f1014;color:#fff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600\">לצפייה בהזמנה</a>"
                + "</div>")
            + "<p style=\"margin-top:24px;font-size:12px;color:#999;text-align:center\">לשאלות אפשר להשיב למייל הזה.</p>"
            + "</td></tr></table>"
            + "</td></tr></table></body></html>";
    }

    private static String summaryRow(String label, String value) {
        return "<tr><td style=\"padding:6px 8px;color:#555;text-align:right\">" + escape(label) + "</td>"
            + "<td style=\"padding:6px 8px;text-align:left;font-family:monospace\">" + value + "</td></tr>";
    }

    private static String money(int agorot) {
        double v = agorot / 100.0;
        return String.format(Locale.US, "₪%.2f", v);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
     * Build the customer-facing order URL. Appends the guest token for
     * orders without a linked user so the link survives across tabs /
     * devices — sessionStorage alone would leave guests staring at
     * "Unauthorized" if they re-open the link.
     */
    private static String buildOrderUrl(String siteBaseUrl, Order order) {
        if (siteBaseUrl == null || siteBaseUrl.isBlank()) return "";
        String base = siteBaseUrl.trim().replaceAll("/+$", "") + "/orders/" + order.getOrderNumber();
        return order.getGuestToken() == null
            ? base
            : base + "?t=" + order.getGuestToken();
    }
}
