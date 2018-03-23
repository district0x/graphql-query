(ns graphql-query.spec
  (:require #?(:clj [clojure.spec.alpha :as s]
               :cljs [cljs.spec.alpha :as s])
                    [graphql-query.exception :as ex]
                    [clojure.set :as c-set]
                    [clojure.string :as c-string]))

(defn namespaced-fragment-kw?
  [x]
  (and (keyword? x)
       (= "fragment" (namespace x))))

(defn- fragment-keyword?
  "Checks if keyword has :fragment namespace"
  [x]
  (if (namespaced-fragment-kw? x)
    x
    ::s/invalid))

(defn- or-conformer
  "Conforms x and returns only conformed value without value type."
  [x spec]
  (second (s/conform spec x)))

(defn- extract-fragments-name [query]
  (let [fields (or (get-in query [:query/data :fields]) (:fields query))]
    (if (keyword? fields)
      (name fields)
      nil)))

(defn- resolve-used-fragments
  [x]
  (->> x
    :queries
    (map #(-> %
            second
            extract-fragments-name))
    (remove nil?)
    set))

(defn- valid-fragments
  "Checks that all fragments used in queries are actually defined."
  [x]
  (if-not (:fragments x)
    (let [used-fragments (resolve-used-fragments x)]
      (if-not (empty? used-fragments)
        (ex/throw-ex {:graphql-query/ex-type :graphql-query/invalid-fragments
                      :graphql-query/ex-data used-fragments})
        x))

    (let [fragment-names (->> x
                           :fragments
                           (map (comp name :fragment/name))
                           set)
          used-fragments (resolve-used-fragments x)
          undefined-fragments (c-set/difference used-fragments fragment-names)]
      (if (empty? undefined-fragments)
        x
        (ex/throw-ex {:graphql-query/ex-type :graphql-query/invalid-fragments
                      :graphql-query/ex-data undefined-fragments})))))

(defn- extract-variables [query]
  (let [args (or (get-in query [:query/data :args]) (:args query))]
    (->> args
      vals
      (filter #(and (keyword? %)
                    (c-string/index-of (name %) "$")))
      (map #(c-string/replace (name %) "$" "")))))

(defn- resolve-used-variables
  [x]
  (->> x
    :queries
    (map #(-> %
            second
            extract-variables))
    flatten
    (remove nil?)
    set))

(defn- valid-variables
  "Checks that all variables used in queries are actually defined."
  [x]
  (if-not (:variables x)
    (let [used-variables (resolve-used-variables x)]
      (if-not (empty? used-variables)
        (ex/throw-ex {:graphql-query/ex-type :graphql-query/invalid-variables
                      :graphql-query/ex-data used-variables})
        x))

    (let [variables-names (->> x
                            :variables
                            (map #(c-string/replace (name (:variable/name %)) "$" ""))
                            set)
          used-variables (resolve-used-variables x)
          undefined-variables (c-set/difference used-variables variables-names)]
      (if (empty? undefined-variables)
        x
        (ex/throw-ex {:graphql-query/ex-type :graphql-query/invalid-variables
                      :graphql-query/ex-data undefined-variables})))))

(def meta-fields #{:meta/typename})

(s/def :graphql-query/query-name keyword?)
(s/def :graphql-query/fields
  (s/conformer
    #(or-conformer %
                   (s/or
                     :fields
                     (s/coll-of (s/or :graphql-query/meta-field meta-fields
                                      :fragments (s/coll-of namespaced-fragment-kw?)
                                      :graphql-query/nested-field-with-fragments (s/cat :graphql-query/nested-field-root keyword?
                                                                                        :fragments (s/coll-of namespaced-fragment-kw?))
                                      :graphql-query/field keyword?

                                      :graphql-query/field-with-args (s/cat :graphql-query/field keyword?
                                                                            :args :graphql-query/args)

                                      :graphql-query/field-with-data (s/keys :req [:field/data]
                                                                             :opt [:field/alias])

                                      :graphql-query/nested-field-arg-only (s/cat :graphql-query/nested-field-root keyword?
                                                                                  :args :graphql-query/args)
                                      :graphql-query/nested-field (s/cat :graphql-query/nested-field-root keyword?
                                                                         :args (s/? :graphql-query/args)
                                                                         :graphql-query/nested-field-children :graphql-query/fields)))

                     :fragment fragment-keyword?))))

(s/def :graphql-query/args (s/keys :opt []))
(s/def :query/data (s/cat :query :graphql-query/query-name :args (s/? :graphql-query/args) :fields (s/? :graphql-query/fields)))
(s/def :graphql-query/query (s/or :query/data :query/data
                                  :graphql-query/query-with-data (s/keys :req [:query/data]
                                                                         :opt [:query/alias])))
(s/def :query/alias keyword?)

(s/def :field/data :graphql-query/fields)
(s/def :field/alias :query/alias)

(s/def :fragment/name (fn [name]
                        (and (keyword? name)
                             (= (namespace name) "fragment"))))

(s/def :fragment/type keyword?)
(s/def :fragment/fields :graphql-query/fields)
(s/def :graphql-query/fragment (s/keys :req [:fragment/name :fragment/type :fragment/fields]))
(s/def :graphql-query/fragments (s/coll-of :graphql-query/fragment :min-count 1))

(s/def :operation/type #{:query :mutation :subscription})
(s/def :operation/name #(or (string? %) (keyword? %)))
(s/def :graphql-query/operation (s/keys :req [:operation/type :operation/name]))

(s/def :variable/name (fn [x]
                        (and (keyword? x)
                             (= (str (first (name x))) "$"))))

(s/def :variable/type keyword?)
(s/def :query/variable (s/keys :req [:variable/name :variable/type]
                               :opt [:variable/default]))
(s/def :graphql-query/variables (s/coll-of :query/variable :min-count 1))

(s/def :graphql-query/queries (s/coll-of :graphql-query/query :min-count 1))


(s/def :graphql-query/valid-fragments (s/conformer valid-fragments))
(s/def :graphql-query/valid-variables (s/conformer valid-variables))

(s/def :graphql-query/query-def (s/and (s/keys :req-un [:graphql-query/queries]
                                               :opt-un [:graphql-query/fragments
                                                        :graphql-query/operation
                                                        :graphql-query/variables])
                                       :graphql-query/valid-fragments
                                       :graphql-query/valid-variables))

(defn query->spec [query]
  (let [conformed (s/conform :graphql-query/query-def query)]
    (if (= ::s/invalid conformed)
      (ex/throw-ex {:graphql-query/ex-type :graphql-query/spec-validation
                    :graphql-query/ex-explain (s/explain :graphql-query/query-def query)})
      [:graphql-query/query-def conformed])))
