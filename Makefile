.phony: ci, ephemeral

ci:
	clojure -Xdb-h2:test

ephemeral:
	ENV=:dev \
        DB_TYPE=h2:mem \
        DB_NAME=ephemeral \
        DB_SCHEMA=lrsql \
        clojure -Mdb-h2 -m lrsql.main
