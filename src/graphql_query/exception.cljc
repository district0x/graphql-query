(ns graphql-query.exception)

(defmulti throw-ex :graphql-query/ex-type)

#?(:clj (defmethod throw-ex :graphql-query/spec-validation
          [data]
          (throw (ex-info "Invalid query data" data))))

#?(:cljs (defmethod throw-ex :graphql-query/spec-validation
           [data]
           (throw (js/Error. (str "Invalid query data " data)))))

#?(:clj (defmethod throw-ex :graphql-query/invalid-fragments
          [data]
          (throw (ex-info (str "Invalid fragments: " (:graphql-query/ex-data data)) data))))

#?(:cljs (defmethod throw-ex :graphql-query/invalid-fragments
           [data]
           (throw (js/Error. (str "Invalid fragments: " (:graphql-query/ex-data data))))))

#?(:clj (defmethod throw-ex :graphql-query/invalid-variables
          [data]
          (throw (ex-info (str "Invalid variables: " (:graphql-query/ex-data data)) data))))

#?(:cljs (defmethod throw-ex :graphql-query/invalid-variables
           [data]
           (throw (js/Error. (str "Invalid variables: " (:graphql-query/ex-data data))))))
