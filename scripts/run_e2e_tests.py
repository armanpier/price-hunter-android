#!/usr/bin/env python3
import sys
import os
import subprocess
import argparse
import xml.etree.ElementTree as ET
import json
import shutil
from datetime import datetime, timezone

def run_e2e():
    parser = argparse.ArgumentParser(description="Price Hunter Standalone 4-Tier E2E Test Runner")
    parser.add_argument("--tier", default="all", choices=["all", "1", "2", "3", "4"], help="Test Tier to execute")
    parser.add_argument("--report-dir", default="test-reports", help="Directory to output reports")
    args = parser.parse_args()

    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    report_dir = os.path.join(project_root, args.report_dir)
    os.makedirs(report_dir, exist_ok=True)

    print("================================================================================")
    print("        NATIVE ANDROID PRICE HUNTER — 4-TIER E2E TEST RUNNER                    ")
    print("================================================================================")
    print(f"Target Tier: {args.tier}")
    print(f"Report Directory: {report_dir}")

    # Clean test results directory before executing Gradle to prevent reading stale test XMLs
    results_dir = os.path.join(project_root, "app", "build", "test-results", "testDebugUnitTest")
    if os.path.exists(results_dir):
        print(f"[*] Cleaning test results directory: {results_dir}")
        try:
            shutil.rmtree(results_dir, ignore_errors=True)
        except Exception as e:
            print(f"[*] Warning during rmtree: {e}")
        # In case a subfolder or file handle remains open on Windows, explicitly unlink all XMLs
        if os.path.exists(results_dir):
            for fname in os.listdir(results_dir):
                if fname.endswith(".xml"):
                    try:
                        os.remove(os.path.join(results_dir, fname))
                    except OSError:
                        pass

    # Determine Gradle invocation
    gradle_cmd = os.path.join(project_root, "gradlew.bat" if os.name == "nt" else "gradlew")
    if not os.path.exists(gradle_cmd):
        gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"

    tier_filter_map = {
        "all": "com.teamwork.pricehunter.e2e.tier*.*",
        "1": "com.teamwork.pricehunter.e2e.tier1_coverage.*",
        "2": "com.teamwork.pricehunter.e2e.tier2_boundary.*",
        "3": "com.teamwork.pricehunter.e2e.tier3_combination.*",
        "4": "com.teamwork.pricehunter.e2e.tier4_application.*",
    }

    tier_pkg_map = {
        "1": "tier1_coverage",
        "2": "tier2_boundary",
        "3": "tier3_combination",
        "4": "tier4_application",
    }

    tier_name_map = {
        "1": "Tier 1",
        "2": "Tier 2",
        "3": "Tier 3",
        "4": "Tier 4",
    }

    test_pattern = tier_filter_map.get(args.tier, "com.teamwork.pricehunter.e2e.tier*.*")
    target_pkg = tier_pkg_map.get(args.tier)
    target_tier_name = tier_name_map.get(args.tier)

    cmd = [
        gradle_cmd,
        ":app:testDebugUnitTest",
        "--tests", test_pattern,
        "--info"
    ]

    print(f"[*] Executing command: {' '.join(cmd)}")
    start_time = datetime.now()
    try:
        proc = subprocess.run(cmd, cwd=project_root, check=False)
        duration_s = (datetime.now() - start_time).total_seconds()
    except Exception as e:
        print(f"[!] Critical error invoking gradle: {e}")
        sys.exit(2)

    # Immediately inspect Gradle process returncode
    if proc.returncode != 0:
        print(f"\n[!] Gradle execution failed with return code {proc.returncode}")
        print(">>> GRADLE EXECUTION FAILED <<<")
        sys.exit(proc.returncode)

    # Parse JUnit XML test results for the executed tier(s)
    total_tests = 0
    total_failures = 0
    total_skipped = 0
    tier_counts = {
        "Tier 1": [0, 0],  # [passed, total]
        "Tier 2": [0, 0],
        "Tier 3": [0, 0],
        "Tier 4": [0, 0],
    }

    if os.path.exists(results_dir):
        for f in sorted(os.listdir(results_dir)):
            if f.startswith("TEST-com.teamwork.pricehunter.e2e.tier") and f.endswith(".xml"):
                # Filter XML parsing to only the target tier when args.tier != "all"
                if target_pkg is not None and target_pkg not in f:
                    continue

                xml_path = os.path.join(results_dir, f)
                try:
                    tree = ET.parse(xml_path)
                    root = tree.getroot()
                except Exception as e:
                    print(f"[!] Failed to parse {f}: {e}")
                    continue

                suite_tests = int(root.attrib.get("tests", 0))
                suite_failures = int(root.attrib.get("failures", 0)) + int(root.attrib.get("errors", 0))
                suite_skipped = int(root.attrib.get("skipped", 0))
                suite_passed = suite_tests - suite_failures - suite_skipped

                total_tests += suite_tests
                total_failures += suite_failures
                total_skipped += suite_skipped

                name = root.attrib.get("name", "")
                if "tier1_coverage" in name or "tier1_coverage" in f:
                    tier_counts["Tier 1"][0] += suite_passed
                    tier_counts["Tier 1"][1] += suite_tests
                elif "tier2_boundary" in name or "tier2_boundary" in f:
                    tier_counts["Tier 2"][0] += suite_passed
                    tier_counts["Tier 2"][1] += suite_tests
                elif "tier3_combination" in name or "tier3_combination" in f:
                    tier_counts["Tier 3"][0] += suite_passed
                    tier_counts["Tier 3"][1] += suite_tests
                elif "tier4_application" in name or "tier4_application" in f:
                    tier_counts["Tier 4"][0] += suite_passed
                    tier_counts["Tier 4"][1] += suite_tests

    passed_tests = total_tests - total_failures - total_skipped
    pass_rate = (passed_tests / total_tests * 100.0) if total_tests > 0 else 0.0

    print("\n--------------------------------------------------------------------------------")
    print("| METRIC                     | VALUE                                           |")
    print("--------------------------------------------------------------------------------")
    print(f"| {'Total Tests Executed':<26} | {total_tests:<47} |")
    print(f"| {'Tests Passed':<26} | {passed_tests:<47} |")
    print(f"| {'Tests Failed':<26} | {total_failures:<47} |")
    print(f"| {'Tests Ignored/Skipped':<26} | {total_skipped:<47} |")
    print(f"| {'Execution Time':<26} | {f'{duration_s:.2f}s':<47} |")
    print(f"| {'Pass Rate':<26} | {f'{pass_rate:.2f}%':<47} |")
    print("--------------------------------------------------------------------------------")

    print("\nTier Breakdown:")
    for t_name in ["Tier 1", "Tier 2", "Tier 3", "Tier 4"]:
        t_passed, t_total = tier_counts[t_name]
        if args.tier != "all" and t_name != target_tier_name:
            print(f"  - {t_name}: 0/0 passed (SKIPPED)")
        else:
            print(f"  - {t_name}: {t_passed}/{t_total} passed")

    print("\n--------------------------------------------------------------------------------")
    print("| TIER   | SUITE NAME                  | EXECUTED | PASSED | STATUS            |")
    print("--------------------------------------------------------------------------------")
    tier_display_names = {
        "Tier 1": "Feature Coverage",
        "Tier 2": "Boundary & Corner",
        "Tier 3": "Cross-Feature Combinations",
        "Tier 4": "Real-World Scenarios",
    }
    for t_name in ["Tier 1", "Tier 2", "Tier 3", "Tier 4"]:
        t_passed, t_total = tier_counts[t_name]
        t_failed = t_total - t_passed
        if args.tier != "all" and t_name != target_tier_name:
            status_col = "0/0 passed (SKIPPED)"
        else:
            status_col = "PASS" if t_failed == 0 and t_total > 0 else ("SKIPPED" if t_total == 0 else "FAIL")
        print(f"| {t_name:<6} | {tier_display_names[t_name]:<27} | {t_total:<8} | {t_passed:<6} | {status_col:<17} |")
    print("--------------------------------------------------------------------------------")

    # Generate Reports
    timestamp_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # 1. e2e_report.md
    md_path = os.path.join(report_dir, "e2e_report.md")
    status_str = "PASSED" if (proc.returncode == 0 and total_failures == 0 and total_tests > 0) else "FAILED"
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("# E2E Test Execution Summary Report\n")
        f.write(f"- **Timestamp**: {timestamp_iso}\n")
        f.write(f"- **Target Tier**: {args.tier}\n")
        f.write(f"- **Total Tests**: {total_tests}\n")
        f.write(f"- **Passed**: {passed_tests} ({pass_rate:.1f}%)\n")
        f.write(f"- **Failed**: {total_failures}\n")
        f.write(f"- **Duration**: {duration_s:.2f}s\n")
        f.write(f"- **Status**: **{status_str}**\n\n")
        f.write("## Tier Breakdown\n")
        for t_key in ["Tier 1", "Tier 2", "Tier 3", "Tier 4"]:
            t_pass, t_tot = tier_counts[t_key]
            if args.tier != "all" and t_key != target_tier_name:
                f.write(f"- **{t_key}**: 0/0 passed (SKIPPED)\n")
            else:
                f.write(f"- **{t_key}**: {t_pass}/{t_tot} passed\n")
        f.write("\n| Tier | Name | Target | Executed | Passed | Failed | Status |\n")
        f.write("|---|---|---|---|---|---|---|\n")
        targets = {"Tier 1": 56, "Tier 2": 52, "Tier 3": 12, "Tier 4": 8}
        names = {
            "Tier 1": "Feature Coverage",
            "Tier 2": "Boundary & Corner",
            "Tier 3": "Cross-Feature Combinations",
            "Tier 4": "Real-World Scenarios"
        }
        for t_key in ["Tier 1", "Tier 2", "Tier 3", "Tier 4"]:
            t_pass, t_tot = tier_counts[t_key]
            t_fail = t_tot - t_pass
            if args.tier != "all" and t_key != target_tier_name:
                t_status = "0/0 passed (SKIPPED)"
            else:
                t_status = "PASS" if t_fail == 0 and t_tot > 0 else ("SKIPPED" if t_tot == 0 else "FAIL")
            f.write(f"| {t_key} | {names[t_key]} | >={targets[t_key]} | {t_tot} | {t_pass} | {t_fail} | {t_status} |\n")
        f.write("\n## Verification Result\n")
        if status_str == "PASSED":
            f.write(f"All {passed_tests} executed tests passed with 100% success rate. Exit code 0.\n")
        else:
            f.write("One or more tests failed or build error occurred.\n")

    # 2. e2e_summary.json
    json_path = os.path.join(report_dir, "e2e_summary.json")
    summary_data = {
        "timestamp": timestamp_iso,
        "tier": args.tier,
        "totalTests": total_tests,
        "passed": passed_tests,
        "failed": total_failures,
        "passRatePercent": round(pass_rate, 2),
        "durationSeconds": round(duration_s, 2),
        "status": status_str,
        "tiers": {}
    }
    for t_key in ["Tier 1", "Tier 2", "Tier 3", "Tier 4"]:
        t_pass, t_tot = tier_counts[t_key]
        if args.tier != "all" and t_key != target_tier_name:
            summary_data["tiers"][t_key] = {"passed": 0, "total": 0, "status": "SKIPPED"}
        else:
            t_stat = "PASS" if (t_tot > 0 and t_tot == t_pass) else ("SKIPPED" if t_tot == 0 else "FAIL")
            summary_data["tiers"][t_key] = {"passed": t_pass, "total": t_tot, "status": t_stat}
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=2)

    # 3. junit_report.xml
    xml_path = os.path.join(report_dir, "junit_report.xml")
    with open(xml_path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n')
        f.write(f'<testsuite name="com.teamwork.pricehunter.e2e.E2ETestSuite" tests="{total_tests}" failures="{total_failures}" errors="0" skipped="{total_skipped}" time="{duration_s:.3f}">\n')
        f.write('</testsuite>\n')

    print(f"\n[+] Generated reports in {report_dir}")
    if proc.returncode == 0 and total_failures == 0 and total_tests > 0:
        print("\n>>> ALL E2E TESTS PASSED (100% SUCCESS) <<<")
        sys.exit(0)
    else:
        print("\n>>> E2E TEST FAILURES DETECTED <<<")
        sys.exit(proc.returncode if proc.returncode != 0 else 1)

if __name__ == "__main__":
    run_e2e()
