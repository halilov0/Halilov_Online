package com.halilov.online.auth;

public final class PasswordResetEmailBuilder {

    private PasswordResetEmailBuilder() {}

    public static String subject() {
        return "איפוס סיסמה - חלילוב אונליין";
    }

    public static String html(String userName, String resetUrl, boolean triggeredByAdmin) {
        String name = (userName == null || userName.isBlank()) ? "שלום" : "שלום " + escape(userName);
        String intro = triggeredByAdmin
            ? "צוות חלילוב אונליין יזם איפוס סיסמה לחשבון שלך. לחצו על הכפתור להגדרת סיסמה חדשה:"
            : "קיבלנו בקשה לאיפוס הסיסמה לחשבון שלך. לחצו על הכפתור להגדרת סיסמה חדשה:";
        return "<!doctype html><html dir=\"rtl\" lang=\"he\"><body style=\"margin:0;padding:0;background:#f6f6f6;font-family:Arial,sans-serif;color:#0f1014;direction:rtl;text-align:right\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"background:#f6f6f6;padding:24px 0;direction:rtl\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"max-width:600px;background:#fff;border:1px solid #eee;border-radius:8px;overflow:hidden;direction:rtl\">"
            + "<tr><td style=\"background:#0f1014;color:#fff;padding:20px;text-align:center;direction:rtl\"><h1 style=\"margin:0;font-size:22px;direction:rtl\">חלילוב אונליין</h1></td></tr>"
            + "<tr><td style=\"padding:24px;direction:rtl;text-align:right\">"
            + "<h2 style=\"margin:0 0 12px 0;font-size:20px;direction:rtl;text-align:right\">איפוס סיסמה</h2>"
            + "<p style=\"margin:0 0 16px 0;color:#333;direction:rtl;text-align:right\">" + name + ",</p>"
            + "<p style=\"margin:0 0 16px 0;color:#555;direction:rtl;text-align:right\">" + intro + "</p>"
            + "<div style=\"margin-top:20px;text-align:center\">"
            + "<a href=\"" + escape(resetUrl) + "\" style=\"display:inline-block;background:#0f1014;color:#fff;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600\">איפוס סיסמה</a>"
            + "</div>"
            + "<p style=\"margin-top:24px;font-size:12px;color:#999;direction:rtl;text-align:right\">הקישור תקף שעה אחת. אם לא ביקשת איפוס סיסמה, ניתן להתעלם מההודעה הזו.</p>"
            + "<p style=\"margin-top:8px;font-size:11px;color:#bbb;direction:ltr;text-align:left;word-break:break-all\">" + escape(resetUrl) + "</p>"
            + "</td></tr></table>"
            + "</td></tr></table></body></html>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
