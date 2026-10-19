import unittest
from check_device_tests import passing_test_count


class DeviceResultTest(unittest.TestCase):
    def test_accepts_growing_suites_and_singular(self):
        for count in (1, 41, 43, 156):
            self.assertEqual(count, passing_test_count(f"runner output\nOK ({count} tests)\nINSTRUMENTATION_CODE: -1\n"))
        self.assertEqual(1, passing_test_count("OK (1 test)\n"))

    def test_rejects_empty_missing_and_duplicate_results(self):
        for report in ("", "OK (0 tests)", "Tests run: 43", "OK (1 test)\nOK (1 test)\n"):
            with self.assertRaises(ValueError):
                passing_test_count(report)

    def test_rejects_failure_or_crash_even_with_partial_success(self):
        for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED", "Process crashed", "INSTRUMENTATION_RESULT: shortMsg=crash"):
            with self.assertRaises(ValueError):
                passing_test_count(f"OK (43 tests)\n{marker}\n")
