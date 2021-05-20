.phony: ci, ephemeral, nvm

nvm:
	curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.38.0/install.sh | bash
	nvm use 12

ci: nvm
	clojure -Xdb-h2:test

ephemeral:
	ENV=:dev \
        DB_TYPE=h2:mem \
        DB_NAME=ephemeral \
        clojure -Mdb-h2 -m lrsql.main
