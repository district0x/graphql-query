# graphql-query

[![Build Status](https://travis-ci.org/district0x/graphql-query.svg?branch=master)](https://travis-ci.org/district0x/graphql-query)

A Clojure(Script) qraphql query generation library. Generate valid graphql queries with Clojure data structures.

This library is fork of [venia](https://github.com/Vincit/venia) library with a few modifications. So big thanks
to original creator!

## Installation
Add `[district0x/graphql-query "1.0.1"]` into your project.clj  
Include `[graphql-query.core :refer [graphql-query]]` in your CLJS file

## Usage

graphql-query is originally supposed to be used in Clojurescript apps, but can be used as well in Clojure, as the core 
is written in CLJC. The sole purpose of this library is graphql query string generation from Clojure data, 
so that strings concatenations and manipulations could be avoided when using grapqhl.
It is up to developers to hook it up to frontend apps. However, at least some sort of re-frame-graphql-fx library 
is on a roadmap. 


### Simple query

The easiest way to start with graphql-query, is simple's query generation. 

```clojure
(ns my.project
  (:require [graphql-query.core :refer [graphql-query]]))

(graphql-query {:queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]]})

=> "{employee(id:1,active:true){name,address,friends{name,email}}}"
```

Obviously, If we would like to fetch employees and projects within the same simple query, we would do it this way:

```clojure
(graphql-query {:queries [[:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]
                          [:projects {:active true} [:customer :price]]]})

=> "{employee(active:true){name,address},project(active:true){customer,price}}"
```

### Field arguments

In the example above, `:employee` and `:projects` fields have arguments `{:id 1 :active true}` and `{:id 1 :active true}` 
respectively.

We can add arguments to other fields easily by wrapping field name and its arguments to vector `[:customer {:id 2}]`:

```clojure
(graphql-query {:queries [[:projects {:active true} [[:customer {:id 2}] :price]]]})

=> "{project(active:true){customer(id:2),price}}"
```

### Query with alias

Now, if we need to have an alias for query, it can be easily achieved by using graphql-query's query-with-data map

```clojure
(graphql-query {:queries [{:query/data [:employee {:id 1 :active true} [:name :address [:friends [:name :email]]]]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} [:name :address [:friends [:name :email]]]]
                           :query/alias :boss}]})
     
=> prettified:
{
  workhorse: employee(id: 1, active: true) {
    name
    address
  },
  boss: employee(id: 2, active: true) {
    name
    address
  }
}
```

In the query above, we use `:query/data` key for query definition and `:query/alias` for query's alias definition.

To use alias for nested fields, we use `:field/data` and `:field/alias`:

```clojure
(graphql-query {:queries [[:employee {:id 1 :active true}
                           [:name :address
                            {:field/data [[:friends [:name :email]]]
                             :field/alias :mates}
                            {:field/data [[:friends [:name :email]]]
                             :field/alias :enemies}]]]})
                                                            
=> prettified:                                                            
{
  employee(id:1,active:true) {
    name
    address
    mates: friends {
      name
      email
    }
    enemies: friends {
      name,
      email
    }
  }
}                                                            
```



### Query with fragments

What about fragments? Just add `:fragments` vector with fragments definitions

```clojure
(graphql-query {:queries [{:query/data [:employee {:id 1 :active true} :fragment/comparisonFields]
                           :query/alias :workhorse}
                          {:query/data [:employee {:id 2 :active true} :fragment/comparisonFields]
                           :query/alias :boss}]
                :fragments [{:fragment/name "comparisonFields"
                             :fragment/type :Worker
                             :fragment/fields [:name :address]}]})

=> prettified:
{
  workhorse: employee(id: 1, active: true) {
    ...comparisonFields
  }
  boss: employee(id: 2, active: true) {
    ...comparisonFields
  }
}

fragment comparisonFields on Worker {
  name
  address
}
```

### Query with variables

Now you can generate really complex queries with variables as well. In order to define variables, we need to define 
an operation type and name.


```clojure
(v/graphql-query {:operation {:operation/type :query
                                :operation/name "employeeQuery"}
                  :variables [{:variable/name "id"
                               :variable/type :Int
                               :variable/default 1}
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
                               :fragment/fields [:name :address [:friends [:name :email]]]}]})

=> prettified:
query employeeQuery($id: Int = 1, $name: String) {
  workhorse: employee(id: $id, active: true, name: $name) {
    ...comparisonFields
  }
  boss: employee(id: $id, active: false) {
    ...comparisonFields
  }
}

fragment comparisonFields on Worker {
  name
  address
  friends {
    name
    email
  }
}

```

### Mutation

Mutations are also supported, just use `:mutation` operation type:

```clojure

(v/graphql-query {:operation {:operation/type :mutation
                              :operation/name "AddProjectToEmployee"}
                  :variables [{:variable/name "id"
                               :variable/type :Int!}
                              {:variable/name "project"
                               :variable/type :ProjectNameInput!}]
                  :queries [[:addProject {:employeeId :$id
                                          :project :$project}
                             [:allocation :name]]]})
                                     
=> prettified:
mutation AddProjectToEmployee($id:Int!,$project:ProjectNameInput!) {
  addProject(employeeId:$id, project:$project) {
    allocation,
    name
  }
}
```

### Validation

graphql-query will verify that you don't use undefined variables or fragments. 

For example, the following `v/graphql-query` calls will throw exceptions:

```clojure

(v/graphql-query {:queries [[:employee {:id 1 :active true} :fragment/undefined]]}

(v/graphql-query {:queries [[:employee {:id 1 :active :$undefined} [:name]]]}))
```

because fragment and variable are never defined.

### Meta fields

You can use graphql's `__typename` meta field anywhere inside of your query.
For example:

```clojure
(v/graphql-query {:queries [[:employee [:meta/typename :name :address]]]})

=> prettified:

{
  employee {
    __typename,
    name,
    address
  }
}

```

### Keywords Transformation
Sometimes you may want to preserve namespaces on fields and transform them into your own graphql-friendly format.
For this purpose, this library contains: `*kw->gql-name*`. By default, this functions equals to core's `name` function.
You can change this function globally with `set!` or just for a single query by passing it as `:kw->gql-name`. 

```clojure
;; Example of simplistic custom transform function
(defn custom-name [key]
    (str (when (namespace key)
           (str (namespace key) "_"))
         (name key)))

;; Setting transform function globally
(set! graphql-query.core/*transform-name-fn* custom-name)

;; Passing transform function per query
(v/graphql-query {:queries [[:employee [:user/name :user/address]]]}
                 {:kw->gql-name custom-name})
                  
=> prettified:

{
  employee {
    user_name
    user_address
  }
}

```


## License

Forked from [venia](https://github.com/Vincit/venia)

Distributed under the Eclipse Public License, the same as Clojure.
