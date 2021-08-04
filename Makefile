.phony: ci, ephemeral, persistent, bench, clean, run-jar-h2, run-jar-sqlite, bundle, run-jar-h2-persistent, run-jar-postgres

ci:
	clojure -X:test

ephemeral:
	LRSQL_DB_NAME=ephemeral \
		LRSQL_SEED_API_KEY=username \
		LRSQL_SEED_API_SECRET=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent false

persistent:
	LRSQL_DB_NAME=persistent \
		LRSQL_SEED_API_KEY=username \
		LRSQL_SEED_API_SECRET=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent true

# Intended for use with `make ephemeral` or `make persistent`
bench:
	clojure -M:bench -m lrsql.bench http://localhost:8080/xapi/statements \
		-i src/bench/bench_input.json \
		-q src/bench/query_input.json \
		-u username -p password

# Build
clean:
	rm -rf target

# Compile and make Uberjar
target/bundle/lrsql.jar:
	clojure -Xbuild uber

# Copy scripts
target/bundle/bin:
	mkdir -p target/bundle
	cp -r bin target/bundle/bin
	chmod +x target/bundle/bin/*.sh

# entire bundle
target/bundle: target/bundle/lrsql.jar target/bundle/bin

bundle: target/bundle

# dev build testing stuff
run-jar-h2: target/bundle
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_h2.sh

run-jar-h2-persistent: target/bundle
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_h2_persistent.sh

run-jar-sqlite: target/bundle
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_sqlite.sh

run-jar-postgres: target/bundle
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_postgres.sh
