## 0.6.0

- `kyc.startSession()` gained an overload accepting optional `email`/`phone`: `startSession(externalUserId, email, phone)`. Never Protegey-verified, trusted as given — feeds identity-resolution's cross-partner matching. The existing single-argument `startSession(externalUserId)` is unchanged and still works.
- `transactions.report()`'s Javadoc and the README now document `devicePhoneNumber` — the partner's own customer-account identifier, never extracted from the device (it has nothing to do with device fingerprinting, which this SDK doesn't collect). No code change: the SDK already forwarded this key, it just wasn't documented.
- Fixed test fixtures that used a stale lowercase `decision` value (`"clear"`) — the real wire value from `POST /partner-api/transactions` is always uppercase (`CLEAR`/`REVIEW`/`BLOCKED`). No SDK behavior changed; this only affected test assertions.
- Fixed a `KycModuleTest` fixture using a leftover third-party Didit URL; the real `url` returned by `POST /partner-api/kyc/sessions` is a Protegey-hosted FaceTec capture link (`kyc.protegey.com`), never a third-party redirect. Test-only change.

## 0.5.0

- `transactions.report()` accepts a new optional key: `customerName` — shows up directly in the Pan Studio transactions table instead of just the opaque `externalCustomerId`. Self-declared, never verified by Protegey. Send it once and it's remembered on this customer's later transactions too, even if you omit it on some of them. No code change needed on your side: the SDK already forwards any key you give it.

## 0.4.0

- `transactions.report()` accepts a new optional key: `accountBalanceBefore` — lets a Pan Studio rule flag a withdrawal/transfer that drains most of the account balance, a classic mule-account/account-drain signal. Omit it and the corresponding rule field simply never fires. No code change needed on your side: the SDK already forwards any key you give it.

## 0.3.0

- `transactions.report()` accepts new optional keys: `lastPasswordChangeAt`, `lastEmailChangeAt`, `lastPhoneChangeAt` (ISO 8601) — lets a Pan Studio rule flag a large transaction shortly after an account credential change, a classic account-takeover pattern. `lastPhoneChangeAt` also covers a SIM swap for telcos. Omit any of them and the corresponding rule field simply never fires — Protegey never guesses this. No code change needed on your side beyond passing the new keys: the SDK already forwards any key you give it.

## 0.2.0

- `transactions.report()` accepts new optional keys: `channel` ("branch"|"atm"|"pos"|"online"|"mobile_app"|"ussd"|"agent"|"api"|"call_center"), `counterpartyInstitutionCode`, `counterpartyCountry` — lets Pan Studio rules target bank-wire and cross-border scenarios, not just mobile-money structuring. No code change needed on your side beyond passing the new keys: the SDK already forwards any key you give it.

## 0.1.0

- Initial release.
- `Protegey.transactions.report()` — transaction monitoring.
- `Protegey.kyc.startSession()` / `Protegey.kyc.getSession()` — identity verification + webhook-polling fallback.
- `WebhookVerifier.verify()` — HMAC-SHA256 signature verification for incoming webhooks.
- `baseUrl` is a required constructor parameter, with no built-in default — see the README for why.
