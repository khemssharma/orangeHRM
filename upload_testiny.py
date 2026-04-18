import os
import glob
import subprocess
import sys

PROJECT_KEY = "STDNO"
SOURCE_NAME = "surefire-tests"
REPORTS_GLOB = "target/surefire-reports/*.xml"

def upload_reports():
    api_key = os.getenv("TESTINY_API_KEY")
    if not api_key:
        raise RuntimeError("Missing TESTINY_API_KEY environment variable")

    report_files = glob.glob(REPORTS_GLOB)
    if not report_files:
        raise RuntimeError(f"No XML reports found for pattern: {REPORTS_GLOB}")

    env = os.environ.copy()
    env["TESTINY_API_KEY"] = api_key

    cmd = [
        "npx",
        "@testiny/cli",
        "automation",
        "--project", PROJECT_KEY,
        "--source", SOURCE_NAME,
        "--junit",
        *report_files
    ]

    result = subprocess.run(cmd, env=env, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"Testiny import failed with exit code {result.returncode}")

if __name__ == "__main__":
    upload_reports()