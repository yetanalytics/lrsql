.phony: ci, ephemeral, persistent, bench, clean, run-jar-h2, run-jar-sqlite, bundle, config, run-jar-h2-persistent, run-jar-postgres

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

sqlite:
	LRSQL_DB_NAME=db.sqlite \
		LRSQL_SEED_API_KEY=username \
		LRSQL_SEED_API_SECRET=password \
		clojure -M:db-sqlite -m lrsql.sqlite.main

# TODO: Postgres

clean-dev:
	rm -f *.db *.log

# Intended for use with `make ephemeral` or `make persistent`
bench:
	clojure -M:bench -m lrsql.bench http://localhost:8080/xapi/statements \
		-i src/bench/dev-resources/default/insert_input.json \
		-q src/bench/dev-resources/default/query_input.json \
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

# Copy config
target/bundle/config/lrsql.json.example:
	mkdir -p target/bundle/config
	cp resources/lrsql/config/lrsql.json.example target/bundle/config/lrsql.json.example

target/bundle/config/authority.json.template.example:
	mkdir -p target/bundle/config
	cp resources/lrsql/config/authority.json.template target/bundle/config/authority.json.template.example

config: target/bundle/config/lrsql.json.example target/bundle/config/authority.json.template.example

# entire bundle
target/bundle: config target/bundle/lrsql.jar target/bundle/bin

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
