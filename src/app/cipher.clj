(ns app.cipher
  (:require [app.util :refer :all]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [byte-streams :as b])
  (:import [javax.crypto Cipher]
           [javax.crypto.spec SecretKeySpec IvParameterSpec]))

(def aes-block-size 16)

(defn pkcs7-pad [blocksize d]
  {:pre [(s/valid? :app.util/data d) (s/valid? pos-int? blocksize)]}
  (let [bytes-in-last-block (mod (count d) blocksize)
        num-bytes-to-add (- blocksize bytes-in-last-block)]
    (if (pos? bytes-in-last-block)
      (into d (repeat num-bytes-to-add (unchecked-byte num-bytes-to-add)))
      d)))

(defn pkcs7-unpad [blocksize d]
  {:pre [(s/valid? :app.util/data d) (s/valid? pos-int? blocksize)]}
  (let [last-byte (last d)]
    (if (and (pos? last-byte) (< last-byte blocksize))
      (let [possible-pad (subvec d (- (count d) last-byte) (count d))]
        (if (every? #(= last-byte %) possible-pad)
          (subvec d 0 (- (count d) last-byte))
          d))
      d)))

(defn pkcs-unpad-ex [blocksize d]
  {:pre [(s/valid? :app.util/data d) (s/valid? pos-int? blocksize)]}
  (let [last-byte (last d)]
    (if (and (pos? last-byte) (< last-byte blocksize))
      (let [possible-pad (subvec d (- (count d) last-byte) (count d))]
        (if (every? #(= last-byte %) possible-pad)
          (subvec d 0 (- (count d) last-byte))
          (throw (ex-info "Invalid PKCS padding" {:bytes (take-last blocksize d)}))))
      d)))

(def ^:private cipher (Cipher/getInstance "AES/ECB/NoPadding"))
(defn- ecb [mode key d]
  (.init cipher mode (SecretKeySpec. (data->bytes key) "AES"))
  #_(println (bytes->data (.doFinal cipher (data->bytes d))))
  (bytes->data (.doFinal cipher (data->bytes d))))

(defn ecb-decrypt [k d]
  (pkcs7-unpad aes-block-size (ecb Cipher/DECRYPT_MODE k d)))

(defn ecb-encrypt [k d]
  (ecb Cipher/ENCRYPT_MODE k (pkcs7-pad aes-block-size d)))

(deftest ecbtest
  (let [k (pkcs7-pad aes-block-size (string->data "YELLOW SUBMARINE"))
        d (string->data "Come together... right now.. over me!")]
    (is (= d (ecb-decrypt k (ecb-encrypt k d))))))

;; CBC functions

(defn cbc-decrypt [k iv d]
  (let [blocks (partition aes-block-size (pkcs7-pad aes-block-size d))
        d1 (into [iv] conj (butlast blocks))
        d2 blocks]
    (pkcs7-unpad
     aes-block-size
     (vec (mapcat
           #(xor %1 (ecb-decrypt k %2))
           d1
           d2)))))

(defn cbc-encrypt [k iv d]
  (let [blocks (partition aes-block-size (pkcs7-pad aes-block-size d))]
    (vec
     (apply concat
            (rest (reductions
                   #(ecb-encrypt k (xor %1 %2))
                   iv
                   blocks))))))

;;; For comparison only, not for use
(defn- cbc-java [mode k iv d]
  (let [cipher (Cipher/getInstance "AES/CBC/NoPadding")
        bs (data->bytes (pkcs7-pad aes-block-size d))]
    (.init cipher mode
           (SecretKeySpec. (data->bytes k) "AES")
           (IvParameterSpec. (data->bytes iv)))
    (.doFinal cipher bs)))

(defn cbc-java-encrypt [k iv d]
  (bytes->data (cbc-java Cipher/ENCRYPT_MODE k iv d)))

(defn cbc-java-decrypt [k iv d]
  (bytes->data (cbc-java Cipher/DECRYPT_MODE k iv d)))

(deftest cbctest
  (let [k (string->data "YELLOW SUBMARINE")
        iv (vec (repeat aes-block-size (unchecked-byte 0)))
        d #_(string->data "Come together, R")
        (string->data "Come together, Right now")
        cipher-text (cbc-encrypt k iv d)
        ]
    (is (= d (cbc-decrypt k iv (cbc-encrypt k iv d))))))
