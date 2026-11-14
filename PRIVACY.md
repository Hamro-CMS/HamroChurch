# Privacy Policy — Hamro Church

**Effective Date:** November 14, 2026  
**Project:** Hamro Church Desktop Presentation Software (`Hamro-CMS/HamroChurch`)  
**Privacy Contact:** `support@hamrocms.com`

---

## 1. Core Commitment: 100% Local Church Data & Zero PII

**Hamro Church** is designed from the ground up to respect congregational privacy and comply strictly with the **UK General Data Protection Regulation (UK GDPR)** and **Nepal's Individual Privacy Act, 2075 (2018)**.

- **100% Offline & Local Worship Data:** All church presentations, custom hymns, scripture notes, member/church records, financial slides, media files, and local configurations remain **100% offline and local** on your computer. None of your church or worship content is ever uploaded to our servers.
- **Zero Personally Identifiable Information (PII):** We **never** collect, transmit, or store personal names, email addresses, computer hostnames (`os.hostname()`), system usernames, hardware serial numbers, or raw/partial IP addresses.

---

## 2. Optional, Strictly Opt-In Anonymous Usage Statistics

On first launch, **Hamro Church** displays a transparent opt-in prompt (`Anonymous Usage Statistics`). **Zero telemetry requests are sent unless you explicitly click `Accept`.**

If you choose to opt in, the desktop app sends only basic, non-personal aggregate usage statistics to help us understand regional adoption and improve stability across operating systems:

| Field | Example | Purpose |
| :--- | :--- | :--- |
| `anonymous_device_id` | `f47ac10b-58cc-4372-a567-0e02b2c3d479` | Random local UUID v4 generated solely to avoid counting the same installation twice (hashed server-side to an 8-character anonymous label such as `Device #a4f91c2b`) |
| `os_platform` & `arch` | `Windows` (`x64`), `macOS` (`arm64`), `Linux` (`x64`) | Understand which desktop platforms and CPU architectures require testing and support |
| `app_version` | `1.7.23` | Verify update adoption and identify version-specific stability needs |
| `City` & `Country` | `Kathmandu, Nepal` / `London, United Kingdom` | Resolved **in memory only** during the request; **the IP address is immediately discarded and never written to any database table** |
| `session_duration` | `1840` (seconds) | Measure aggregate active usage time |

---

## 3. What We NEVER Collect or Store

Whether anonymous usage statistics are enabled or disabled, **Hamro Church** **NEVER** collects or stores:

1. Your name, church name, pastor/member directories, or financial data.
2. Your computer hostname (e.g., `DESKTOP-...`) or operating system username.
3. Raw, partial, or masked IP addresses in any analytics database table.
4. Hardware serial numbers, MAC addresses, or OS machine IDs.
5. Worship slides, custom hymns, Bible bookmarks, media files, or local file paths.
6. Unconsented third-party analytics or crash trackers.

---

## 4. Full User Control: Opt Out or Delete Logs Anytime

You remain in complete control of your privacy settings at all times:

1. **Turn Off Instantaneously (`Settings > Privacy`):**  
   Toggle **`Share Anonymous Usage Statistics (OS, City/Country, Duration)`** **OFF** at any time. Turning this toggle off immediately stops all background pings without sending any network request.
2. **One-Click Server Log Deletion (`Settings > Privacy`):**  
   Click **`Delete Server Logs for This Anonymous Device ID`** inside **Settings > Privacy**. This sends an immediate deletion request (`DELETE https://hamrocms.com/api/analytics/device/{anonymous_device_id}`) that permanently purges any stored anonymous session records associated with your UUID and rotates your local UUID to a fresh random value.
3. **Contact Privacy Support:**  
   For any privacy questions or manual erasure assistance, email **`support@hamrocms.com`**.
