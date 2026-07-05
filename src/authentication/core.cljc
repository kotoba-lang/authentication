(ns authentication.core
  "Authentication orchestration as data — combines credential verifiers
  (password, OTP, ...) into one auth result, tracking which methods
  succeeded (the OIDC 'amr' — Authentication Methods References — concept).

  Real password hashing/verification and OTP checking are injected host
  capabilities (real crypto can't be zero-dep, same seam every
  langchain-clj host uses). OAuth2/OIDC/SAML/WebAuthn/biometric flows live
  in their own kotoba-lang repos (`org-ietf-oauth2`, `org-openid-oidc`,
  `org-oasis-saml`, `org-w3-webauthn`, `com-apple-touchid`,
  `com-apple-faceid`) and feed their own successful outcome into this
  repo's `auth-result` shape rather than being re-implemented here."
  (:require [clojure.string :as str]))

;; ───────────────────────────── auth-result ─────────────────────────────

(defn auth-result
  "Constructor for the common result shape every verifier below returns.
  `amr` defaults to `[method]` on :success and `[]` on :failure, matching
  OIDC's Authentication Methods References list. `reason` is an optional
  keyword explaining a :failure, e.g. :bad-credentials/:locked/:not-found."
  [{:keys [status method identity-ref amr authenticated-at reason]}]
  {:status status
   :method method
   :identity-ref identity-ref
   :amr (or amr (if (= status :success) [method] []))
   :authenticated-at authenticated-at
   :reason reason})

;; ───────────────────────────── password ────────────────────────────────

(defprotocol IPasswordHasher
  "Password hashing host capability."
  (-hash [this plaintext]
    "Returns an opaque hash string.")
  (-verify [this plaintext hash]
    "Returns true if `plaintext` matches `hash`."))

;; test-only, insecure — see mock-password-hasher below
(defrecord MockPasswordHasher []
  IPasswordHasher
  (-hash [_ plaintext] (str "mock:" (str/reverse plaintext)))
  (-verify [_ plaintext hash] (= hash (str "mock:" (str/reverse plaintext)))))

(defn mock-password-hasher
  "A trivial, INSECURE, deterministic IPasswordHasher — tests only. Never
  use this outside a test: it is reversed-string \"hashing\", not real
  crypto. Real hosts inject a real password hasher (bcrypt/argon2/etc.)."
  []
  (->MockPasswordHasher))

;; ───────────────────────────── verifiers ────────────────────────────────

(defprotocol ICredentialVerifier
  "A single authentication-method verifier."
  (-verify-credential [this credential]
    "`credential` is method-specific, e.g.
     {:method :password :identity-ref id :plaintext p} or
     {:method :otp :identity-ref id :code c}.
     Returns an auth-result map."))

(defrecord PasswordVerifier [hasher lookup-hash-fn]
  ICredentialVerifier
  (-verify-credential [_ {:keys [identity-ref plaintext]}]
    (let [stored-hash (lookup-hash-fn identity-ref)]
      (cond
        (nil? stored-hash)
        (auth-result {:status :failure :method :password
                       :identity-ref identity-ref :reason :not-found})

        (-verify hasher plaintext stored-hash)
        (auth-result {:status :success :method :password
                       :identity-ref identity-ref})

        :else
        (auth-result {:status :failure :method :password
                       :identity-ref identity-ref :reason :bad-credentials})))))

(defn password-verifier
  "Returns an ICredentialVerifier for {:method :password ...} credentials.
  `lookup-hash-fn` is an injected `[identity-ref] -> stored-hash-or-nil`."
  [hasher lookup-hash-fn]
  (->PasswordVerifier hasher lookup-hash-fn))

(defrecord OtpVerifier [check-code-fn]
  ICredentialVerifier
  (-verify-credential [_ {:keys [identity-ref code]}]
    (if (check-code-fn identity-ref code)
      (auth-result {:status :success :method :otp :identity-ref identity-ref})
      (auth-result {:status :failure :method :otp
                     :identity-ref identity-ref :reason :bad-credentials}))))

(defn otp-verifier
  "Returns an ICredentialVerifier for {:method :otp ...} credentials.
  `check-code-fn` is an injected `[identity-ref code] -> bool`, e.g. backed
  by `kotoba-lang/onetime`'s TOTP verification in a real host."
  [check-code-fn]
  (->OtpVerifier check-code-fn))

;; ───────────────────────────── amr ────────────────────────────────

(defn combine-amr
  "Given several auth-results (e.g. password succeeded, then OTP
  succeeded), returns the merged :amr vector — only methods from
  :success results, in the order given, deduped. This is what a caller
  feeds forward toward kotoba-lang/mfa's policy check."
  [& auth-results]
  (->> auth-results
       (filter #(= :success (:status %)))
       (mapcat :amr)
       distinct
       vec))
