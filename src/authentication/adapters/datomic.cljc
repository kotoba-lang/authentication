(ns authentication.adapters.datomic
  "Datomic query/transaction plans for durable identity state. This adapter is
  data-oriented: a host supplies `q` and `transact!`, keeping Datomic client and
  peer APIs out of portable CLJS bundles."
  (:require [authentication.identity :as identity]
            [authentication.identity-ports :as ports]))

(def schema
  [{:db/ident :identity.user/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.user/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :identity.user/created-at :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :identity/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :identity/provider-subject :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :identity/key :db/valueType :db.type/tuple
    :db/tupleAttrs [:identity/provider :identity/provider-subject]
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity/user :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :identity/email :db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :identity/email-verified? :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
   {:db/ident :identity/created-at :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :identity.tenant/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.tenant/did :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.membership/user :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :identity.membership/tenant :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :identity.membership/role :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :identity.membership/key :db/valueType :db.type/tuple
    :db/tupleAttrs [:identity.membership/user :identity.membership/tenant]
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}])

(def find-user-by-identity-query
  '[:find ?user-id . :in $ ?provider ?subject
    :where
    [?identity :identity/provider ?provider]
    [?identity :identity/provider-subject ?subject]
    [?identity :identity/user ?user]
    [?user :identity.user/id ?user-id]])

(def find-user-by-verified-email-query
  '[:find ?user-id . :in $ ?email
    :where
    [?identity :identity/email ?email]
    [?identity :identity/email-verified? true]
    [?identity :identity/user ?user]
    [?user :identity.user/id ?user-id]])

(def user-view-query
  '[:find (pull ?user [*]) (pull ?identity [*]) (pull ?membership [*])
    :in $ ?user-id
    :where
    [?user :identity.user/id ?user-id]
    [?identity :identity/user ?user]
    [?membership :identity.membership/user ?user]])

(defn prepare-account-tx [tx]
  (mapv (fn [entity]
          (cond-> entity
            (:identity/provider entity)
            (assoc :identity/key [(:identity/provider entity)
                                  (:identity/provider-subject entity)])
            (and (:identity.membership/user entity) (:identity.membership/tenant entity))
            (assoc :identity.membership/key [(:identity.membership/user entity)
                                             (:identity.membership/tenant entity)])))
        tx))

(defn link-identity-tx [user-id profile now]
  [(assoc profile
          :db/id "new-identity"
          :identity/key (identity/identity-key profile)
          :identity/user [:identity.user/id user-id]
          :identity/created-at now)])

(defrecord DatomicIdentityStore [db q transact!]
  ports/IIdentityStore
  (-find-user-by-identity [_ provider subject]
    (q find-user-by-identity-query (db) provider subject))
  (-find-user-by-verified-email [_ email]
    (q find-user-by-verified-email-query (db) email))
  (-create-account! [_ tx] (transact! (prepare-account-tx tx)))
  (-link-identity! [_ user-id profile now]
    (transact! (link-identity-tx user-id profile now)))
  (-user-view [_ user-id]
    (q user-view-query (db) user-id)))

(defn store [options] (map->DatomicIdentityStore options))
