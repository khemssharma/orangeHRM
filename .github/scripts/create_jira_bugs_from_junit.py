import os
import glob
import hashlib
import xml.etree.ElementTree as ET
import requests

REPORTS_GLOB = "target/surefire-reports/*.xml"

JIRA_BASE_URL = os.environ["JIRA_BASE_URL"].rstrip("/")
JIRA_EMAIL = os.environ["JIRA_EMAIL"]
JIRA_API_TOKEN = os.environ["JIRA_API_TOKEN"]
JIRA_PROJECT_KEY = os.environ["JIRA_PROJECT_KEY"]

TESTINY_PROJECT_KEY = os.getenv("TESTINY_PROJECT_KEY", "STDNO")
TESTINY_SOURCE = os.getenv("TESTINY_SOURCE", "surefire-tests")
GIT_BRANCH = os.getenv("GIT_BRANCH", "")
GIT_COMMIT = os.getenv("GIT_COMMIT", "")

session = requests.Session()
session.auth = (JIRA_EMAIL, JIRA_API_TOKEN)
session.headers.update({"Accept": "application/json", "Content-Type": "application/json"})

def extract_failed_tests():
    failed = []

    for file_path in glob.glob(REPORTS_GLOB):
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
        except Exception:
            continue

        testcases = root.findall(".//testcase")
        for tc in testcases:
            failure = tc.find("failure")
            error = tc.find("error")
            skipped = tc.find("skipped")

            if skipped is not None:
                continue

            if failure is None and error is None:
                continue

            issue_node = failure if failure is not None else error
            classname = tc.attrib.get("classname", "UnknownClass")
            name = tc.attrib.get("name", "unknown_test")
            time_taken = tc.attrib.get("time", "")
            message = issue_node.attrib.get("message", "Test failure")
            details = (issue_node.text or "").strip()

            fingerprint_raw = f"{classname}::{name}"
            fingerprint = hashlib.sha1(fingerprint_raw.encode()).hexdigest()[:12]

            failed.append({
                "classname": classname,
                "name": name,
                "time": time_taken,
                "message": message[:240],
                "details": details[:5000],
                "fingerprint": fingerprint
            })

    return failed

def find_existing_issue(summary):
    jql = f'project = "{JIRA_PROJECT_KEY}" AND summary ~ "\\"{summary}\\"" AND statusCategory != Done ORDER BY created DESC'
    resp = session.post(
        f"{JIRA_BASE_URL}/rest/api/3/search/jql",
        json={"jql": jql, "maxResults": 1, "fields": ["summary", "status"]}
    )
    if resp.status_code >= 300:
        return None

    data = resp.json()
    issues = data.get("issues", [])
    return issues[0]["key"] if issues else None

def create_issue(test):
    summary = f"AUTOMATION FAIL: {test['classname']}::{test['name']}"
    existing = find_existing_issue(summary)
    if existing:
        print(f"Existing Jira issue found for {summary}: {existing}")
        return

    description = (
        f"Automated test failure detected.\n\n"
        f"Testiny project: {TESTINY_PROJECT_KEY}\n"
        f"Source: {TESTINY_SOURCE}\n"
        f"Branch: {GIT_BRANCH}\n"
        f"Commit: {GIT_COMMIT}\n"
        f"Fingerprint: {test['fingerprint']}\n"
        f"Class: {test['classname']}\n"
        f"Test: {test['name']}\n"
        f"Duration: {test['time']}\n"
        f"Failure message: {test['message']}\n\n"
        f"Stacktrace / details:\n{test['details']}"
    )

    payload = {
        "fields": {
            "project": {"key": JIRA_PROJECT_KEY},
            "summary": summary,
            "description": description,
            "issuetype": {"name": "Bug"}
        }
    }

    resp = session.post(f"{JIRA_BASE_URL}/rest/api/3/issue", json=payload)
    if resp.status_code >= 300:
        print(f"Failed to create Jira issue for {summary}: {resp.status_code} {resp.text}")
    else:
        print(f"Created Jira issue for {summary}")

def main():
    failed_tests = extract_failed_tests()
    if not failed_tests:
        print("No failed tests found.")
        return

    for test in failed_tests:
        create_issue(test)

if __name__ == "__main__":
    main()