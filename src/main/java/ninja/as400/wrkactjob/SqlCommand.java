package ninja.as400.wrkactjob;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * One Discord slash command backed by one fixed, read-only SQL query.
 *
 * @param name        slash command name: lowercase letters, digits, "-" or "_", max 32
 * @param description shown in Discord's command picker, max 100 characters
 * @param sql         a SELECT (or WITH) statement. Column aliases become the table headers.
 * @param takesCount  if true, the command gets an optional "count" option and the SQL
 *                    must contain exactly one "?" (for example FETCH FIRST ? ROWS ONLY)
 */
record SqlCommand(String name, String description, String sql, boolean takesCount) {

    static final int DEFAULT_ROWS = 10;
    static final int MAX_ROWS = 25;

    private static final int MAX_COLUMN_WIDTH = 28;
    private static final int MAX_READ_ROWS = 100;
    private static final int DISCORD_LIMIT = 1900; // messages max out at 2000 characters

    SqlCommand {
        String s = sql.stripLeading().toLowerCase();
        if (!s.startsWith("select") && !s.startsWith("with")) {
            throw new IllegalArgumentException(name + ": only SELECT or WITH statements are allowed");
        }
        long marks = sql.chars().filter(ch -> ch == '?').count();
        if (marks != (takesCount ? 1 : 0)) {
            throw new IllegalArgumentException(name + ": takesCount=" + takesCount
                + " needs " + (takesCount ? 1 : 0) + " '?' in the SQL, found " + marks);
        }
    }

    /** Runs the query on the IBM i and returns a Discord-ready table. */
    String run(Config cfg, int count) throws SQLException {
        Properties props = new Properties();
        props.put("user", cfg.user);
        props.put("password", cfg.pass);
        props.put("naming", "sql");
        props.put("login timeout", "15");
        if (cfg.secure) props.put("secure", "true");

        try (Connection c = DriverManager.getConnection("jdbc:as400://" + cfg.host, props)) {
            c.setReadOnly(true);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (takesCount) ps.setInt(1, count);
                try (ResultSet rs = ps.executeQuery()) {
                    return format(rs);
                }
            }
        }
    }

    private static String format(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        String[] header = new String[cols];
        int[] width = new int[cols];
        for (int i = 0; i < cols; i++) {
            header[i] = md.getColumnLabel(i + 1);
            width[i] = Math.min(MAX_COLUMN_WIDTH, header[i].length());
        }

        List<String[]> rows = new ArrayList<>();
        while (rows.size() < MAX_READ_ROWS && rs.next()) {
            String[] row = new String[cols];
            for (int i = 0; i < cols; i++) {
                String v = rs.getString(i + 1);
                row[i] = v == null ? "" : v.trim();
                width[i] = Math.max(width[i], Math.min(MAX_COLUMN_WIDTH, row[i].length()));
            }
            rows.add(row);
        }

        StringBuilder sb = new StringBuilder(line(header, width));
        int shown = 0;
        for (String[] row : rows) {
            String next = line(row, width);
            if (sb.length() + next.length() > DISCORD_LIMIT) break;
            sb.append(next);
            shown++;
        }
        if (rows.isEmpty()) sb.append("(no rows)\n");
        if (shown < rows.size()) sb.append("... ").append(rows.size() - shown).append(" more rows not shown\n");
        return "```\n" + sb + "```";
    }

    private static String line(String[] cells, int[] width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            String v = cells[i].length() > width[i] ? cells[i].substring(0, width[i]) : cells[i];
            // The last column is not padded, so lines have no trailing spaces.
            sb.append(i == cells.length - 1 ? v : String.format("%-" + width[i] + "s  ", v));
        }
        return sb.append('\n').toString();
    }
}
