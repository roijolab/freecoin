;; Freecoin - digital social currency toolkit

;; part of Decentralized Citizen Engagement Technologies (D-CENT)
;; R&D funded by the European Commission (FP7/CAPS 610349)

;; Wrapper based on Secret Share Java implementation by Tim Tiemens
;; Copyright (C) 2015 Denis Roio <jaromil@dyne.org>

;; Shamir's Secret Sharing algorithm was invented by Adi Shamir
;; Shamir, Adi (1979), "How to share a secret", Communications of the ACM 22 (11): 612–613
;; Knuth, D. E. (1997), The Art of Computer Programming, II: Seminumerical Algorithms: 505

;; This library is free software; you can redistribute it and/or
;; modify it under the terms of the GNU Lesser General Public
;; License as published by the Free Software Foundation; either
;; version 3 of the License, or (at your option) any later version.

;; This library is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; Lesser General Public License for more details.

;; You should have received a copy of the GNU Lesser General Public
;; License along with this library; if not, write to the Free Software
;; Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

(ns freecoin.secretshare
  (:gen-class)
  (:import
   [com.tiemens.secretshare.engine SecretShare]
   [java.math]
   )
  (:require
   [freecoin.random :as rand]
   [hashids.core :as hash]

   [clojure.string :only (join split) :as str]
   [clojure.pprint :as pp]
   )

  )

(defn prime384 []
  (SecretShare/getPrimeUsedFor384bitSecretPayload))

(defn prime192 []
  (SecretShare/getPrimeUsedFor192bitSecretPayload))

(defn prime4096 []
  (SecretShare/getPrimeUsedFor4096bigSecretPayload))

;; defaults
(def config
  {
   :version 1
   :total 8
   :quorum 4

   :prime (prime4096)

   :description "Freecoin 0.2"

   ;; this alphabet excludes ambiguous chars:
   ;; 1,0,I,O can be confused on some screens
   :alphabet "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

   ;; the salt should be a secret shared password
   ;; known to all possessors of the key pieces
   :salt "La gatta sul tetto che scotta"
})

;; obsolete
(defn secret-conf

  ([ n k m description]
   (com.tiemens.secretshare.engine.SecretShare$PublicInfo.
    (int n) k m description)
   )

  ([ sec ]
   (com.tiemens.secretshare.engine.SecretShare$PublicInfo.
    (int (:total sec))
    (:quorum sec)
    (:prime sec)
    (:description sec)
    )
   )

  )

(defn ss
  ([pi]
   (SecretShare.
    (com.tiemens.secretshare.engine.SecretShare$PublicInfo.
     (int (:total pi))
     (:quorum pi)
     (:prime pi)
     (:description pi)
     )
    )))

(defn conf2map [si]
  "Convert a secretshare configuration structure into a clojure map"
  (let [pi (.getPublicInfo si)]
    {:index (.getIndex si)
     :share (.getShare si)
     :quorum (.getN pi)
     :total (.getK pi)
     :prime (.getPrimeModulus pi)
     :uuid (.getUuid pi)
     :description (.getDescription pi)}))

(defn map2conf [m]
  "Convert a clojure map into a secretshare configuration structure"
  (com.tiemens.secretshare.engine.SecretShare$ShareInfo.
   (:index m) (:share m)
   (com.tiemens.secretshare.engine.SecretShare$PublicInfo.
    (:quorum m)
    (:total m)
    (:prime m)
    (:description m))))

(defn shamir-split
  ([conf data]
   (map conf2map
        (.getShareInfos
         (.split (ss conf) data))))
  )


(defn shamir-combine
  ([shares]
   (let [shares (map map2conf shares)
         pi     (.getPublicInfo (first shares))]
     (.getSecret
      (.combine (SecretShare. pi) (vec shares)))))
  )

(defn hash-encode [conf num]
  {:pre  [(integer? num)]
   :post [(string? %)]}
  (str (hash/encode conf num))
  )

(defn hash-decode [conf str]
  {:pre  [(string? str)]
   :post [(integer? %)]}
  (biginteger
   (first
    (hash/decode conf str)))
  )

(defn create-single
  ([conf]
   (let [secnum
         [(rand/create 16 3.1)
          (rand/create 16 3.1)]]
     (create-single conf secnum)
     ))
  
  ([conf secnum]
   {:pre  [(contains? conf :version)]
    :post [(> (:entropy-lo %) 0)
           (> (:entropy-hi %) 0)]
    }
   "Creates a single key set"

   {
    :entropy-lo (:entropy (first secnum))
    :entropy-hi (:entropy (second secnum))
    ;; comment this one to avoid saving the secret key
    :key (format "%s_FXC_%s"
                 (hash-encode conf (:integer (first secnum)))
                 (hash-encode conf (:integer (second secnum))))
    }
   
   )
  )

(defn split [conf secret]
  {:pre [(string? secret)]}
  (let [keys (str/split secret #"_")
        lo (shamir-split conf (hash-decode conf (get keys 0)))
        hi (shamir-split conf (hash-decode conf (get keys 2)))]
    {:uuid (:uuid (first lo))
     :shares [ (map (partial hash-encode config) (map :share lo))
               (map (partial hash-encode config) (map :share hi)) ]}
    )
  )

;; (defn compose [conf secret]
;;   {:pre [(string? secret)]}
;;   ;; in auth the format of the token is defined as map { :uid :val }
;;   (let [token (str/split (:val secret) #"_")
;;         uid (:uid secret)]
;;     ;; retrieve 
  


;; TODO: call create-single in here to avoid duplicate code
(defn create-shared
  ([conf]
   (let [secnum
         [(rand/create 16 3.1)
          (rand/create 16 3.1)]]
     (create-shared conf secnum)
   ))

  ;; keys are tuples (hi+lo) to match the entropy needed by
  ;; NXT passphrases
  ([conf secnum]
   {:pre  [(contains? conf :version)]
    :post [(> (:entropy-lo %) 0)
           (> (:entropy-hi %) 0)]
    }
   "Creates a shared key set"

   (let [lo (shamir-split conf (:integer (first secnum)))
         hi (shamir-split conf (:integer (second secnum)))]
   {:shares-lo lo
    :shares-hi hi
    :entropy-lo (:entropy (first secnum))
    :entropy-hi (:entropy (second secnum))
    ;; comment this one to avoid saving the secret key
    :key (format "%s_FXC_%s"
                 (hash-encode conf (:integer (first secnum)))
                 (hash-encode conf (:integer (second secnum))))
    }))

  )

(defn unlock
  [conf secret]
  (let [lo (shamir-combine (:shares-lo secret))
        hi (shamir-combine (:shares-hi secret))]
    (format "%s_FXC_%s"
            (hash-encode conf lo)
            (hash-encode conf hi)
            )))
