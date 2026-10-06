#!/usr/bin/env bash
# Definition-of-Done gate for the offer-management specification.
#
# Every row of the DoD table in spec.md §11 is one check here, plus its
# negative twin: a check that cannot fail proves nothing.
#
# Usage:  ./check-spec.sh [storybook-url]
# Exit:   0 only when every positive check passes AND every negative twin fails.
set -uo pipefail

SPEC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SPEC_DIR/../../.." && pwd)"
STORYBOOK_URL="${1:-http://localhost:6007}"
STORYBOOK_DIR="$REPO_ROOT/.storybook"

pass=0; fail=0
row() { printf '%-6s %-46s %s\n' "$1" "$2" "$3"; }
check() { # id, description, expected, actual, comparator
  local id="$1" desc="$2" expected="$3" actual="$4"
  if [ "$expected" = "$actual" ]; then row "$id" "$desc" "PASS ($actual)"; pass=$((pass+1))
  else row "$id" "$desc" "FAIL (expected $expected, got $actual)"; fail=$((fail+1)); fi
}

echo "DoD gate — $SPEC_DIR"
echo "-----------------------------------------------------------------------"

# ── helper: structural facts, printed as `key=value` ────────────────────────
structural() {
  python3 - "$SPEC_DIR" <<'PY'
import re, os, sys, json, yaml, glob
d = sys.argv[1]
os.chdir(d)

# artifact: every element file named in the index exists, and vice versa
spec = open('spec.md').read()
indexed = re.findall(r'\[\d\d-[a-z-]+\.md\]\((\d\d-[a-z-]+\.md)\)', spec)
missing = [f for f in indexed if not os.path.exists(f)]
present = sorted(glob.glob('[0-9][0-9]-*.md'))
unindexed = [f for f in present if f not in indexed]

# rules: defined exactly once, contiguous
defined = {}
for f in glob.glob('**/*.md', recursive=True):
    for m in re.findall(r'RULE-(\d+):', open(f).read()):
        defined.setdefault(int(m), []).append(f)
nums = sorted(defined)
gaps = [i for i in range(1, max(nums) + 1) if i not in nums] if nums else ['all']
dupes = [n for n, fs in defined.items() if len(fs) > 1]
referenced = set()
for f in glob.glob('**/*.md', recursive=True):
    referenced |= {int(x) for x in re.findall(r'RULE-(\d+)', open(f).read())}
undefined = sorted(referenced - set(nums))

# links
broken = []
for f in glob.glob('**/*.md', recursive=True):
    for t in re.findall(r'\]\(([^)]+)\)', open(f).read()):
        if t.startswith(('http', '#')): continue
        if not os.path.exists(os.path.normpath(os.path.join(os.path.dirname(f), t))): broken.append(f'{f}->{t}')

# code blocks
badblocks = 0
for f in glob.glob('**/*.md', recursive=True):
    s = open(f).read()
    for b in re.findall(r'```yaml\n(.*?)\n```', s, re.S):
        try: yaml.safe_load(b)
        except Exception: badblocks += 1
    for b in re.findall(r'```json\n(.*?)\n```', s, re.S):
        try: json.loads(b)
        except Exception: badblocks += 1

# error codes: every code a decision table denies with must exist in the API table
api = open('02-frontend-api.md').read()
api_codes = set(re.findall(r'^\| `([A-Z_]+)` \|', api, re.M))
decisions = "\n".join(open(f).read() for f in glob.glob('*.md') if f != 'spec.md')
used = set(re.findall(r'`\d{3} ([A-Z_]+)`', decisions))
undeclared = sorted(used - api_codes)

print(f"element_files={len(present)}")
print(f"index_missing_or_orphan={len(missing) + len(unindexed)}")
print(f"rules={len(nums)}")
print(f"rule_gaps={len(gaps)}")
print(f"rule_duplicates={len(dupes)}")
print(f"rule_undefined={len(undefined)}")
print(f"broken_links={len(broken)}")
print(f"unparseable_blocks={badblocks}")
print(f"undeclared_error_codes={len(undeclared)}")
PY
}

