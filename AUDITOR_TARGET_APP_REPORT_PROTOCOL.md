# มหาเทพ AUDITOR: Target App Report Protocol

## Purpose
This protocol lets an app owned by the project report its own GPS detector results to มหาเทพ AUDITOR. It is a reporting interface, not a mechanism for changing another app's location input or bypassing its protections.

## Select a target
AUDITOR lists launchable apps visible to Android's package-visibility rules. The selected package is saved locally on the device. Selection identifies the assessment target only; Android Mock Location remains a system-level provider and is not made app-exclusive by selecting a package.

## Send a report
The target app should create a plain-text report and launch Android's Sharesheet:

- Action: `Intent.ACTION_SEND`
- MIME type: `text/plain`
- Extra: `Intent.EXTRA_TEXT`
- Chooser title: `ส่งผลการประเมินไปมหาเทพ AUDITOR`

The report format is UTF-8 text. One optional metadata line identifies the reporting app, followed by one or more CASE records:

```text
APP|com.example.delivery|Delivery App
CASE|Normal route|LOW|LOW|0|true|
CASE|Mock provider signal|HIGH|HIGH|65|true|ANDROID_MOCK_FLAG
CASE|Implausible displacement|HIGH|LOW|0|false|MISSED_DETECTION
```

CASE fields:
1. `CASE`
2. Scenario name
3. Expected risk: `LOW`, `REVIEW`, or `HIGH`
4. Actual detector result: `LOW`, `REVIEW`, or `HIGH`
5. Detector score from 0 to 100
6. Whether the expected assessment passed: `true` or `false`
7. Finding codes separated by commas

## Evidence quality
- Report actual detector output from the target app. Do not infer a detector verdict from LAB telemetry.
- Include a timestamp, test-case ID, app version, and detector-rule version in an expanded report when available.
- Keep personal/customer location data out of test reports unless explicitly needed and authorized.
- Treat a missed detection as a remediation finding, not as proof that the test should be concealed.
- Re-run the same controlled test after changing the owned app's detector and compare before/after results.

## Current implementation boundary
AUDITOR currently accepts shared text reports and parses CASE records. The target picker stores the selected package locally. The app-to-AUDITOR report contract is documented here; automatic detector-result collection still requires adding the share action to the owned delivery app or its authorized test build.
