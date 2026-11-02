"""Stop hook: asks once per turn for a game test when src/main changed and none has run since.

Only edits made with the file tools in the main conversation are seen, not shell or subagent edits.
"""
import json
import sys

EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
SHELL_TOOLS = {"Bash", "PowerShell"}

REASON = (
    "src/main changed this turn and no client game test has run since. Use the verify-in-game skill, "
    "with -PgameTest=<name> for a quick run. If the change can't affect the game, or the user said not "
    "to test, skip it and say so in your final message, along with what remains unverified."
)


def entries(path):
    with open(path, encoding="utf-8", errors="ignore") as transcript:
        for line in transcript:
            try:
                entry = json.loads(line)
            except ValueError:
                continue
            if isinstance(entry, dict) and not entry.get("isSidechain"):
                yield entry


def starts_turn(entry):
    if entry.get("type") != "user" or entry.get("isMeta"):
        return False
    content = entry.get("message", {}).get("content")
    if isinstance(content, str):
        return True
    return isinstance(content, list) and any(isinstance(p, dict) and p.get("type") == "text" for p in content)


def changes_mod(tool_input):
    path = str(tool_input.get("file_path", "")).replace("\\", "/")
    return "src/main/" in path and "src/main/generated/" not in path


def needs_reminder(path):
    pending = False
    for entry in entries(path):
        if starts_turn(entry):
            pending = False
        elif entry.get("type") == "assistant":
            content = entry.get("message", {}).get("content")
            for part in content if isinstance(content, list) else []:
                if not isinstance(part, dict) or part.get("type") != "tool_use":
                    continue
                tool_input = part.get("input") or {}
                if part.get("name") in EDIT_TOOLS and changes_mod(tool_input):
                    pending = True
                elif part.get("name") in SHELL_TOOLS and "runClientGameTest" in str(tool_input.get("command", "")):
                    pending = False
    return pending


def main():
    payload = json.loads(sys.stdin.buffer.read().decode("utf-8"))
    if not payload.get("stop_hook_active") and needs_reminder(payload["transcript_path"]):
        print(json.dumps({"decision": "block", "reason": REASON}))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, KeyError, ValueError):
        # A broken reminder should never trap a turn
        sys.exit(0)
