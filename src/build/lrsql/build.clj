(ns lrsql.build
  "Build utils for LRSQL artifacts"
  (:require [clojure.tools.build.api :as b]))

;; We add the `src/db` subdirectories separately to ensure that all `src` paths
;; in the code start w/ `lrsql/` (see: the calls to `hugsql.core/def-db-fns`)
(def src-dirs
  ["src/main"
   "resources"
   "src/db/sqlite"
   "src/db/postgres"])

(def class-dir
  "target/classes/")

;; Don't ship crypto - shouldn't be included in the build path anyways,
;; but we exclude them here as extra defense.
;; On the other hand, we keep the unobfuscated OSS source code so that users
;; have easy access to it.
(def ignored-file-regexes
  ["^.*jks$"
   "^.*key$"
   "^.*pem$"])

(def uberjar-file
  "target/bundle/lrsql.jar")

(defn- create-basis []
  (b/create-basis
   {:project "deps.edn"
    :aliases [:db-sqlite :db-postgres]}))

;; We create a single JAR for all DB backends in order to minimize artifact
;; download size, since all backends share most of the app code
(defn uber
  "Create an Uberjar at `target/bundle/lrsql.jar` that can be executed to
   run the SQL LRS app, for any DB backend."
  [_]
  (let [basis (create-basis)]
    (b/copy-dir
     {:src-dirs   src-dirs
      :target-dir class-dir
      :ignores    ignored-file-regexes})
    (b/compile-clj
     {:basis     basis
      :src-dirs  src-dirs
      :class-dir class-dir})
    (b/uber
     {:basis     basis
      :class-dir class-dir
      :uber-file uberjar-file})))

;; Alternate Jar for just bencher
(def src-dirs-bench
  ["src/bench"])

(def uberjar-file-bench
  "target/bundle/bench.jar")

(defn- create-basis-bench []
  (b/create-basis
   {:project "deps.edn"
    :aliases [:bench]}))

(defn uber-bench
  "Create Benchmark uberjar."
  [_]
  (let [basis (create-basis-bench)]
    (b/copy-dir
     {:src-dirs   src-dirs-bench
      :target-dir class-dir
      :ignores    ignored-file-regexes})
    (b/compile-clj
     {:basis     basis
      :src-dirs  src-dirs-bench
      :class-dir class-dir})
    (b/uber
     {:basis     basis
      :class-dir class-dir
      :uber-file uberjar-file-bench})))