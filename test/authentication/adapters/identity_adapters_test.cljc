(ns authentication.adapters.identity-adapters-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [authentication.adapters.oauth :as oauth]
            [authentication.adapters.email :as email]))

(deftest google-authorization-enforces-pkce-and-oidc-nonce
  (let [url (oauth/authorization-url
             {:provider :google :client-id "client" :redirect-uri "https://kotobase.net/auth/callback"
              :state "state" :nonce "nonce" :code-challenge "challenge"})]
    (is (str/includes? url "code_challenge_method=S256"))
    (is (str/includes? url "nonce=nonce"))
    (is (str/includes? url "scope=openid+email+profile"))))

(deftest oidc-refuses-a-missing-nonce
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (oauth/authorization-url
                {:provider :google :client-id "client" :redirect-uri "https://kotobase.net/auth/callback"
                 :state "state" :code-challenge "challenge"}))))

(deftest provider-profiles-share-one-contract
  (is (= [:google "123"]
         ((juxt :identity/provider :identity/provider-subject)
          (oauth/google-profile {:sub "123" :email "A@Example.com" :email_verified true}))))
  (is (= [:github "42"]
         ((juxt :identity/provider :identity/provider-subject)
          (oauth/github-profile {:id 42 :login "a"} {:email "a@example.com" :verified true})))))

(deftest email-challenge-is-expiring-and-single-use
  (let [c (email/challenge {:id "c1" :purpose :login :email "a@example.com"
                            :token-digest (apply str (repeat 64 "a"))
                            :created-at 10 :expires-at 20})]
    (is (email/challenge-usable? c 19))
    (is (not (email/challenge-usable? c 20)))
    (is (= true (:identity.email-challenge/used? (first (email/consume-tx "c1" 15)))))))
