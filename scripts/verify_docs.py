#!/usr/bin/env python3
"""Fail if the documentation's verifiable claims drift from the source tree.

Two failure modes are covered, because both actually happened:

1. Source changed, docs did not. A class or migration was added and the
   documented count stayed at the old value.
2. Docs changed, source did not. A number was "updated" by hand and now
   disagrees with the code.

Every check recomputes a value from the tree, then requires that the prose
still contains that exact value. A wrong hand-edited number therefore fails
just as loudly as a stale one.

Also runs the structural Markdown checks: broken local links, dead anchors,
absolute-path leaks, ragged table rows, and multi-line table cells (which GFM
cannot represent, so they silently truncate the table).

Stdlib only. Python 3.9+. Run: python3 scripts/verify_docs.py
Add --verbose for per-check detail, --no-surefire to skip test-report checks.
"""

import argparse
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "nexus-oms-backend")
FRONTEND = os.path.join(ROOT, "nexus-oms-frontend")
MIGRATIONS = os.path.join(BACKEND, "src", "main", "resources", "db", "migration")
SUREFIRE = os.path.join(BACKEND, "target", "surefire-reports")

VERBOSE = False


def walk(root, suffix):
    return glob.glob(os.path.join(root, "**", "*" + suffix), recursive=True)


