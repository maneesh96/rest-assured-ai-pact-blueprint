"""Summarise the known-defect run for the GitHub job page.

Each test tagged "known-defect" asserts what the OpenAPI spec requires. A failing
test means the defect is still present; a passing one means the API now behaves
per spec and the tag (and its KNOWN_DEFECTS.md entry) can be removed.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

REPORTS = "target/surefire-reports/TEST-*.xml"


def escape(text):
    """Escape text for a GitHub workflow command."""
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def first_line(text):
    for line in (text or "").splitlines():
        if line.strip() and "expectation failed" not in line:
            return line.strip()
    return "no message"


def main():
    files = sorted(glob.glob(REPORTS))
    if not files:
        print("::error title=Known defects::No Surefire reports found; the known-defect run did not execute.")
        return 1

    still_open, fixed = [], []
    for path in files:
        for case in ET.parse(path).getroot().iter("testcase"):
            name = f"{case.get('classname', '').rsplit('.', 1)[-1]} {case.get('name', '')}"
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                still_open.append((name, first_line(problem.get("message") or problem.text)))
            elif case.find("skipped") is None:
                fixed.append(name)

    # GitHub keeps at most 10 annotations of each level per step, so each level
    # gets a single annotation listing every test ("%0A" is a newline there).
    if still_open:
        listing = "%0A".join(escape(f"{name}: {message}") for name, message in still_open)
        print(f"::warning title={len(still_open)} known defects still present::{listing}")
    if fixed:
        listing = "%0A".join(escape(name) for name in fixed)
        print(f"::notice title={len(fixed)} known defects no longer reproduce (remove their tag)::{listing}")

    lines = [
        "### Known defects",
        "",
        f"{len(still_open)} still present, {len(fixed)} no longer reproduce. See KNOWN_DEFECTS.md.",
        "",
        "| Test | Observed |",
        "| --- | --- |",
    ]
    lines += [f"| {name} | {message.replace('|', '/')} |" for name, message in still_open]
    lines += [f"| {name} | behaves per spec |" for name in fixed]
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n")
    else:
        print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
