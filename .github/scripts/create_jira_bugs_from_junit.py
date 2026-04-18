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
        for tc in root.findall(".//testcase"):
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
            message = (issue_node.attrib.get("message") or "Test failure")[:240]
            details = (issue_node.text or "").strip()[:5000]
            fingerprint = hashlib.sha1(f"{classname}::{name}".encode()).hexdigest()[:12]
            failed.append({
                "classname": classname,
                "name": name,
                "time": time_taken,
                "message": message,
                "details": details,
                "fingerprint": fingerprint
            })
    return failed


def build_adf_description(test):
    """Build Atlassian Document Format (ADF) description for Jira API v3"""
    return {
        "type": "doc",
        "version": 1,
        "content": [
            {
                "type": "paragraph",
                "content": [{"type": "text", "text": "Automated test failure detected."}]
            },
            {
                "type": "heading",
                "attrs": {"level": 3},
                "content": [{"type": "text", "text": "Context"}]
            },
            {
                "type": "bulletList",
                "content": [
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Testiny Project: "},
                            {"type": "text", "text": TESTINY_PROJECT_KEY, "marks": [{"type": "strong"}]}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Source: "},
                            {"type": "text", "text": TESTINY_SOURCE, "marks": [{"type": "strong"}]}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Branch: "},
                            {"type": "text", "text": GIT_BRANCH or "N/A"}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Commit: "},
                            {"type": "text", "text": GIT_COMMIT[:8] if GIT_COMMIT else "N/A"}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Fingerprint: "},
                            {"type": "text", "text": test["fingerprint"]}
                        ]}]
                    },
                ]
            },
            {
                "type": "heading",
                "attrs": {"level": 3},
                "content": [{"type": "text", "text": "Failure Details"}]
            },
            {
                "type": "bulletList",
                "content": [
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Class: "},
                            {"type": "text", "text": test["classname"]}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Test: "},
                            {"type": "text", "text": test["name"]}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Duration: "},
                            {"type": "text", "text": test["time"] + "s" if test["time"] else "N/A"}
                        ]}]
                    },
                    {"type": "listItem", "content": [
                        {"type": "paragraph", "content": [
                            {"type": "text", "text": "Message: "},
                            {"type": "text", "text": test["message"]}
                        ]}]
                    },
                ]
            },
            {
                "type": "heading",
                "attrs": {"level": 3},
                "content": [{"type": "text", "text": "Stacktrace / Details"}]
            },
            {
                "type": "codeBlock",
                "attrs": {"language": "text"},
                "content": [{"type": "text", "text": test["details"] or "No details available."}]
            },
        ]
    }


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

    adf_description = build_adf_description(test)

    payload = {
        "fields": {
            "project": {"key": JIRA_PROJECT_KEY},
            "summary": summary,
            "description": adf_description,
            "issuetype": {"name": "Bug"}
        }
    }

    resp = session.post(f"{JIRA_BASE_URL}/rest/api/3/issue", json=payload)
    if resp.status_code >= 300:
        print(f"Failed to create Jira issue for {summary}: {resp.status_code} {resp.text}")
    else:
        issue_data = resp.json()
        print(f"Created Jira issue {issue_data['key']} for {summary}")


def main():
    failed_tests = extract_failed_tests()
    if not failed_tests:
        print("No failed tests found.")
        return
    print(f"Found {len(failed_tests)} failed test(s). Creating Jira bugs...")
    for test in failed_tests:
        create_issue(test)


if __name__ == "__main__":
    main()