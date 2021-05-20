.phony: ci, ephemeral

ci:
	clojure -Xdb-h2:test

ephemeral:
	clojure -Mdb-h2 -m lrsql.main
