"""Validate AndroidJUnitRunner's result without hardcoding the suite size."""
import re
import sys
from pathlib import Path


def passing_test_count(report: str) -> int:
    failures = ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED", "Process crashed", "shortMsg=", "INSTRUMENTATION_RESULT: shortMsg")
    if any(marker in report for marker in failures):
        raise ValueError("Android instrumentation reported a failure or crash.")
    summaries = re.findall(r"^OK \(([1-9][0-9]*) tests?\)\s*$", report, re.MULTILINE)
    if len(summaries) != 1:
        raise ValueError("Expected one successful, nonempty Android test run.")
    return int(summaries[0])


if __name__ == "__main__":
    try:
        count = passing_test_count(Path(sys.argv[1]).read_text())
    except (ValueError, OSError, IndexError) as error:
        sys.exit(str(error))
    print(f"Android instrumentation passed: {count} tests.")
