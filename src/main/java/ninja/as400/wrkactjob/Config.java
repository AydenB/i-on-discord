package ninja.as400.wrkactjob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings from environment variables. A ".env" file in the current folder is
 * read too, so no shell setup is needed. A real environment variable wins over
 * the file.
 */
final class Config {
    final String token;
    final String host;
    final String user;
    final String pass;
    final String guildId;      // optional, blank means global commands
    final boolean secure;      // use SSL for the IBM i connection
    final boolean ephemeral;   // replies only visible to the person who ran the command

    private final Map<String, String> file;

    private Config() {
        file = readDotEnv(Path.of(".env"));
        token = require("DISCORD_TOKEN");
        host = require("IBMI_HOST");
        user = require("IBMI_USER");
        pass = require("IBMI_PASS");
        guildId = get("DISCORD_GUILD_ID");
        secure = "true".equalsIgnoreCase(get("IBMI_SECURE"));
        ephemeral = !"true".equalsIgnoreCase(get("REPLY_PUBLIC"));
    }

    static Config load() {
        return new Config();
    }

    private String get(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = file.get(name);
        return v == null ? "" : v.trim();
    }

    private String require(String name) {
        String v = get(name);
        if (v.isEmpty()) {
            System.err.println("Missing setting: " + name + " (see .env.example)");
            System.exit(1);
        }
        return v;
    }

    /** Minimal .env parser: KEY=value lines, # comments, optional quotes. */
    private static Map<String, String> readDotEnv(Path path) {
        Map<String, String> out = new HashMap<>();
        if (!Files.isRegularFile(path)) return out;
        try {
            for (String line : Files.readAllLines(path)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                        && value.charAt(value.length() - 1) == value.charAt(0)) {
                    value = value.substring(1, value.length() - 1);
                }
                out.put(line.substring(0, eq).trim(), value);
            }
        } catch (IOException e) {
            System.err.println("Could not read .env: " + e.getMessage());
        }
        return out;
    }
}
