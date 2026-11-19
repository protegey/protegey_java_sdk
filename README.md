# protegey-sdk (Java)

Official Protegey SDK for Java backends — the full partner API (`/partner-api/*`): transactions,
cash deposits, KYC sessions, KYB applicants, alerts, device and behavioral events, sanctions screening and the
shared fraud signal network, plus webhook signature verification. Called directly from your server
with your own API key.

Device fingerprinting and behavioral biometrics *collection* are browser/mobile concerns — see
`@protegey/sdk` (JS), `protegey_sdk` (Flutter) or `@protegey/react-native-sdk`. This SDK forwards
what those collect (`deviceEvents.record()`, `behavioralEvents.record()`).

Requires Java 11+. Built on `java.net.http.HttpClient` (no HTTP library dependency); the one
dependency is Gson, for JSON (de)serialization.

## Install

Not yet published to Maven Central — install directly from GitHub for now (build and install into
your local `~/.m2` repository):

```bash
git clone https://github.com/protegey/protegey_java_sdk.git
cd protegey_java_sdk
mvn install
```

```xml
<dependency>
  <groupId>com.protegey</groupId>
  <artifactId>protegey-sdk</artifactId>
  <version>0.8.0</version>
</dependency>
```

Source: [github.com/protegey/protegey_java_sdk](https://github.com/protegey/protegey_java_sdk)

## Conventions

- Auth is the `x-api-key` header. **Live vs sandbox is decided by which key you use** — there is no
  key prefix or header that selects the mode. Sandbox quotas: 100 transactions and 10 KYC sessions
  per UTC day.
- Every method returns a plain `Map<String, Object>`, so new fields or enum values never break you.
  Gson parses JSON numbers as `Double` (`riskScore` → `20.0`).
- Status-like values (`status`, `decision`, `action`, `deviceAction`, `category`, `outcome`,
  `submissionStatus`, `previousStatus`/`newStatus`, KYC `recommendation`, ...) are
  UPPER_SNAKE_CASE on the wire. A few keys stay lowercase: `ruleSeverity`, `priority`, sanctions
  `source`/`type`.
- List methods return `{data, total, page, limit, totalPages}` and accept `page`/`limit` (max 100).
  Where the backend only accepts a specific casing for a filter, the SDK converts it for you: KYC
  `status` (`IN_REVIEW` → `In Review`), alert `status`/`disposition` (lowercased), sanctions `type`
  (lowercased).
- Non-2xx responses throw `ProtegeyApiException` with `getStatus()`, `getMessage()`,
  `getErrorCode()` (`PARTNER_SUSPENDED` / `PARTNER_CONTRACT_REQUIRED` on 403, else null) and
  `getBody()`.

## Usage

```java
import com.protegey.sdk.Protegey;
import java.util.HashMap;
import java.util.Map;

Protegey protegey = new Protegey("YOUR_API_KEY", "https://api.protegey.com");
```

### Transactions

```java
Map<String, Object> result = protegey.transactions.report(Map.of(
    "externalTransactionId", "tx-00234",   // idempotency key — a repeat gets a 409
    "externalCustomerId", "cust-9981",
    "customerName", "Awa Diallo",
    "devicePhoneNumber", "+221771234567",
    "direction", "DEBIT",                   // DEBIT | CREDIT (exact uppercase)
    "amount", 250000,
    "currency", "XOF",
    "transactionType", "cashout",
    "channel", "agent",
    "isCash", true
));
// occurredAt defaults to now. result: transactionId, decision (CLEAR|REVIEW|BLOCKED), riskScore,
// deviceAction (ALLOW|SOFT_CHALLENGE|HARD_CHALLENGE|BLOCK|null), alerts[]

Map<String, Object> page = protegey.transactions.list(Map.of("decision", "BLOCKED", "limit", 50));
Map<String, Object> tx = protegey.transactions.get("tx-00234"); // Protegey id or your external id
```

Note that `amount` is a decimal string (`"250000.00"`) in `transactions.list()` and in alert
`transaction` snapshots.

### Cash deposits

```java
Map<String, Object> deposit = new HashMap<>();
deposit.put("externalTransactionId", "dep-0001"); // optional, generated if omitted — send it: a repeat gets a 409
deposit.put("externalCustomerId", "cust-9981");
deposit.put("customerName", "Moussa Traoré");
deposit.put("amount", 2500000);
deposit.put("currency", "XOF");                   // ISO 4217, required
deposit.put("channel", "BRANCH");
deposit.put("branchId", "DKR-PLATEAU");
deposit.put("depositorName", "Awa Diallo");       // who brought the cash, if not the account holder
deposit.put("metadata", Map.of("tellerId", "T-04"));
Map<String, Object> recorded = protegey.cashDeposits.record(deposit); // depositedAt defaults to now
// cashDepositId, externalTransactionId, decision (CLEAR|REVIEW|BLOCKED), riskScore, alerts[]

protegey.cashDeposits.list(Map.of("dateFrom", "2026-11-01", "minAmount", 1000000, "channel", "BRANCH"));
// filters: page, limit (≤100), dateFrom, dateTo, externalCustomerId, minAmount, maxAmount,
// currency, channel, decision, branchId, agentId — amount is a decimal string ("2500000.00")
protegey.cashDeposits.get("dep-0001"); // Protegey cashDepositId or your externalTransactionId, + alerts
```

### Identity verification (KYC)

```java
Map<String, Object> session = protegey.kyc.startSession("cust-9981", "awa@example.com", "+221771234567");
// Send session.get("url") to your user (single-use, valid 30 minutes)

Map<String, Object> current = protegey.kyc.getSession((String) session.get("sessionId"));
// status (final decision), outcome (SUCCESS|MANUAL|FAILED|null),
// recommendation (APPROVE|REVIEW|REJECT|null — Protegey's verdict, never changed by an override),
// decision, fullName, ..., images

protegey.kyc.listSessions(Map.of("status", "IN_REVIEW"));
protegey.kyc.findByExternalUserId("cust-9981");  // exact match on your customer id
protegey.kyc.setStatus(sessionId, "APPROVED", "Checked in branch"); // = updateSession(status + reviewNote)
protegey.kyc.updateSession(sessionId, Map.of("fullName", "Awa Diallo")); // correct an OCR misread
protegey.kyc.resendSession(sessionId); // fresh link, status RESUBMITTED
```

Photos come as pre-signed links in `images` (on `getSession()` and every `listSessions()` row):
`documentFront`, `documentBack`, `selfie`, `documentFaceCrop`, each `{url, expiresAt}` or null.
Links are valid for **1 hour** and re-issued on every GET — fetch the session again instead of
storing a URL. `getSession()` still returns the legacy base64 photo fields for backward
compatibility.

A manual status change (`setStatus()`/`updateSession()` or the dashboard) is recorded in
`decision.manualReview` (`reviewedBy`, `reviewedAt`, `previousStatus`, `newStatus`, `note`) and
fires the `kyc.status_changed` webhook like an automatic result.

### Business onboarding (KYB)

```java
// contactEmail OR contactPhone (at least one). Hand applicationUrl to the applicant yourself
// when you only have a phone number — Protegey only emails when contactEmail is set.
Map<String, Object> applicant = protegey.kyb.invite("John Mbarga", null, "+237690000000");
String link = (String) applicant.get("applicationUrl");

protegey.kyb.listApplicants(Map.of("stage", "responded"));
protegey.kyb.getApplicant(clientId);          // includes submission
protegey.kyb.resendInvitation(clientId);      // returns the submission + a new applicationUrl
protegey.kyb.screen(clientId);                // sanctions/PEP on the business and its owners
protegey.kyb.approve(clientId);
protegey.kyb.reject(clientId, "Registration number does not match");
protegey.kyb.requestMoreInfo(clientId, "Please upload the RCCM extract"); // returns applicationUrl
```

### Alerts

```java
protegey.alerts.list(Map.of("status", "OPEN"));
Map<String, Object> alert = protegey.alerts.get(alertId);
protegey.alerts.update(alertId, Map.of("status", "CONFIRMED", "disposition", "CONFIRMED_FRAUD"));
Map<String, Object> escalated = protegey.alerts.convertToCase(alertId); // {alert, case}
protegey.alerts.flagTransaction(transactionId); // Protegey transaction uuid
```

### Device and behavioral events

```java
protegey.deviceEvents.record(Map.of(
    "eventId", java.util.UUID.randomUUID().toString(),
    "visitorId", visitorIdFromYourFrontend,
    "externalCustomerId", "cust-9981",
    "deviceAttributes", Map.of("platform", "android", "isRooted", false, "isVpnActive", true)
)); // {recorded, action, riskScore}

protegey.behavioralEvents.record(Map.of(
    "externalCustomerId", "cust-9981",
    "sessionId", "sess-1",
    "keystroke", Map.of("avgHoldTimeMs", 95, "typingSpeedCharsPerSec", 4.2)
)); // {status (LEARNING|SCORED), deviationScore, confidenceTier, stepUpRecommended, escalatedAlertId}
```

### Sanctions screening and shared signals

```java
Map<String, Object> screening = protegey.sanctions.screen("Jane Doe", "person");
// decision (BLOCKED|REVIEW|CLEAR), score, pepMatch, matches[], provider, providers, degraded

Map<String, Object> signal = protegey.sharedSignal.check(Map.of(
    "phoneNumber", "+221771234567",
    "externalCustomerId", "cust-9981"
));
// flagged, category, reportedDaysAgo, flagConfidence, matchCount, matches[{category, reportedDaysAgo}]
```

## Verifying incoming webhooks

```java
import com.protegey.sdk.WebhookVerifier;

String payload = rawRequestBody; // the RAW body — do not re-encode/re-serialize it
String timestamp = request.getHeader("X-Timestamp");
String signature = request.getHeader("X-Signature");

if (!WebhookVerifier.verify(payload, timestamp, signature, yourWebhookSecret, 300)) {
    response.setStatus(401);
    return;
}
```

The signature is `hex(HMAC_SHA256(secret, "{X-Timestamp}.{rawBody}"))`, compared in constant
time; the 5-argument overload also rejects timestamps more than N seconds from now.

Event `type`s: `kyc.status_changed`, `kyb.status_changed`, `alert.created`,
`alert.status_changed`, `transaction.blocked`, `webhook.test` (constants on `WebhookVerifier`).
Failed deliveries are retried up to 5 times over ~3 hours — deduplicate on `eventId` and ignore
unknown types.

- `kyc.status_changed`: `{type, eventId, externalUserId, status, recommendation, sessionId,
  decision}` — fires on an automatic result **and** on a manual status change (dashboard or
  `kyc.setStatus()`/`updateSession()`). `sessionId` is the id `startSession()` returned.
- `kyb.status_changed`: `{type, eventId, clientId, status, submissionStatus, decision}` —
  `submissionStatus` is UPPER_SNAKE_CASE (e.g. `APPROVED`).

## `baseUrl` — no default, on purpose

Production is `https://api.protegey.com` (paths are `/partner-api/...`, no version prefix).
Confirm the value for your environment with Protegey before you ship.

## Development

```bash
mvn verify
```

## Security note

Keep your API key and webhook secret out of source control, the same way you would any other
secret.
