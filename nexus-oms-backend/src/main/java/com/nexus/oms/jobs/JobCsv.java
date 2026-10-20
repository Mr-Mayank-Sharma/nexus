package com.nexus.oms.jobs;

import java.util.List;

/** Writes CSV that is safe to open in a spreadsheet. */
public final class JobCsv {

    private JobCsv() {}

    public static void row(StringBuilder sb, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        sb.append("\r\n");
    }

    /**
     * A cell that begins with = + - or @ is read by a spreadsheet as a formula, and a file built from other
     * people's data (SKUs, names, error text) can be made to run one. Such cells are prefixed with an apostrophe.
     */
    static String cell(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !isPlainNumber(s)) s = "'" + s;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static boolean isPlainNumber(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    static String errors(List<NxJobRunIssue> issues) {
        StringBuilder sb = new StringBuilder();
        row(sb, "row", "reason", "data");
        for (NxJobRunIssue i : issues) row(sb, i.getRowRef(), i.getReason(), i.getRaw());
        return sb.toString();
    }
}