def read(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def count_files_with_token(root, token, suffix=".java"):
    """Files under `root` containing `token`. Token = a real marker, not a substring."""
    return sum(1 for p in walk(root, suffix) if token in read(p))


def count_lines_with(patterns, root, suffix):
    """Total lines under `root` matching any regex in `patterns`."""
    rx = re.compile("|".join(patterns))
    total = 0
    for p in walk(root, suffix):
        total += sum(1 for ln in read(p).splitlines() if rx.search(ln))
    return total


# ── source-derived values ────────────────────────────────────────────────────

def src_jpa_entities():
    return count_files_with_token(os.path.join(BACKEND, "src", "main", "java"), "@Entity")


def src_migrations():
    return len([p for p in walk(MIGRATIONS, ".sql")
                if os.path.basename(p).startswith("V")])


def src_permission_paths():
    path = os.path.join(BACKEND, "src", "main", "java", "com", "nexus", "oms",
                        "service", "PermissionService.java")
    return len(re.findall(r"PATH_TO_RESOURCE\.put\(", read(path)))


def src_permission_unique_prefixes():
    path = os.path.join(BACKEND, "src", "main", "java", "com", "nexus", "oms",
                        "service", "PermissionService.java")
    found = re.findall(r'PATH_TO_RESOURCE\.put\(\s*"([^"]+)"', read(path))
    return len(found), len(set(found))


def src_rest_controllers():
    return count_files_with_token(
        os.path.join(BACKEND, "src", "main", "java"), "@RestController")


def src_services():
    return count_files_with_token(
        os.path.join(BACKEND, "src", "main", "java"), "@Service")


def src_frontend_routes():
    app = os.path.join(FRONTEND, "src", "App.tsx")
    return len(re.findall(r'\bpath="', read(app)))


def src_frontend_api_modules():
    api = os.path.join(FRONTEND, "src", "api")
    return len(glob.glob(os.path.join(api, "*.ts")))


def src_vitest_cases():
    patterns = [r"\bit\(", r"\btest\("]
    return count_lines_with(patterns, os.path.join(FRONTEND, "src"), ".test.ts") + \
        count_lines_with(patterns, os.path.join(FRONTEND, "src"), ".test.tsx")


def src_backend_test_files():
    return len(glob.glob(os.path.join(BACKEND, "src", "test", "**", "*Test.java"),
                         recursive=True))


def src_surefire(class_name=None):
    """Totals from surefire XML. Returns (tests, failures, errors, skipped)."""
    if not os.path.isdir(SUREFIRE):
        return None
    tests = failures = errors = skipped = 0
    for p in glob.glob(os.path.join(SUREFIRE, "*.xml")):
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        name = root.get("name", "")
        if class_name and name.split(".")[-1] != class_name:
            continue
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    return tests, failures, errors, skipped


# ── documented claims ─────────────────────────────────────────────────────────
# Each entry: id -> (label, computed_value, [(file, template), ...])
# `{v}` in a template is replaced with the computed value; the resulting string
# must appear verbatim in that file. Use `{v}` with surrounding text to pin the
# exact phrasing so "789" cannot pass as "78" or "7890".

CLAIMS = [
    ("jpa-entities", "JPA @Entity classes",
     lambda: src_jpa_entities(),
     [("docs/01-CURRENT-STATE.md", "**{v} JPA entities**"),
      ("docs/MASTER-ONBOARDING-GUIDE.md", "{v} JPA entities"),
      ("docs/NEXUS-DEVELOPER-ONBOARDING-AND-SYSTEM-GUIDE.md", "{v} Hiber")]),

    ("migrations", "Flyway migration files",
     lambda: src_migrations(),
     [("docs/01-CURRENT-STATE.md", "| **Migrations** | {v} Flyway migrations"),
      ("docs/03-ER-DIAGRAM.md", "| Schema migrations | {v} Flyway files")]),

    ("permission-paths", "PermissionService path mappings",
     lambda: src_permission_paths(),
     [("docs/06-BUSINESS-FLOW-RBAC.md", "PATH_TO_RESOURCE` ({v} mappings"),
      ("docs/05-DATA-FLOW.md", "PermissionAuthorizationFilter` ({v} mappings)")]),

    ("rest-controllers", "@RestController classes",
     lambda: src_rest_controllers(),
     [("docs/01-CURRENT-STATE.md", "**{v} controllers**"),
      ("docs/MASTER-ONBOARDING-GUIDE.md", "{v} REST controllers")]),

    ("services", "@Service classes",
     lambda: src_services(),
     [("docs/01-CURRENT-STATE.md", "{v} services"),
      ("docs/MASTER-ONBOARDING-GUIDE.md", "{v} services (business logic)")]),

    ("frontend-routes", "Frontend routes (path= in App.tsx)",
     lambda: src_frontend_routes(),
     [("docs/01-CURRENT-STATE.md", "**{v} routes**")]),

    ("api-modules", "Frontend API modules (src/api/*.ts)",
     lambda: src_frontend_api_modules(),
     [("docs/GAP_CLOSURE_PLAN.md", "{v} API modules")]),

    ("vitest-cases", "Frontend test cases (it/test)",
     lambda: src_vitest_cases(),
     [("docs/GAP_CLOSURE_PLAN.md", "{v} tests passing")]),

    ("backend-test-files", "Backend *Test.java files",
     lambda: src_backend_test_files(),
     [("docs/hotwax/nexus-vs-hotwax-honest-scorecard.md", "Nexus {v} test files")]),
]


# Claims verified against surefire rather than recomputed from source. Skipped
# unless reports exist, because the count is only knowable by running the suite.
SUREFIRE_CLAIMS = [
    ("backend-tests", "Backend tests (surefire)",
     lambda: src_surefire()[0],
     [("docs/07-SCM-COMPARISON.md", "**{v} run, 0 failures, 0 errors**"),
      ("docs/01-CURRENT-STATE.md", "{v} backend tests green")]),

    ("permission-svc-tests", "PermissionServiceTest cases",
     lambda: src_surefire("PermissionServiceTest")[0],
     [("docs/implementation.md", "| `PermissionServiceTest.java` | {v} |")]),

    ("perm-matrix-tests", "PermissionMatrixIntegrationTest cases",
     lambda: src_surefire("PermissionMatrixIntegrationTest")[0],
     [("docs/implementation.md", "| `PermissionMatrixIntegrationTest.java` | {v} |")]),

    ("authz-filter-tests", "PermissionAuthorizationFilterTest cases",
     lambda: src_surefire("PermissionAuthorizationFilterTest")[0],
     [("docs/implementation.md", "| `PermissionAuthorizationFilterTest.java` | {v} |")]),
]


# ── structural Markdown checks ───────────────────────────────────────────────

def md_files():
    out = []
    for pat in ("docs/**/*.md", "*.md"):
        out += glob.glob(os.path.join(ROOT, pat), recursive=True)
    return sorted(set(out))


FENCE_RE = re.compile(r"^\s{0,3}(`{3,}|~{3,})")


def content_lines(path):
    """Yield (lineno, line) for lines outside fenced code blocks.

    Fences must be excluded: a shell pipeline inside a ```bash block legitimately
    starts with `|`, and `[text](x)` in an example is not a link. Scanning
    fenced lines produces false positives, and a linter that cries wolf gets
    switched off.
    """
    fence = None
    for n, line in enumerate(read(path).splitlines(), 1):
        m = FENCE_RE.match(line)
        if m:
            marker = m.group(1)
            if fence is None:
                fence = marker[0] * 3
            elif marker[0] == fence[0] and len(marker) >= 3:
                fence = None
            continue
        if fence is None:
            yield n, line


def anchor_slugs(path):
    slugs = set()
    for ln in read(path).splitlines():
        m = re.match(r"^#{1,6}\s+(.*)", ln)
        if m:
            s = re.sub(r"[`*_]", "", m.group(1)).strip().lower()
            s = re.sub(r"[^\w\- ]", "", s)
            if s:
                slugs.add(s.replace(" ", "-"))
    return slugs


def check_links():
    """Broken local links, dead anchors, absolute-path leaks."""
    files = md_files()
    norm = {os.path.normpath(p).replace(os.sep, "/"): p for p in files}
    broken, leaks, links, anchors = [], [], 0, 0
    for f in files:
        for n, line in content_lines(f):
            for m in re.finditer(r"\[[^\]]*\]\(([^)]+)\)", line):
                t = m.group(1).strip()
                if t.startswith(("http://", "https://", "mailto:", "tel:", "#!")):
                    continue
                links += 1
                if t.startswith("/"):
                    leaks.append((f, n, t))
                path, _, frag = t.partition("#")
                path = path.split("?")[0]
                tgt = os.path.normpath(
                    os.path.join(os.path.dirname(f), path)) if path else f
                if not os.path.exists(tgt):
                    broken.append((f, n, t, "file missing"))
                    continue
                if frag:
                    anchors += 1
                    key = os.path.normpath(tgt).replace(os.sep, "/")
                    if key not in norm or frag.lower() not in anchor_slugs(norm[key]):
                        broken.append((f, n, t, "dead anchor"))
    return links, anchors, broken, leaks


def check_tables():
    """Ragged rows and multi-line cells.

    Ragged = row pipe-count differs from its header. Escaped \\| inside a code
    span is legal and is discounted.
    Multi-line cell = a row whose closing pipe is absent, so the table silently
    truncates at that line (GFM tables cannot span lines).
    """
    ragged, multiline = [], []
    for f in md_files():
        lines = list(content_lines(f))
        i = 0
        while i < len(lines):
            n, cur = lines[i]
            if cur.lstrip().startswith("|") and i + 1 < len(lines) and \
                    re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1][1]):
                head = re.sub(r"\\\|", "", cur).count("|")
                j = i + 2
                while j < len(lines) and lines[j][1].lstrip().startswith("|"):
                    if re.sub(r"\\\|", "", lines[j][1]).count("|") != head:
                        ragged.append((f, lines[j][0]))
                    j += 1
                i = j
                continue
            # a table row that opens a cell it never closes
            if cur.lstrip().startswith("|"):
                s = re.sub(r"\\\|", "", cur).rstrip()
                if not s.endswith("|"):
                    nxt = lines[i + 1][1] if i + 1 < len(lines) else ""
                    if not nxt.lstrip().startswith("|"):
                        multiline.append((f, n))
            i += 1
    return ragged, multiline


