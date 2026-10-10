## 0.2.0

- `transactions.report()` accepts new optional keys: `channel` ("branch"|"atm"|"pos"|"online"|"mobile_app"|"ussd"|"agent"|"api"|"call_center"), `counterpartyInstitutionCode`, `counterpartyCountry` — lets Pan Studio rules target bank-wire and cross-border scenarios, not just mobile-money structuring. No code change needed on your side beyond passing the new keys: the SDK already forwards any key you give it.

## 0.1.0

- Initial release.
- `Protegey.transactions.report()` — transaction monitoring.
- `Protegey.kyc.startSession()` / `Protegey.kyc.getSession()` — identity verification + webhook-polling fallback.
- `WebhookVerifier.verify()` — HMAC-SHA256 signature verification for incoming webhooks.
- `baseUrl` is a required constructor parameter, with no built-in default — see the README for why.
