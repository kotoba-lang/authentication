(ns authentication.adapters.datomic-test
  (:require [clojure.test :refer [deftest is]]
            [authentication.adapters.datomic :as datomic]
            [authentication.identity :as identity]
            [authentication.identity-ports :as ports]))

(deftest account-tx-adds-unique-tuple-keys
  (let [profile (identity/normalized-profile
                 {:provider :google :provider-subject "sub-1"
                  :email "a@example.com" :email-verified? true})
        tx (-> {:user-id "u1" :tenant-id "t1" :tenant-did "did:web:example:t1"
                :identity profile :now 1}
               identity/account-tx datomic/prepare-account-tx)]
    (is (= [:google "sub-1"] (:identity/key (second tx))))
    (is (= ["u1" "t1"] (:identity.membership/key (last tx))))))

(deftest store-delegates-query-and-transaction-plans
  (let [calls (atom [])
        store (datomic/store
               {:db (constantly :db)
                :q (fn [& args] (swap! calls conj args) "u1")
                :transact! (fn [tx] (swap! calls conj tx) {:tx-data tx})})]
    (is (= "u1" (ports/-find-user-by-identity store :google "sub")))
    (ports/-link-identity! store "u1"
                           (identity/normalized-profile
                            {:provider :github :provider-subject "42"}) 10)
    (is (= datomic/find-user-by-identity-query (ffirst @calls)))
    (is (= [:identity.user/id "u1"]
           (:identity/user (first (second @calls)))))))
