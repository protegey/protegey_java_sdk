## 0.1.0

- Initial release.
- `Protegey.transactions.report()` — transaction monitoring.
- `Protegey.kyc.startSession()` / `Protegey.kyc.getSession()` — identity verification + webhook-polling fallback.
- `WebhookVerifier.verify()` — HMAC-SHA256 signature verification for incoming webhooks.
- `baseUrl` is a required constructor parameter, with no built-in default — see the README for why.
