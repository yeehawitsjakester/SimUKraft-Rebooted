#!/usr/bin/env bash
# Build the mod and swap the changed classes into the running test pack (JDWP on port 5005, see instance JvmArgs).
# The installed jar is replaced too (by rename, so the running game keeps its open copy) so a restart picks it up.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
INSTANCE="${SUK_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/OneSevenTenPack-J21/minecraft}"
INSTALLED="$INSTANCE/mods/ashjacksimukraftreloaded-dev.jar"
PORT="${SUK_DEBUG_PORT:-5005}"
JAVA="${SUK_JAVA:-$HOME/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2/bin/java}"

cd "$REPO"
echo "Building..."
./gradlew --no-configuration-cache -q assemble
NEW="$(ls -t build/libs/*.jar | grep -vE -- '-(sources|dev)\.jar$' | head -1)"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir "$WORK/old" "$WORK/new"
unzip -q "$INSTALLED" -d "$WORK/old"
unzip -q "$NEW" -d "$WORK/new"

changed=(); added=(); other=0
while IFS= read -r f; do
    rel="${f#"$WORK/new/"}"
    if [[ ! -e "$WORK/old/$rel" ]]; then
        [[ "$rel" == *.class ]] && added+=("$rel") || other=1
    elif ! cmp -s "$f" "$WORK/old/$rel"; then
        [[ "$rel" == *.class ]] && changed+=("$rel") || other=1
    fi
done < <(find "$WORK/new" -type f ! -path '*/META-INF/*')

if (( ${#changed[@]} == 0 && ${#added[@]} == 0 && other == 0 )); then
    echo "Nothing changed since the installed jar."
    exit 0
fi
(( ${#added[@]} )) && echo "New classes (need a restart to exist in the game): ${added[*]}"
(( other )) && echo "Textures/sounds/lang changed: those need a restart."

status=0
if (( ${#changed[@]} )); then
    names=()
    for c in "${changed[@]}"; do n="${c%.class}"; names+=("${n//\//.}"); done
    "$JAVA" "$REPO/tools/hotswap/HotSwap.java" "$PORT" "$WORK/new" "${names[@]}" || status=$?
fi

if (( status == 2 )); then
    echo "Game not running: installing the jar for the next launch."
fi
cp "$NEW" "$INSTALLED.swap"
mv -f "$INSTALLED.swap" "$INSTALLED"
echo "Installed jar updated."
exit $status
