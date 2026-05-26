package com.halilov.online.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180-ish CSV reader/writer. Excel-friendly UTF-8 with BOM on write.
 * Quotes any field that contains comma, quote, CR, or LF; escapes embedded quotes by doubling.
 */
public final class Csv {

    private static final char SEP = ',';
    private static final char QUOTE = '"';
    public static final String BOM = "﻿";

    private Csv() {}

    public static String escape(Object v) {
        if (v == null) return "";
        String s = v.toString();
        // Defuse spreadsheet formula injection: customer-controlled fields
        // (name, address, notes) get exported to CSV that we open in Excel
        // for bookkeeping. A leading =, +, -, @, TAB, or CR makes Excel
        // interpret the cell as a formula. Prefix with a single quote so
        // Excel/Sheets treat it as literal text instead.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        boolean needsQuoting = s.indexOf(SEP) >= 0 || s.indexOf(QUOTE) >= 0
            || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
        if (!needsQuoting) return s;
        return QUOTE + s.replace("\"", "\"\"") + QUOTE;
    }

    public static String row(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(escape(fields[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    /** Reads all rows from the stream. Strips UTF-8 BOM if present. Skips fully-empty trailing lines. */
    public static List<List<String>> read(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder field = new StringBuilder();
            List<String> current = new ArrayList<>();
            boolean inQuotes = false;
            boolean atLineStart = true;
            int c;
            while ((c = reader.read()) != -1) {
                // strip UTF-8 BOM at very start
                if (atLineStart && rows.isEmpty() && current.isEmpty() && field.length() == 0 && c == 0xFEFF) {
                    continue;
                }
                atLineStart = false;
                if (inQuotes) {
                    if (c == QUOTE) {
                        int peek = reader.read();
                        if (peek == QUOTE) {
                            field.append(QUOTE);
                        } else {
                            inQuotes = false;
                            if (peek == -1) break;
                            // re-process peeked char
                            handleUnquoted(peek, field, current, rows);
                        }
                    } else {
                        field.append((char) c);
                    }
                } else {
                    if (c == QUOTE && field.length() == 0) {
                        inQuotes = true;
                    } else {
                        handleUnquoted(c, field, current, rows);
                    }
                }
            }
            // flush final field/row
            if (field.length() > 0 || !current.isEmpty()) {
                current.add(field.toString());
                if (!(current.size() == 1 && current.get(0).isEmpty())) {
                    rows.add(current);
                }
            }
        }
        return rows;
    }

    private static void handleUnquoted(int c, StringBuilder field, List<String> current, List<List<String>> rows) {
        if (c == SEP) {
            current.add(field.toString());
            field.setLength(0);
        } else if (c == '\n' || c == '\r') {
            // consume \r\n as one newline
            current.add(field.toString());
            field.setLength(0);
            if (!(current.size() == 1 && current.get(0).isEmpty())) {
                rows.add(new ArrayList<>(current));
            }
            current.clear();
        } else {
            field.append((char) c);
        }
    }
}
