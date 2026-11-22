"""PostToolUse hook: flags Java edits that bring back a bug this mod has already hit."""
import json
import re
import sys

KNOWN_BUGS = [
    (re.compile(r"\.button\(\)\s*[=!]=\s*\d"),
     "literal mouse button number. 26.3 renumbered them (left is 1, right is 3), so compare with "
     "InputConstants.MOUSE_BUTTON_LEFT and friends. See 6137f55."),
    (re.compile(r"RenderTypes\.lightning\("),
     "RenderTypes.lightning() writes depth and re-sorts its quads, which flickers as the camera moves. "
     "Use VoidHeartRenderTypes.GLOW. See 27f73ad."),
    (re.compile(r"System\.(nanoTime|currentTimeMillis)\(\)"),
     "wall clock. If this times an animation, fade or glide, use Util.getNanos() or Util.getMillis(): client game "
     "tests run on a virtual clock through Util, and a wall-clock animation falls behind there. Measuring real cost "
     "is fine."),
    (re.compile(r"addBox\([^()]*,\s*0F?(,\s*[\d.]+F?){0,2}\)"),
     "flat model box drawing all its faces. Entity cutout culls nothing since 26.3, so both faces of a flat box show "
     "in one plane and fight. Pass the one face to keep, such as ModelBoxes.FRONT. MobModelGeometryTest checks this."),
    (re.compile(r"addOrUpdateTransientModifier\("),
     "AttributeInstance.addOrUpdateTransientModifier keeps the old modifier counted under its old operation when the "
     "operation changes, so the attribute stays wrong though getModifiers() looks right. If one id ever changes "
     "operation, removeModifier(id) first, as ArmorWear.set does."),
    (re.compile(r"(\w+)\.getStack\(\)\.copyWithCount\(1\)[\s\S]{0,300}?\b\1\.setStack\(ItemStack\.EMPTY\)"),
     "one item moved, then the whole source emptied. A Shatter Forge output can hold two items, and the Inventory "
     "Inserter lost one of every pair this way. Shrink the source by what was moved instead."),
]


def main():
    payload = json.loads(sys.stdin.buffer.read().decode("utf-8"))
    tool_input = payload.get("tool_input", {})
    if not str(tool_input.get("file_path", "")).endswith(".java"):
        return 0

    # Only the new text is checked, so editing near an old problem doesn't report it
    text = tool_input.get("new_string") or tool_input.get("content") or ""
    problems = [message for pattern, message in KNOWN_BUGS if pattern.search(text)]
    if not problems:
        return 0

    print(tool_input["file_path"] + ":", file=sys.stderr)
    for problem in problems:
        print("- " + problem, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
