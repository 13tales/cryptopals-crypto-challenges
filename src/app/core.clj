(ns app.core
  (:require [app.util :refer :all]
            [clojure.spec :as s]
            [clojure.string :as str :refer [upper-case lower-case]]
            [medley.core :refer [filter-keys map-vals map-keys]]))

(s/def ::probability (s/and number? #(<= 0 %) #(<= % 1)))
(s/def ::hist (s/map-of char? number?))
(s/def ::letter-hist (s/map-of char? ::probability))

(defn normalized-frequencies
  ([hist]
   {:pre [(s/valid? ::hist hist)]}
   (normalized-frequencies hist (reduce-kv (fn [total k v] (+ total v)) 0 hist)))
  ([hist total]
   {:pre [(s/valid? number? total)]}
   (map-vals #(/ % total) hist)))

(defn letter-frequency [hist]
  ;;; Lowercases all letters and removes non-letters
  {:pre [(s/valid? ::hist hist)]}
  (let [nonletter-hist (filter-keys #(not (Character/isLetter %)) hist)
        uppercase-hist (filter-keys #(Character/isUpperCase %) hist)
        lowercase-hist (filter-keys #(Character/isLowerCase %) hist)]
    (merge-with +
                nonletter-hist
                lowercase-hist
                (map-keys #(Character/toLowerCase %) uppercase-hist))))


(def en-letter-probabilities
  (normalized-frequencies {\space 18.29 \e 10.27 \t 7.51 \a 6.53 \o 6.16 \i 5.67 \n 5.71 \s 5.32 \r 4.99 \h 4.98 \l 3.32 \d 3.28 \u 2.28 \c 2.23 \m 2.03 \f 1.98 \w 1.07 \g 1.62 \p 1.50 \y 1.43 \b 1.26 \v 0.80 \k 0.56 \x 0.14 \j 0.10 \q 0.08 \z 0.05 \. 1.39 \, 1.35 \" 0.68 \- 0.48 \' 0.36 \? 0.14 \! 0.10 \; 0.09 \: 0.06}))

(defn chi2-for-letter [c num-c num-total-chars]
  {:pre [(s/valid? char? c)
         (s/valid? pos-int? num-c)
         (s/valid? (s/and pos-int? #(< num-c %)) num-total-chars)]}
  (let [c-prob (en-letter-probabilities c 0.001) ; Non-existent letters cannot have 0 probability
        expected-num-c (* c-prob num-total-chars)]
    (/ (* (- num-c expected-num-c) (- num-c expected-num-c))
       expected-num-c)))

(defn chi2 [hist]
  {:pre [(s/valid? ::hist hist)]}
  (let [num-total-chars (reduce-kv (fn [total k v] (+ total v)) 0 hist)]
    (reduce-kv
     (fn [X2 k v] (+ X2 (chi2-for-letter k v num-total-chars)))
     0
     hist)))

(defn chi2-results [bytes-to-xor cipher-data]
  {:pre [(s/valid? :app.util/data bytes-to-xor)
         (s/valid? :app.util/data cipher-data)]}
  (for [c bytes-to-xor]
    (let [res (xor-with-byte-fill cipher-bytes c)
          res-string (data->string res)
          hist (letter-frequency (frequencies res-string))]
      {:xor-result res-string
       :chi2 (chi2 hist)
       :char c})))

(def xor-search-bytes (concat (range 32 127) (range 0 31) (range 128 255))) ; Starts with printable chars
(defn most-likely-xor-byte [d]
  (:char (first (sort-by :chi2 (chi2-results xor-search-bytes d)))))

(defn hamming [d1 d2]
  #_{:pre [(s/valid? :app.util/data d1)
         (s/valid? :app.util/data d2)
         (= (count d1) (count d2))]}
  (reduce
   +
   0
   (map #(Long/bitCount %)
        (map bit-xor d1 d2))))

(s/fdef hamming
        :args (s/and (s/cat :d1 :app.util/data :d2 :app.util/data)
                     #(= (count (:d1 %)) (count (:d2 %))))
        :ret nat-int?
        :fn #(<= :ret (count (-> % :args :d1))))


(defn normalized-hamming [d1 d2]
  (/ (hamming d1 d2) (count d1)))
