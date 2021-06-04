(ns lrsql.hugsql.command.util
  (:require [lrsql.hugsql.util :refer [parse-json]]))

(defn wrapped-parse-json
  "Wraps `parse-json` in a try-catch block, returning a map with :json
  or :exception which is the parse exception, wrapped in an ex-info"
  [data]
  (try {:json (parse-json data)}
       (catch Exception ex
         {:exception ex})))

(defmacro throw-invalid-table-ex
  "Throw an exception with the following error data:
     :type     ::invalid-table
     :table    <table name>
     :input    `input`
     :fn-name  `fn-name`"
  [fn-name input]
  (let [table-kw#   (:table input)
        table-name# (when table-kw# (name table-kw#))]
    `(throw
      (ex-info (format "`%s` is not supported for table type `%s`"
                       ~fn-name
                       ~table-name#)
               {:type    ::invalid-table
                :table   ~table-kw#
                :input   ~input
                :fn-name ~fn-name}))))
