(ns graphql-query.core
  (:require [graphql-query.spec :as spec]
            [clojure.string :as str])
  #?(:clj
     (:import (clojure.lang IPersistentMap Keyword IPersistentCollection))))

(def ^:dynamic *transform-name-fn* name)

(defprotocol ArgumentFormatter
  "Protocol responsible for query arguments' formatting to string.
  Has separate implementations for general data types in cljs and clj."
  (arg->str [arg]))

(defn arguments->str
  "Given a map of query arguments, formats them and concatenates to string.

  E.g. (arguments->str {:id 1 :type \"human\"}) => id:1,type:\"human\""
  [args]
  (->> (for [[k v] args]
         [(*transform-name-fn* k) ":" (arg->str v)])
    (interpose ",")
    flatten
    (apply str)))

(defn sequential->str
  "Given something that is sequential format it to be like a JSON array."
  [arg]
  (str "[" (apply str (interpose "," (map arg->str arg))) "]"))

#?(:clj (extend-protocol ArgumentFormatter
          nil
          (arg->str [arg] "null")
          String
          (arg->str [arg] (str "\"" arg "\""))
          IPersistentMap
          (arg->str [arg] (str "{" (arguments->str arg) "}"))
          IPersistentCollection
          (arg->str [arg] (str "[" (apply str (interpose "," (map arg->str arg))) "]"))
          Keyword
          (arg->str [arg] (*transform-name-fn* arg))
          Object
          (arg->str [arg] (str arg))))

#?(:cljs (extend-protocol ArgumentFormatter
           nil
           (arg->str [arg] "null")
           string
           (arg->str [arg] (str "\"" arg "\""))
           PersistentArrayMap
           (arg->str [arg] (str "{" (arguments->str arg) "}"))
           PersistentHashMap
           (arg->str [arg] (str "{" (arguments->str arg) "}"))
           PersistentVector
           (arg->str [arg] (sequential->str arg))
           IndexedSeq
           (arg->str [arg] (sequential->str arg))
           LazySeq
           (arg->str [arg] (sequential->str arg))
           List
           (arg->str [arg] (sequential->str arg))
           Keyword
           (arg->str [arg] (*transform-name-fn* arg))
           number
           (arg->str [arg] (str arg))
           object
           (arg->str [arg] (str arg))
           boolean
           (arg->str [arg] (str arg))))

(defn meta-field->str
  "Converts namespaced meta field keyword to graphql format, e.g :meta/typename -> __typename"
  [meta-field]
  (str "__" (name meta-field)))

