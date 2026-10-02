# protegey-sdk (Java)

Official Protegey SDK for Java backends — transaction reporting, identity verification sessions,
and webhook signature verification, called directly from your server with your own API key.

Backend-only: no device fingerprinting or behavioral biometrics here (those are browser/mobile
concepts) — see `@protegey/sdk` (JS), `protegey_sdk` (Flutter) or `@protegey/react-native-sdk` for
those.

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
  <version>0.1.0</version>
</dependency>
```

Source: [github.com/protegey/protegey_java_sdk](https://github.com/protegey/protegey_java_sdk)

## Usage

```java
import com.protegey.sdk.Protegey;
import java.util.Map;

Protegey protegey = new Protegey("YOUR_API_KEY", "https://api.protegey.com");

// Transactions
Map<String, Object> result = protegey.transactions.report(Map.of(
    "externalTransactionId", "tx-00234",
    "externalCustomerId", "cust-9981",
    "direction", "DEBIT",
    "amount", 250000,
    "currency", "XOF",
    "transactionType", "cashout",
    "isCash", true
));

// Identity verification — no manual API call needed, the SDK starts the session and hands back the link
Map<String, Object> session = protegey.kyc.startSession("cust-9981");
// Send session.get("url") to your user however you like (SMS, email, your own hosted redirect page)

// Polling fallback — webhook delivery is best-effort (one retry, no queue), so use this if you're
// not sure a delivery ever arrived, or just want to double-check a session's status.
Map<String, Object> current = protegey.kyc.getSession((String) session.get("sessionId"));
```

## Verifying incoming webhooks

```java
import com.protegey.sdk.WebhookVerifier;

String payload = rawRequestBody; // the RAW body — do not re-encode/re-serialize it
String timestamp = request.getHeader("X-Timestamp");
String signature = request.getHeader("X-Signature");

if (!WebhookVerifier.verify(payload, timestamp, signature, yourWebhookSecret)) {
    response.setStatus(401);
    return;
}
```

## `baseUrl` — no default, on purpose

Confirm the current value with Protegey before you ship — it can differ between environments and
change independently of this package's version.

## Development

```bash
mvn test
```

## Security note

Keep your API key and webhook secret out of source control, the same way you would any other
secret.
