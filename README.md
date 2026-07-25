# authentication

Authentication decision substrate. It combines factor results from FaceID,
TouchID, WebAuthn, OTP, CACAO, OAuth/OIDC/SAML, or host-defined factors.

`docs/IDENTITY-BAAS.md` describes the Clerk-shaped identity service built on
top of it (`authentication.identity`, `authentication.identity-service`);
`docs/PRODUCTION-HARDENING.md` describes the fail-closed contracts a host must
apply before exposing that service publicly.

## Two hosts, two verifier shapes

Factor verification is synchronous where the platform API is
(`IFactorVerifier`) and Promise-returning where it is not
(`IAsyncFactorVerifier`) — every WebCrypto-backed edge verifier is the latter,
because `crypto.subtle` returns Promises. `authentication.async/authenticate!`
takes a verifier map holding either kind and reaches the same decision
`authentication.core/decide` would, so a Cloudflare Worker and a JVM host stay
on one decision path instead of two.

That distinction is why `cacao` has two production adapters, not one:

| adapter | host | crypto |
|---|---|---|
| `authentication.adapters.cacao` | JVM / Node | `cacao.core` (`node:crypto`, `js/Buffer`) |
| `authentication.adapters.cacao-edge` | Cloudflare Workers, browsers | `cacao.edge.verify` (WebCrypto `crypto.subtle`) |

Both verify the same wire format. The edge adapter additionally carries the
policy the crypto layer deliberately does not — audience binding, required
capability URIs, and the **per-DID issuer check**: when a factor request names
a subject, the CACAO's issuer must equal it, so a crypto-valid CACAO from one
DID can never authenticate another (ADR-2607177000's structural rule, applied
at the authentication layer rather than re-derived per product Worker).

## Tests

```bash
clojure -M:test                                   # JVM: models, policy, decisions
nbb --classpath "src:test:../org-chainagnostic-cacao/src" \
    test/authentication/cacao_edge_smoke.cljs     # edge CACAO, real WebCrypto Ed25519
```

The `.cljs` smokes are not reachable from `clojure -M:test` (they are CLJS-only
by construction) and are not yet wired into CI.
