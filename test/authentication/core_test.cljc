(ns authentication.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [authentication.core :as auth]))

(deftest auth-result-test
  (testing "success defaults :amr to [method]"
    (is (= {:status :success :method :password :identity-ref "u1"
            :amr [:password] :authenticated-at nil :reason nil}
           (auth/auth-result {:status :success :method :password :identity-ref "u1"}))))
  (testing "failure defaults :amr to []"
    (is (= [] (:amr (auth/auth-result {:status :failure :method :password
                                        :identity-ref "u1" :reason :bad-credentials})))))
  (testing "explicit :amr is respected"
    (is (= [:password :otp]
           (:amr (auth/auth-result {:status :success :method :otp
                                     :identity-ref "u1" :amr [:password :otp]}))))))

(deftest mock-password-hasher-test
  (let [hasher (auth/mock-password-hasher)
        h (auth/-hash hasher "s3cret")]
    (testing "verify matches the same plaintext"
      (is (true? (auth/-verify hasher "s3cret" h))))
    (testing "verify rejects a different plaintext"
      (is (false? (auth/-verify hasher "wrong" h))))))

(deftest password-verifier-test
  (let [hasher (auth/mock-password-hasher)
        store {"u1" (auth/-hash hasher "s3cret")}
        verifier (auth/password-verifier hasher #(get store %))]
    (testing "happy path"
      (let [r (auth/-verify-credential verifier {:method :password :identity-ref "u1" :plaintext "s3cret"})]
        (is (= :success (:status r)))
        (is (= [:password] (:amr r)))))
    (testing "bad credentials"
      (let [r (auth/-verify-credential verifier {:method :password :identity-ref "u1" :plaintext "nope"})]
        (is (= :failure (:status r)))
        (is (= :bad-credentials (:reason r)))))
    (testing "unknown identity"
      (let [r (auth/-verify-credential verifier {:method :password :identity-ref "ghost" :plaintext "s3cret"})]
        (is (= :failure (:status r)))
        (is (= :not-found (:reason r)))))))

(deftest otp-verifier-test
  (let [verifier (auth/otp-verifier (fn [id code] (and (= id "u1") (= code "123456"))))]
    (testing "happy path"
      (let [r (auth/-verify-credential verifier {:method :otp :identity-ref "u1" :code "123456"})]
        (is (= :success (:status r)))
        (is (= [:otp] (:amr r)))))
    (testing "bad code"
      (let [r (auth/-verify-credential verifier {:method :otp :identity-ref "u1" :code "000000"})]
        (is (= :failure (:status r)))
        (is (= :bad-credentials (:reason r)))))))

(deftest combine-amr-test
  (testing "merges :success results in order, deduped"
    (let [password-ok (auth/auth-result {:status :success :method :password :identity-ref "u1"})
          otp-ok (auth/auth-result {:status :success :method :otp :identity-ref "u1"})
          password-ok-again (auth/auth-result {:status :success :method :password :identity-ref "u1"})]
      (is (= [:password :otp] (auth/combine-amr password-ok otp-ok password-ok-again)))))
  (testing "excludes :failure results"
    (let [password-ok (auth/auth-result {:status :success :method :password :identity-ref "u1"})
          otp-failed (auth/auth-result {:status :failure :method :otp :identity-ref "u1" :reason :bad-credentials})]
      (is (= [:password] (auth/combine-amr password-ok otp-failed))))))
