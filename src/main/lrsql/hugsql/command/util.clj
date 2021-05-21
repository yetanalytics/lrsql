(ns lrsql.hugsql.command.util
  (:require [lrsql.hugsql.util :refer [parse-json]]))

(defmacro wrapped-parse-json
  "Wraps `parse-json` in a try-catch block, throwing ExceptionInfo containing
   the description `data-type` on failure."
  [data-type data]
  `(try (parse-json ~data)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse %s as JSON!" ~data-type)
                          {:kind ::non-json-document
                           :type ~data-type
                           :data ~data})))))

(defmacro throw-invalid-table-ex
  "Throw an exception with the following error data:
     :kind     ::invalid-table
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
               {:kind    ::invalid-table
                :table   ~table-kw#
                :input   ~input
                :fn-name ~fn-name}))))
