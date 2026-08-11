(ns lrsql.backend.result)

(defn affected->applied?
  "Normalize the affected-row result of a single-row CAS command to a boolean.
   Throws if the backend reports more than one affected row, since that would
   violate the logical document-key invariant."
  [result]
  (let [affected (if (map? result)
                   (:next.jdbc/update-count result)
                   result)]
    (case affected
      0 false
      1 true
      (throw (ex-info "Unexpected affected-row count for document CAS"
                      {:type     ::unexpected-affected-count
                       :affected affected})))))
