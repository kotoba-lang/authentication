(ns authentication.adapters.datomic
  "Datomic query/transaction plans for durable identity state. This adapter is
  data-oriented: a host supplies `q` and `transact!`, keeping Datomic client and
  peer APIs out of portable CLJS bundles."
  (:require [authentication.identity :as identity]
            [authentication.identity-ports :as ports]
            [authentication.schema :as canonical]))

(def schema
  "The identity attributes, from the one place they are declared. This
  namespace used to hold its own copy, which is how it and the in-memory
  store came to describe the same database in two dialects."
  canonical/identity)

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
