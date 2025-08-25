(ns lrsql.debug-break
  (:require [clojure.main :as m]
            [clojure.walk :as walk]))

(defmacro debug-break []
  (let [locals (keys &env)
        syms (mapv (comp symbol name) locals)
        vals (vec locals)
        ns-sym (symbol (str *ns*))]
    #_(println "locals:" locals)
    `(let [syms# '~syms
           vals# (vector ~@locals)
]
       #_(println "syms#:" syms#)
       #_(println "vals#:" vals#)
       (m/repl
        :init #(do
                 (println ~'*ns*)
                 (in-ns '~ns-sym))
        :eval (fn [form#]
                (let [params# (mapv (comp symbol gensym name) syms#)
                      mapping# (zipmap syms# params#)
                      rewritten# (walk/postwalk-replace mapping# form#)
                      fn-form# (list 'fn (vec params#) rewritten#)
                      #_#__# (println fn-form#)
                      #_#__# (println vals#)
                      fn# (eval fn-form#)]
                  (apply fn# vals#)))))))
