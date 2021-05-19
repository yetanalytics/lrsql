.phony: ci

ci:
	clojure -Xdb-h2:test
