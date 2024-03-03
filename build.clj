(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'is.d0x/graphql-query) ; ends up as <group-id>/<artifact-id> in pom.xml
(def version "1.0.7")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn- pom-template [version]
  [[:description "The next generation of clojure.java.jdbc: a new low-level Clojure wrapper for JDBC-based access to databases."]
   [:url "https://github.com/seancorfield/next-jdbc"]
   [:licenses
    [:license
     [:name "Eclipse Public License"]
     [:url "https://www.eclipse.org/legal/epl-v10.html"]]]
   [:developers
    [:developer
     [:name "Madis Nõmme"]]]
   [:scm
    [:url "https://github.com/district0x/graphql-query"]
    [:connection "scm:git:git@github.com:district0x/graphql-query.git"]
    [:tag (str "v" version)]]])

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :pom-data (pom-template version)})

  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
