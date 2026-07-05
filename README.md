# kotoba-lang/authentication

[![CI](https://github.com/kotoba-lang/authentication/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/authentication/actions/workflows/ci.yml)

Authentication **orchestration** as data — combines credential verifiers
(password, OTP, ...) into one `auth-result`, tracking which methods
succeeded (the OIDC "amr" — Authentication Methods References — concept).
Every namespace is `.cljc`, with **zero third-party runtime deps**.

This is *not* a re-implementation of OAuth2, OpenID Connect, SAML,
WebAuthn, or biometric authentication — those already exist as their own
kotoba-lang repos and feed their own successful outcome into this repo's
`auth-result` shape instead of being duplicated here:

- [`org-ietf-oauth2`](https://github.com/kotoba-lang/org-ietf-oauth2)
- [`org-openid-oidc`](https://github.com/kotoba-lang/org-openid-oidc)
- [`org-oasis-saml`](https://github.com/kotoba-lang/org-oasis-saml)
- [`org-w3-webauthn`](https://github.com/kotoba-lang/org-w3-webauthn)
- [`com-apple-touchid`](https://github.com/kotoba-lang/com-apple-touchid)
- [`com-apple-faceid`](https://github.com/kotoba-lang/com-apple-faceid)

It's also not a TOTP/HOTP implementation — see
[`kotoba-lang/onetime`](https://github.com/kotoba-lang/onetime) for that.
Real password hashing/verification and OTP code checking are **injected
host capabilities** (real crypto can't be zero-dep, the same seam every
langchain-clj host uses) — this repo only orchestrates the result.

## Usage

```clojure
(require '[authentication.core :as auth])

;; a real host injects a real IPasswordHasher (bcrypt/argon2/etc.) and a
;; lookup fn; this example uses the insecure mock, tests only
(def hasher (auth/mock-password-hasher))
(def stored {"u1" (auth/-hash hasher "s3cret")})
(def pw-verifier (auth/password-verifier hasher #(get stored %)))
(def otp-verifier (auth/otp-verifier (fn [id code] (and (= id "u1") (= code "123456")))))

(def password-result
  (auth/-verify-credential pw-verifier {:method :password :identity-ref "u1" :plaintext "s3cret"}))

(def otp-result
  (auth/-verify-credential otp-verifier {:method :otp :identity-ref "u1" :code "123456"}))

(auth/combine-amr password-result otp-result)
;; => [:password :otp]
```

## Test

```bash
clojure -M:test
```
