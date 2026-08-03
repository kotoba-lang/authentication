# authentication

Authentication decision substrate. It combines factor results from FaceID,
TouchID, WebAuthn, OTP, CACAO, OAuth/OIDC/SAML, or host-defined factors.

Email OTP and magic-link hosts use `authentication.adapters.email`: the common
record owns expiry, single-use state, subject binding, and the resulting
`:email` authentication factor. Token generation, digesting, constant-time
comparison, persistence, rate limiting, and delivery remain host ports because
their secure implementations differ by runtime and provider.

Applications that need only the provider-neutral email factor can depend on
this repository with `:deps/root "modules/email"`. That facet has no transitive
provider dependencies; requiring the full root keeps the FaceID, WebAuthn,
OAuth, OIDC, SAML, and other adapters available in a sibling workspace.