read_facts() { structural | sed -n "s/^$1=//p"; }

# ── positive checks ─────────────────────────────────────────────────────────
check D1 "every element file exists and is indexed"      0 "$(read_facts index_missing_or_orphan)"
check D2 "every RULE-n defined exactly once"             0 "$(read_facts rule_duplicates)"
check D3 "no gap in the RULE-n range"                    0 "$(read_facts rule_gaps)"
check D4 "no RULE-n referenced but undefined"            0 "$(read_facts rule_undefined)"
check D5 "every relative link resolves"                  0 "$(read_facts broken_links)"
check D6 "every fenced yaml/json block parses"           0 "$(read_facts unparseable_blocks)"
check D7 "every denied error code is declared in the API" 0 "$(read_facts undeclared_error_codes)"

hurlfmt --check "$SPEC_DIR/prototypes/frontend-api.hurl" >/dev/null 2>&1
check D8 "hurlfmt --check on the API contract"           0 "$?"

pairs=$(grep -cE '^HTTP ' "$SPEC_DIR/prototypes/frontend-api.hurl")
check D9 "API contract carries 41 request/response pairs" 41 "$pairs"

shots=$(ls "$SPEC_DIR"/prototypes/ui-mockups/*.png 2>/dev/null | wc -l | tr -d ' ')
check D10 "one screenshot per mockup story"              9 "$shots"

# ── negative twins: the gate must fail when the artifact is wrong ───────────
# Each twin mutates the real folder through a scratch file and asserts the gate
# reacts, then removes it — so no environment-dependent links are disturbed.
check_min() { # id, description, minimum, actual
  local id="$1" desc="$2" min="$3" actual="$4"
  if [ "$actual" -ge "$min" ] 2>/dev/null; then row "$id" "$desc" "PASS ($actual)"
  else row "$id" "$desc" "FAIL (expected >= $min, got $actual)"; fail=$((fail+1)); fi
}

SCRATCH="$SPEC_DIR/_dod-scratch.md"

printf '\n- RULE-1: a deliberately duplicated definition\n' > "$SCRATCH"
check_min D2N "duplicated RULE definition is caught"    1 "$(read_facts rule_duplicates)"

printf '\n[broken](nowhere/at-all.md)\n' > "$SCRATCH"
check_min D5N "broken link is caught"                   1 "$(read_facts broken_links)"

printf '\n| `999 NOT_A_REAL_CODE` | deny |\n' > "$SCRATCH"
check_min D7N "undeclared error code is caught"         1 "$(read_facts undeclared_error_codes)"

printf '\n```json\n{ this is not json }\n```\n' > "$SCRATCH"
check_min D6N "unparseable block is caught"             1 "$(read_facts unparseable_blocks)"

rm -f "$SCRATCH"
check D0N "gate is clean again after the twins"         0 "$(( $(read_facts broken_links) + $(read_facts rule_duplicates) + $(read_facts undeclared_error_codes) + $(read_facts unparseable_blocks) ))"

# ── the mockup gate (needs the gallery running) ─────────────────────────────
if curl -sf -o /dev/null "$STORYBOOK_URL/index.json"; then
  gate=$(cd "$STORYBOOK_DIR" && NODE_PATH="$STORYBOOK_DIR/node_modules" \
        node ./verify-storybook.cjs "$STORYBOOK_URL" 2>&1 | grep -cE 'QUALITY GATE PASSED')
  check D11 "storybook CDP gate passes (stories + flows)" 1 "$gate"
else
  row D11 "storybook CDP gate passes (stories + flows)" "SKIP (no gallery at $STORYBOOK_URL)"
fi

echo "-----------------------------------------------------------------------"
echo "passed: $pass   failed: $fail"
[ "$fail" -eq 0 ] || exit 1