(defn fields->str
  "Given a spec conformed vector of query fields (and possibly nested fields),
  concatenates them to string, keeping nested structures intact."
  [fields]
  (if (keyword? fields)
    (str "..." (*transform-name-fn* (name fields)))
    (->> (for [[type value] fields]
           (condp = type
             :graphql-query/meta-field (meta-field->str value)
             :graphql-query/field (*transform-name-fn* value)
             :graphql-query/field-with-args (str (*transform-name-fn* (:graphql-query/field value))
                                                 (when (:args value)
                                                   (str "(" (arguments->str (:args value)) ")")))
             :graphql-query/field-with-data (str (when-let [alias (*transform-name-fn* (:field/alias value))]
                                                   (str alias ":"))
                                                 (fields->str (:field/data value)))
             :graphql-query/nested-field (str (*transform-name-fn* (:graphql-query/nested-field-root value))
                                              (when (:args value)
                                                (str "(" (arguments->str (:args value)) ")"))
                                              "{"
                                              (fields->str (:graphql-query/nested-field-children value))
                                              "}")
             :graphql-query/nested-field-arg-only (str (*transform-name-fn* (:graphql-query/nested-field-root value))
                                                       (str "(" (arguments->str (:args value)) ")"))
             :fragments (str/join " " (map #(str "..." (*transform-name-fn* (name %))) value))
             :graphql-query/nested-field-with-fragments (str (*transform-name-fn* (:graphql-query/nested-field-root value))
                                                             "{"
                                                             (str/join " " (map #(str "..." (*transform-name-fn* (name %)))
                                                                                (:fragments value)))
                                                             "}")))
      (interpose ",")
      (apply str))))

(defn variables->str
  "Given a vector of variable maps, formats them and concatenates to string.

  E.g. (variables->str [{:variable/name \"id\" :variable/type :Int}]) => \"$id: Int\""
  [variables]
  (->> (for [{var-name :variable/name var-type :variable/type var-default :variable/default} variables]
         (str "$" var-name ":" (*transform-name-fn* var-type) (when var-default (str "=" (arg->str var-default)))))
    (interpose ",")
    (apply str)))

(defn fragment->str
  "Given a fragment map, formats it and concatenates to string,"
  [fragment]
  (let [fields (str "{" (fields->str (:fragment/fields fragment)) "}")]
    (str "fragment "
         (*transform-name-fn* (:fragment/name fragment))
         " on "
         (*transform-name-fn* (:fragment/type fragment))
         fields)))

(defn include-fields?
  "Include fields if fields is not empty or is a keyword.
   fields could be nil or empty for operations that return a scalar."
  [fields]
  (or (keyword? fields)
      (not (empty? fields))))

(defmulti ->query-str
          (fn [query]
            (cond (vector? query) (first query)
                  (:graphql-query/query query) :graphql-query/query
                  (:graphql-query/query-with-data query) :graphql-query/query-with-data
                  :else :default)))

(defmethod ->query-str :graphql-query/query-vector
  [[_ query]]
  "Given a spec conformed query vector, creates query string with query, arguments and fields."
  (str "{"
       (->> (map ->query-str query)
         (interpose ",")
         (apply str))
       "}"))

(defmethod ->query-str :graphql-query/query-def
  [[_ query]]
  "Given a spec conformed root query map, creates a complete query string."
  (let [operation (:operation query)
        operation-with-name (when operation (str (*transform-name-fn* (:operation/type operation)) " " (:operation/name operation)))
        variables (:variables query)
        variables-str (when variables (str "(" (variables->str variables) ")"))
        fragments (:fragments query)
        fragments-str (when fragments (str " " (->> (map fragment->str fragments)
                                                 (interpose ",")
                                                 (apply str))))]
    (str operation-with-name
         variables-str
         "{"
         (->> (map ->query-str (:queries query))
           (interpose ",")
           (apply str))
         "}"
         fragments-str)))

(defmethod ->query-str :graphql-query/query
  [query]
  "Processes a single query."
  (let [query-def (:graphql-query/query query)
        alias (when (:query/alias query) (str (*transform-name-fn* (:query/alias query)) ":"))
        query-str (*transform-name-fn* (:query query-def))
        args (when (:args query-def) (str "(" (arguments->str (:args query-def)) ")"))
        fields (str "{" (fields->str (:fields query-def)) "}")]
    (str alias query-str args fields)))

(defmethod ->query-str :queries
  [[_ query]]
  (str "{"
       (->> (map ->query-str query)
         (interpose ",")
         (apply str))
       "}"))

(defmethod ->query-str :graphql-query/query-with-data
  [[_ query]]
  (let [query-str (->query-str (:query/data query))
        alias (when (:query/alias query) (str (*transform-name-fn* (:query/alias query)) ":"))]
    (str alias query-str)))

(defmethod ->query-str :query/data
  [[_ query]]
  "Processes simple query."
  (let [query-str (*transform-name-fn* (:query query))
        args (when (:args query) (str "(" (arguments->str (:args query)) ")"))
        fields (when (include-fields? (:fields query)) (str "{" (fields->str (:fields query)) "}"))]
    (str query-str args fields)))

(defmethod ->query-str :default
  [query]
  "Processes a query map (with query name, args and fields)"
  (let [query-str (*transform-name-fn* (:query query))
        args (when (:args query) (str "(" (arguments->str (:args query)) ")"))
        fields (when (include-fields? (:fields query)) (str "{" (fields->str (:fields query)) "}"))]
    (str query-str args fields)))

(defn graphql-query
  "Formats clojure data structure to valid graphql query string."
  [data]
  (binding [*transform-name-fn* (or (:transform-name-fn data) *transform-name-fn*)]
    (-> (spec/query->spec data)
      ->query-str)))


(comment
  (defn custom-name [key]
    (str (when (namespace key)
           (str (namespace key) "_"))
         (name key)))

  (set! graphql-query.core/*transform-name-fn* custom-name)

  (v/graphql-query {:queries [[:employee [:user/name :user/address]]]
                    :transform-name-fn custom-name})


(graphql-query {:queries [[:employee {:id 1 :active true}
                           [:name :address
                            {:field/data [[:friends [:name :email]]]
                             :field/alias :mates}
                            {:field/data [[:friends [:name :email]]]
                             :field/alias :enemies}]]]})

  )