(ns authentication.webcrypto-smoke
  (:require [authentication.adapters.webcrypto :as crypto]))

(defn fail! [error]
  (js/console.error error)
  (set! (.-exitCode js/process) 1))

(defn ^:export main []
  (-> (crypto/pkce-pair)
      (.then (fn [pkce]
               (when-not (and (= "S256" (aget pkce "method"))
                              (> (count (aget pkce "verifier")) 43))
                 (throw (js/Error. "invalid PKCE pair")))
               (crypto/credential-envelope "correct horse battery staple")))
      (.then (fn [envelope]
               (js/Promise.all
                #js [(crypto/verify-password "correct horse battery staple" envelope)
                     (crypto/verify-password "incorrect password value" envelope)])))
      (.then (fn [results]
               (when-not (and (true? (aget results 0)) (false? (aget results 1)))
                 (throw (js/Error. "password verification failed")))
               (js/console.log "ok - WebCrypto PKCE and password adapter")))
      (.catch fail!)))

(main)
