package ninja.as400.wrkactjob;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discord bot that turns slash commands into read-only IBM i SQL queries.
 * The commands themselves are listed in {@link ninja.as400.wrkactjob.Commands}.
 */
public class Bot extends ListenerAdapter {

    private final Config cfg;
    private final Map<String, SqlCommand> commands = new HashMap<>();

    private Bot(Config cfg) {
        this.cfg = cfg;
        for (SqlCommand c : ninja.as400.wrkactjob.Commands.ALL) commands.put(c.name(), c);
    }

    public static void main(String[] args) throws Exception {
        Config cfg = Config.load();
        Bot bot = new Bot(cfg);

        JDA jda = JDABuilder.createLight(cfg.token).addEventListeners(bot).build().awaitReady();

        List<CommandData> defs = bot.commands.values().stream().map(Bot::toDiscord).toList();

        // A guild id makes commands appear instantly. Without it, global commands can take up to an hour.
        if (!cfg.guildId.isEmpty()) {
            Guild guild = jda.getGuildById(cfg.guildId);
            if (guild == null) throw new IllegalStateException("Bot is not in guild " + cfg.guildId);
            guild.updateCommands().addCommands(defs).queue();
        } else {
            jda.updateCommands().addCommands(defs).queue();
        }
    }

    private static CommandData toDiscord(SqlCommand c) {
        SlashCommandData data = Commands.slash(c.name(), c.description());
        if (c.takesCount()) {
            data.addOption(OptionType.INTEGER, "count",
                "How many rows to show (1-" + SqlCommand.MAX_ROWS + ")", false);
        }
        return data;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SqlCommand cmd = commands.get(event.getName());
        if (cmd == null) return;

        int n = event.getOption("count") == null ? SqlCommand.DEFAULT_ROWS
              : (int) Math.max(1, Math.min(SqlCommand.MAX_ROWS, event.getOption("count").getAsLong()));

        // The query can take a moment, so acknowledge first.
        event.deferReply(cfg.ephemeral).queue();
        try {
            event.getHook().sendMessage(cmd.run(cfg, n)).queue();
        } catch (Exception e) {
            // Keep the reply generic. Details go to the console, not to the channel.
            e.printStackTrace();
            event.getHook().sendMessage("Could not run /" + cmd.name() + " on the IBM i. Check the bot's console.").queue();
        }
    }
}
