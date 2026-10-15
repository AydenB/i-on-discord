package ninja.as400.wrkactjob;

import java.util.List;

/**
 * Every slash command the bot offers. To add one, add another SqlCommand to this list.
 * See "Adding your own command" in the README.
 */
final class Commands {
    private Commands() {}

    static final List<SqlCommand> ALL = List.of(

        new SqlCommand(
            "wrkactjob",
            "Show the busiest active jobs on the IBM i",
            "SELECT JOB_NAME AS JOB, SUBSYSTEM, JOB_STATUS AS STATUS, JOB_TYPE AS TYPE, "
          + "       COALESCE(ELAPSED_CPU_PERCENTAGE, 0) AS CPU "
          + "  FROM TABLE(QSYS2.ACTIVE_JOB_INFO(DETAILED_INFO => 'NONE')) "
          + " ORDER BY CPU DESC, JOB_NAME "
          + " FETCH FIRST ? ROWS ONLY",
            true)

    );
}
