(ns graphql-query.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [graphql-query.core-test]
            [graphql-query.spec-test]))

(enable-console-print!)

(doo-tests 'graphql-query.core-test
           'graphql-query.spec-test)