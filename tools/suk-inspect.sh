#!/usr/bin/env bash
# Look inside the running test pack: Sims, buildings, boxes and settings, read live through the JDWP debug port
# (127.0.0.1:5005, see the instance JvmArgs). Reading never pauses the game.
#
#   suk-inspect.sh folks [filter]        every Sim: job, workplace, action, happiness, position, status
#   suk-inspect.sh folk <name>           every field of one Sim, and of their job
#   suk-inspect.sh buildings             every building: type, position, constructor box, done, tenants
#   suk-inspect.sh boxes                 mining and farming boxes
#   suk-inspect.sh states                credits, game mode, day of week...
#   suk-inspect.sh get <path>            any static field path, e.g. SimukraftReloaded.serverFolks[0].theirJob
#   suk-inspect.sh set <path> <value>    set a number/boolean/string field, e.g. SimukraftReloaded.serverStates.credits 150
#   suk-inspect.sh cmd <command>         run a server command as the console (needs the game unpaused)
#   suk-inspect.sh path <name> [x y z]   the route a Sim would take to x,y,z (default: their work), on a map (needs the game unpaused)
#   suk-inspect.sh log [n]               the mod's last n log lines (default 30)
#   suk-inspect.sh quits                 every Sim that quit or was fired this session, and which code did it
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
INSTANCE="${SUK_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/OneSevenTenPack-J21/minecraft}"
PORT="${SUK_DEBUG_PORT:-5005}"
JAVA="${SUK_JAVA:-$HOME/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2/bin/java}"
LOG="$INSTANCE/logs/fml-client-latest.log"

case "${1:-}" in
    log)
        grep -a '\[SimUKraft/\]' "$LOG" | tail -n "${2:-30}" | sed 's/ \[Server thread\/[A-Z]*\] \[SimUKraft\/\]://'
        ;;
    quits)
        grep -a 'quit their job' "$LOG" | sed 's/ \[Server thread\/[A-Z]*\] \[SimUKraft\/\]: FolkData://' || echo "Nobody has quit this session."
        ;;
    ""|-h|--help)
        sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
        ;;
    *)
        exec "$JAVA" --add-modules jdk.jdi "$REPO/tools/inspect/SukInspect.java" "$PORT" "$@"
        ;;
esac