# ── runner ───────────────────────────────────────────────────────────────────

def run_claims(claims, label):
    results = []
    for cid, label_text, compute, claims_list in claims:
        try:
            value = compute()
        except Exception as exc:  # a broken compute() is a guard bug, not a doc bug
            results.append((cid, "ERROR", str(exc)))
            continue
        missing = []
        for rel, tmpl in claims_list:
            path = os.path.join(ROOT, rel)
            if not os.path.exists(path):
                missing.append("%s (file missing)" % rel)
                continue
            needle = tmpl.replace("{v}", str(value))
            if needle not in read(path):
                missing.append("%s  [expected: %s]" % (rel, needle))
        results.append((cid, value, missing))
    return results


def main():
    global VERBOSE
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verbose", "-v", action="store_true",
                    help="list every satisfied claim, not just failures")
    ap.add_argument("--no-surefire", action="store_true",
                    help="skip claims derived from surefire test reports")
    args = ap.parse_args()
    VERBOSE = args.verbose

    failures = []

    print("=" * 72)
    print("Documentation consistency guard")
    print("=" * 72)

    # source-derived claims
    print("\n-- source-derived counts ------------------------------------------------")
    for cid, status, extra in run_claims(CLAIMS, "source"):
        if status == "ERROR":
            print("  ERROR  %-22s %s" % (cid, extra))
            failures.append("%s: guard error: %s" % (cid, extra))
            continue
        if extra:
            print("  FAIL   %-22s = %s" % (cid, status))
            for m in extra:
                print("           doc does not state it: %s" % m)
            failures.append("%s = %s: %d doc claim(s) stale" % (cid, status, len(extra)))
        else:
            print("  ok     %-22s = %s" % (cid, status))
            if VERBOSE:
                for rel, _ in dict((c[0], c) for c in
                                   [x for x in CLAIMS if x[0] == cid])[0][3]:
                    print("           verified in %s" % rel)

    # surefire-derived claims
    have_reports = os.path.isdir(SUREFIRE) and bool(glob.glob(os.path.join(SUREFIRE, "*.xml")))
    if args.no_surefire or not have_reports:
        print("\n-- surefire-derived counts: SKIPPED (no test reports; "
              "run `mvn test` first) --")
    else:
        print("\n-- surefire-derived counts --------------------------------------------")
        for cid, status, extra in run_claims(SUREFIRE_CLAIMS, "surefire"):
            if status == "ERROR":
                print("  ERROR  %-22s %s" % (cid, extra))
                failures.append("%s: guard error: %s" % (cid, extra))
            elif extra:
                print("  FAIL   %-22s = %s" % (cid, status))
                for m in extra:
                    print("           doc does not state it: %s" % m)
                failures.append("%s = %s: %d doc claim(s) stale" % (cid, status, len(extra)))
            else:
                print("  ok     %-22s = %s" % (cid, status))

    # permission map integrity (behavioural invariant, not just a count)
    print("\n-- structural invariants ----------------------------------------------")
    total, uniq = src_permission_unique_prefixes()
    if total == uniq:
        print("  ok     permission prefixes unique    %d entries, %d unique"
              % (total, uniq))
    else:
        print("  FAIL   permission prefixes duplicated: %d entries, %d unique"
              % (total, uniq))
        failures.append("PATH_TO_RESOURCE has %d duplicate prefix declaration(s)"
                        % (total - uniq))

    # data-model domain counts must sum to the documented total
    ref = os.path.join(ROOT, "docs", "10-DATA-MODEL-REFERENCE.md")
    if os.path.exists(ref):
        lines = read(ref).splitlines()
        rows, total_doc = [], None
        for i, ln in enumerate(lines):
            if ln.startswith("| # | Domain | Tables"):
                j = i + 2
                while j < len(lines) and lines[j].lstrip().startswith("|"):
                    cells = [c.strip() for c in lines[j].strip().strip("|").split("|")]
                    if len(cells) >= 3 and cells[2].isdigit():
                        rows.append(int(cells[2]))
                    j += 1
                break
        m = re.search(r"all \*\*(\d+) tables", read(ref))
        if m:
            total_doc = int(m.group(1))
        if rows and total_doc:
            s = sum(rows)
            if s == total_doc:
                print("  ok     domain tables sum           %d domains = %d tables"
                      % (len(rows), s))
            else:
                print("  FAIL   domain tables sum           %d domains = %d, "
                      "header claims %d" % (len(rows), s, total_doc))
                failures.append("10-DATA-MODEL-REFERENCE domain counts sum to %d, "
                                "header claims %d" % (s, total_doc))

    # markdown structure
    links, anchors, broken, leaks = check_links()
    print("\n-- markdown structure --------------------------------------------------")
    print("  ..    %d files, %d local links, %d anchors" % (len(md_files()), links, anchors))
    if leaks:
        print("  FAIL   absolute-path leaks: %d" % len(leaks))
        for f, n, t in leaks[:10]:
            print("           %s:%d  %s" % (os.path.relpath(f, ROOT), n, t))
        failures.append("%d absolute-path link leak(s)" % len(leaks))
    else:
        print("  ok     absolute-path leaks          0")
    if broken:
        print("  FAIL   broken links/anchors: %d" % len(broken))
        for f, n, t, why in broken[:10]:
            print("           %s:%d  %s  [%s]" % (os.path.relpath(f, ROOT), n, t, why))
        failures.append("%d broken link/anchor(s)" % len(broken))
    else:
        print("  ok     broken links/anchors        0")

    ragged, multiline = check_tables()
    if ragged:
        print("  FAIL   ragged table rows: %d" % len(ragged))
        for f, n in ragged[:10]:
            print("           %s:%d" % (os.path.relpath(f, ROOT), n))
        failures.append("%d ragged table row(s)" % len(ragged))
    else:
        print("  ok     ragged table rows            0")
    if multiline:
        print("  FAIL   multi-line table cells: %d (GFM truncates these)" % len(multiline))
        for f, n in multiline[:10]:
            print("           %s:%d" % (os.path.relpath(f, ROOT), n))
        failures.append("%d multi-line table cell(s)" % len(multiline))
    else:
        print("  ok     multi-line table cells      0")

    print("\n" + "=" * 72)
    if failures:
        print("FAIL — %d problem(s):" % len(failures))
        for f in failures:
            print("  * %s" % f)
        print("=" * 72)
        return 1
    print("PASS — all documented claims match the source tree.")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    sys.exit(main())