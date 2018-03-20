(ns graphql-query.spec-test
  (:require [graphql-query.spec :as qs]
    #?(:cljs [cljs.test :refer-macros [is are deftest testing]]
       :clj
            [clojure.test :refer :all])))

(deftest query->spec-simple-query
  (testing "Wrong data type - vector instead of map, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec []))))
  (testing "Correct data type, but map is empty, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {}))))
  (testing ":graphql-query/queries is missing, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:operation {:operation/name "operation" :operation/type :query}}))))
  (testing ":graphql-query/queries has wrong type - map instead of vector, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries {}}))))
  (testing ":graphql-query/queries is empty, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries []}))))

  (testing "Query vector has only query name, should return conformed data."
    (is (= [:graphql-query/query-def {:queries [[:query/data {:query :queryName}]]}]
           (qs/query->spec {:queries [[:queryName]]}))))

  (testing "Query vector has only fields, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec (qs/query->spec {:queries [[[:x :y]]]})))))
  (testing "Query vector has non-keyword query name, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec (qs/query->spec {:queries [["queryName" {:id 1} [:x :y]]]})))))
  (testing "Query vector has query name and args, but no fields. Should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec (qs/query->spec {:queries [[:queryName {:id 1}]]})))))
  (testing "Query vector has args in wrong format, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries [[:queryName [:id 1] [:x :y]]]}))))
  (testing "Query vector has fields in wrong format, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries [[[:queryName {:id 1} {:x :y}]]]}))))
  (testing "Valid vector with single query, should return conformed data"
    (is (= [:graphql-query/query-def {:queries [[:query/data {:query :employee
                                                              :args {:id 1 :active true}
                                                              :fields [[:graphql-query/field :name] [:graphql-query/field :address]
                                                                       [:graphql-query/nested-field {:graphql-query/nested-field-root :friends
                                                                                                     :graphql-query/nested-field-children [[:graphql-query/field :name]
                                                                                                                                           [:graphql-query/field :email]]}]]}]]}]
           (qs/query->spec {:queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))
  (testing "Valid vector with single query and nested fragment, should return conformed data"
    (is (= [:graphql-query/query-def {:queries [[:query/data {:query :employee
                                                              :args {:id 1 :active true}
                                                              :fields [[:graphql-query/field :name] [:graphql-query/field :address]
                                                                       [:graphql-query/nested-field {:graphql-query/nested-field-root :friends
                                                                                                     :graphql-query/nested-field-children [[:graphql-query/field :name]
                                                                                                                                           [:graphql-query/field :email]]}]
                                                                       [:graphql-query/nested-field-with-fragments {:graphql-query/nested-field-root :pet
                                                                                                                    :fragments [:fragment/cat :fragment/dog]}]]}]]}]
           (qs/query->spec {:queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]] [:pet [:fragment/cat :fragment/dog]]]]]}))))
  (testing "Valid vector with single query and top level fragments, should return conformed data"
    (is (= [:graphql-query/query-def {:queries [[:query/data {:query :employee
                                                              :args {:id 1 :active true}
                                                              :fields [[:fragments [:fragment/cat :fragment/dog]]]}]]}]
           (qs/query->spec {:queries [[:employee {:id 1 :active true} [[:fragment/cat :fragment/dog]]]]}))))
  (testing ":graphql-query/operation is missing name, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:operation {:operation/type :query}
                                                     :queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))
  (testing ":graphql-query/operation is missing type, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:operation {:operation/name "name"}
                                                     :queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))
  (testing ":graphql-query/operation has type, which is not supported, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:operation {:operation/type :mutation}
                                                     :queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))

  (testing ":graphql-query/variables is empty, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:variables []
                                                     :queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))
  (testing ":graphql-query/variables is missing name, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:variables [{:variable/type :query}]
                                                     :queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))
  (testing ":graphql-query/variables is missing type, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:variables [{:variable/name "name"}]
                                                     :queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}))))
  (testing "Invalid fragment is used in query definition, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries [[:employee {:id 1 :active true} :fragment/invalid]]
                                                     :fragments [{:fragment/name "comparisonFields"
                                                                  :fragment/type :Worker
                                                                  :fragment/fields [[:graphql-query/field :name] [:graphql-query/field :address]
                                                                                    [:graphql-query/nested-field {:graphql-query/nested-field-root :friends
                                                                                                                  :graphql-query/nested-field-children [[:graphql-query/field :name]
                                                                                                                                                        [:graphql-query/field :email]]}]]}]}))))
  (testing "Undefined fragments are used in query definition, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries [[:employee {:id 1 :active true} :fragment/undefined]]}))))
  (testing "Invalid variable is used in query definition, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries [[:employee {:id 1 :active :$invalid} [:name]]]
                                                     :variables [{:variable/name "valid"
                                                                  :variable/type :Int}]}))))
  (testing "Undefined variables are used in query definition, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (qs/query->spec {:queries [[:employee {:id 1 :active :$undefined} [:name]]]}))))
  (testing "Valid vector with all possible data, should return conformed data"
    (is (= [:graphql-query/query-def {:operation {:operation/type :query
                                                  :operation/name "employeeQuery"}
                                      :variables [{:variable/name "id"
                                                   :variable/type :Int}
                                                  {:variable/name "name"
                                                   :variable/type :String}]
                                      :fragments [{:fragment/name "comparisonFields"
                                                   :fragment/type :Worker
                                                   :fragment/fields [[:graphql-query/field :name] [:graphql-query/field :address]
                                                                     [:graphql-query/nested-field {:graphql-query/nested-field-root :friends
                                                                                                   :graphql-query/nested-field-children [[:graphql-query/field :name]
                                                                                                                                         [:graphql-query/field :email]]}]]}]
                                      :queries [[:graphql-query/query-with-data {:query/data {:query :employee
                                                                                              :args {:id :$id
                                                                                                     :active true
                                                                                                     :name :$name}
                                                                                              :fields :fragment/comparisonFields}
                                                                                 :query/alias :workhorse}]
                                                [:graphql-query/query-with-data {:query/data {:query :employee
                                                                                              :args {:id :$id
                                                                                                     :active false}
                                                                                              :fields :fragment/comparisonFields}
                                                                                 :query/alias :boss}]]}]
           (qs/query->spec {:operation {:operation/type :query
                                        :operation/name "employeeQuery"}
                            :variables [{:variable/name "id"
                                         :variable/type :Int}
                                        {:variable/name "name"
                                         :variable/type :String}]
                            :queries [{:query/data [:employee {:id :$id
                                                               :active true
                                                               :name :$name}
                                                    :fragment/comparisonFields]
                                       :query/alias :workhorse}
                                      {:query/data [:employee {:id :$id
                                                               :active false}
                                                    :fragment/comparisonFields]
                                       :query/alias :boss}]
                            :fragments [{:fragment/name "comparisonFields"
                                         :fragment/type :Worker
                                         :fragment/fields [:name :address [:friends [:name :email]]]}]})))))
