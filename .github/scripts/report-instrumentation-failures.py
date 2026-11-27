import glob
import xml.etree.ElementTree as ET


def escape_command(value):
    for character, escaped in (
        ("%", "%25"),
        ("\r", "%0D"),
        ("\n", "%0A"),
        (":", "%3A"),
        (",", "%2C"),
    ):
        value = value.replace(character, escaped)
    return value


found_failures = False
reports = glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True)
for report in reports:
    try:
        root = ET.parse(report).getroot()
    except (ET.ParseError, OSError):
        continue
    for case in root.findall(".//testcase"):
        for failure in list(case.findall("failure")) + list(case.findall("error")):
            found_failures = True
            identity = ".".join(part for part in (case.get("classname"), case.get("name")) if part)
            detail = failure.get("message") or failure.text or "Android instrumentation test failed"
            message = escape_command(" ".join(f"{identity}: {detail}".split()))
            print(f"::error title=Android instrumentation failure,file={report}::{message[:4000]}")

if not found_failures:
    try:
        with open("/tmp/android-device-tests.log", encoding="utf-8") as log:
            lines = log.readlines()
    except OSError as error:
        lines = [str(error)]
    message = escape_command(" ".join(" ".join(lines[-50:]).split()))
    print(f"::error title=Android instrumentation runner failure::{message[-4000:]}")
