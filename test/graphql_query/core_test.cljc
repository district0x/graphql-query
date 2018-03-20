(ns graphql-query.core-test
  (:require [clojure.string :as string]
            [graphql-query.core :as q]
    #?(:cljs [cljs.test :refer-macros [is are deftest testing]]
       :clj
            [clojure.test :refer :all])))

(deftest ArgumentFormatter-test
  (is (= "null" (q/arg->str nil)))
  (is (= "\"human\"" (q/arg->str "human")))
  (is (= "{id:1}" (q/arg->str {:id 1})))
  (is (= "{id:null}" (q/arg->str {:id nil})))
  (is (= "[1,2,3]" (q/arg->str [1 2 3])))
  (is (= "[1,{id:1},\"human\"]" (q/arg->str [1 {:id 1} "human"])))
  (is (= "human" (q/arg->str :human)))
  (is (= "1" (q/arg->str 1)))
  (is (= "{active:true}" (q/arg->str {:active true})))
  (let [value (hash-map :a 0 :b 1 :c 2)
        output (q/arg->str value)]
    (is (and (string/starts-with? output "{")
             (string/ends-with? output "}")))
    (is (= (set ["a:0" "b:1" "c:2"])
           (-> output
             (string/replace #"^.|.$" "")
             (string/split #",")
             (set)))))
  ;; List in cljs
  (is (= "[1,2,3]" (q/arg->str '(1 2 3))))
  ;; IndexedSeq in cljs
  (is (= "[1,2,3]" (q/arg->str (seq [1 2 3]))))
  ;; LazySeq in cljs
  (is (= "[1,2,3]" (q/arg->str (map :x [{:x 1} {:x 2} {:x 3}])))))

(deftest arguments->str-test
  (is (= "" (q/arguments->str {})))
  (is (= "id:1" (q/arguments->str {:id 1})))
  (is (= "id:null" (q/arguments->str {:id nil})))
  (is (= "id:1,type:\"human\"" (q/arguments->str {:id 1 :type "human"})))
  (is (= "id:1,vector:[1,2,3]" (q/arguments->str {:id 1 :vector [1 2 3]}))))

(deftest meta-field->str
  (is (= "__typename" (q/meta-field->str :meta/typename))))

(deftest fields->str-test
  (is (= "name" (q/fields->str [[:graphql-query/field :name]])))
  (is (= "name,address" (q/fields->str [[:graphql-query/field :name] [:graphql-query/field :address]])))
  (is (= "friends{name,email}" (q/fields->str [[:graphql-query/nested-field {:graphql-query/nested-field-root :friends
                                                                             :graphql-query/nested-field-children [[:graphql-query/field :name]
                                                                                                                   [:graphql-query/field :email]]}]]))))

(deftest variables->str-test
  (is (= "$id:Int" (q/variables->str [{:variable/name "id"
                                       :variable/type :Int}])))
  (is (= "$id:Int=2" (q/variables->str [{:variable/name "id"
                                         :variable/type :Int
                                         :variable/default 2}])))
  (is (= "$id:Int,$name:String" (q/variables->str [{:variable/name "id"
                                                    :variable/type :Int}
                                                   {:variable/name "name"
                                                    :variable/type :String}])))
  (is (= "$id:Int=1,$name:String=\"my-name\"" (q/variables->str [{:variable/name "id"
                                                                  :variable/type :Int
                                                                  :variable/default 1}
                                                                 {:variable/name "name"
                                                                  :variable/type :String
                                                                  :variable/default "my-name"}])))
  (is (= "" (q/variables->str nil)))
  (is (= "" (q/variables->str []))))

(deftest fragment->str-test
  (is (= "fragment comparisonFields on Worker{name,address,friends{name,email}}"
         (q/fragment->str {:fragment/name "comparisonFields"
                           :fragment/type :Worker
                           :fragment/fields [[:graphql-query/field :name] [:graphql-query/field :address]
                                             [:graphql-query/nested-field {:graphql-query/nested-field-root :friends
                                                                           :graphql-query/nested-field-children [[:graphql-query/field :name]

                                                                                                                 [:graphql-query/field :email]]}]]}))))

(deftest graphql-query-test
  (testing "Should be able to change name transform fn."
    (let [data {:queries [[:employee {:id 1 :active true} [:user/name :address [:friends [:name :email]]]]]
                :transform-name-fn (fn [key]
                                     (str (when (namespace key)
                                            (str (namespace key) "_"))
                                          (name key)))}
          query-str "{employee(id:1,active:true){user_name,address,friends{name,email}}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string."
    (let [data {:queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]}
          query-str "{employee(id:1,active:true){name,address,friends{name,email}}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string with __typename meta field included"
    (let [data {:queries [[:employee {:id 1 :active true} [:name :address :meta/typename [:friends [:meta/typename :name :email]]]]]}
          query-str "{employee(id:1,active:true){name,address,__typename,friends{__typename,name,email}}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string using params on nested fields that doesnt't have nested fields."
    (let [data {:queries [[:employee {:id 1 :active true} [:name :address [:boss_name {:id 1}]]]]}
          query-str "{employee(id:1,active:true){name,address,boss_name(id:1)}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string using params on nested fields."
    (let [data {:queries [[:employee {:id 1 :active true} [:name :address [:friends {:id 1} [:name :email]]]]]}
          query-str "{employee(id:1,active:true){name,address,friends(id:1){name,email}}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string using params on fields."
    (let [data {:queries [[:employee [[:name {:isEmpty false}] :address [:friends [:name [:email {:isValid true}]]]]]]}
          query-str "{employee{name(isEmpty:false),address,friends{name,email(isValid:true)}}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string using params on different nested levels of fields."
    (let [data {:queries [[:employee {:id 1 :active true} [[:name {:isEmpty false}] :address [:friends {:id 1} [:name [:email {:isValid true}]]]]]]}
          query-str "{employee(id:1,active:true){name(isEmpty:false),address,friends(id:1){name,email(isValid:true)}}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string when no args are required and no fields are specified."
    (let [data {:queries [[:getDate]]}
          query-str "{getDate}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string there are args and no fields are specified."
    (let [data {:queries [[:sayHello {:name "Tom"}]]}
          query-str "{sayHello(name:\"Tom\")}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string there are no args but fields are specified."
    (let [data {:queries [[:sayHello [:name]]]}
          query-str "{sayHello{name}}"]
      (is (= query-str (q/graphql-query data)))))

  (testing "Invalid query, should throw exception"
    (is (thrown? #?(:clj  Exception
                    :cljs js/Error) (q/graphql-query []))))

  (testing "Should create a valid graphql string with query aliases"
    (let [data {:queries [{:query/data [:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} [:name :address [:friends [:name :email]]]]
                           :query/alias :boss}]}
          query-str (str "{workhorse:employee(id:1,active:true){name,address,friends{name,email}},"
                         "boss:employee(id:2,active:true){name,address,friends{name,email}}}")]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql string with field aliases"
    (let [data {:queries [[:employee {:id 1 :active true} [:name :address
                                                           {:field/data [[:friends [:name :email]]]
                                                            :field/alias :mates}
                                                           {:field/data [[:friends [:name :email]]]
                                                            :field/alias :enemies}]]]}
          query-str (str "{employee(id:1,active:true){name,address,mates:friends{name,email},enemies:friends{name,email}}}")]
      (is (= query-str (q/graphql-query data)))))

  (testing "Should create a valid graphql query with fragment"
    (let [data {:queries [{:query/data [:employee {:id 1 :active true} :fragment/comparisonFields]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} :fragment/comparisonFields]
                           :query/alias :boss}]
                :fragments [{:fragment/name "comparisonFields"
                             :fragment/type :Worker
                             :fragment/fields [:name :address [:friends [:name :email]]]}]}
          query-str (str "{workhorse:employee(id:1,active:true){...comparisonFields},boss:employee(id:2,active:true){...comparisonFields}} "
                         "fragment comparisonFields on Worker{name,address,friends{name,email}}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql query with multiple fragments"
    (let [data {:queries [{:query/data [:employee {:id 1 :active true} :fragment/comparisonFields]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} :fragment/comparisonFields]
                           :query/alias :boss}]
                :fragments [{:fragment/name "comparisonFields"
                             :fragment/type :Worker
                             :fragment/fields [:name :address [:friends [:name :email]]]}
                            {:fragment/name "secondFragment"
                             :fragment/type :Worker
                             :fragment/fields [:name]}]}
          query-str (str "{workhorse:employee(id:1,active:true){...comparisonFields},boss:employee(id:2,active:true){...comparisonFields}} "
                         "fragment comparisonFields on Worker{name,address,friends{name,email}},"
                         "fragment secondFragment on Worker{name}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql query with multiple fragments for fields (deals with unions)"
    (let [data {:queries [{:query/data [:employee {:id 1 :active true} [[:fragment/comparisonFields :fragment/secondFragment]]]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} :fragment/comparisonFields]
                           :query/alias :boss}]
                :fragments [{:fragment/name "comparisonFields"
                             :fragment/type :Worker
                             :fragment/fields [:name :address [:friends [:name :email]]]}
                            {:fragment/name "secondFragment"
                             :fragment/type :Worker
                             :fragment/fields [:name]}]}
          query-str (str "{workhorse:employee(id:1,active:true){...comparisonFields ...secondFragment},boss:employee(id:2,active:true){...comparisonFields}} "
                         "fragment comparisonFields on Worker{name,address,friends{name,email}},"
                         "fragment secondFragment on Worker{name}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql query with nested fragments (deals with unions)"
    (let [data {:queries [{:query/data [:employee {:id 1 :active true} [[:data [:fragment/comparisonFields :fragment/secondFragment]]]]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} :fragment/comparisonFields]
                           :query/alias :boss}]
                :fragments [{:fragment/name "comparisonFields"
                             :fragment/type :Worker
                             :fragment/fields [:name :address [:friends [:name :email]]]}
                            {:fragment/name "secondFragment"
                             :fragment/type :Worker
                             :fragment/fields [:name]}]}
          query-str (str "{workhorse:employee(id:1,active:true){data{...comparisonFields ...secondFragment}},boss:employee(id:2,active:true){...comparisonFields}} "
                         "fragment comparisonFields on Worker{name,address,friends{name,email}},"
                         "fragment secondFragment on Worker{name}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql query with a fragment within a fragment field (deals with unions)"
    (let [data {:queries [{:query/data [:employee {:id 1 :active true} :fragment/comparisonFields]
                           :query/alias :workhorse}]
                :fragments [{:fragment/name "comparisonFields"
                             :fragment/type :Worker
                             :fragment/fields [:name :address [:friends [:name :email]] [:pet [:fragment/dog :fragment/cat]]]}
                            {:fragment/name "dog"
                             :fragment/type :Dog
                             :fragment/fields [:name :bark]}
                            {:fragment/name "cat"
                             :fragment/type :Cat
                             :fragment/fields [:name :purr]}]}
          query-str (str "{workhorse:employee(id:1,active:true){...comparisonFields}} "
                         "fragment comparisonFields on Worker{name,address,friends{name,email},pet{...dog ...cat}},"
                         "fragment dog on Dog{name,bark},"
                         "fragment cat on Cat{name,purr}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql query with variables"
    (let [data {:operation {:operation/type :query
                            :operation/name "employeeQuery"}
                :variables [{:variable/name "id"
                             :variable/type :Int}
                            {:variable/name "name"
                             :variable/type :String}]
                :queries [[:employee {:id :$id
                                      :active true
                                      :name :$name}
                           [:name :address [:friends [:name :email]]]]]}
          query-str (str "query employeeQuery($id:Int,$name:String){employee(id:$id,active:true,name:$name){name,address,friends{name,email}}}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql query with variables, aliases and fragments"
    (let [data {:operation {:operation/type :query
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
                             :fragment/fields [:name :address [:friends [:name :email]]]}]}
          query-str (str "query employeeQuery($id:Int,$name:String){workhorse:employee(id:$id,active:true,name:$name){...comparisonFields},"
                         "boss:employee(id:$id,active:false){...comparisonFields}} fragment comparisonFields on Worker{name,address,friends{name,email}}")
          result (q/graphql-query data)]
      (is (= query-str result))))

  (testing "Should create a valid graphql mutation"
    (let [data {:operation {:operation/type :mutation
                            :operation/name "AddProjectToEmployee"}
                :variables [{:variable/name "id"
                             :variable/type :Int!}
                            {:variable/name "project"
                             :variable/type :ProjectNameInput!}]
                :queries [[:addProject {:employeeId :$id
                                        :project :$project}
                           [:allocation :name]]]}
          query-str (str "mutation AddProjectToEmployee($id:Int!,$project:ProjectNameInput!){addProject(employeeId:$id,project:$project){allocation,name}}")
          result (q/graphql-query data)]
      (is (= query-str result)))))
