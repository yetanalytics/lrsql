(ns lrsql.ops.util)

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
