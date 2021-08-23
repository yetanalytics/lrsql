# *** Development ***

.phony: clean-dev, ci, ephemeral, persistent, bench

clean-dev:
	rm -f *.db *.log

ci:
	clojure -X:test

ephemeral:
	LRSQL_DB_NAME=ephemeral \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent false

persistent:
	LRSQL_DB_NAME=persistent \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent true

sqlite:
	LRSQL_DB_NAME=db.sqlite \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-sqlite -m lrsql.sqlite.main

# TODO: Postgres

# Intended for use with `make ephemeral` or `make persistent`
bench:
	clojure -M:bench -m lrsql.bench http://localhost:8080/xapi/statements \
		-i dev-resources/default/insert_input.json \
		-q dev-resources/default/query_input.json \
		-u username -p password

# *** Build ***

.phony: clean, bundle, executable, bundle-exe

clean:
	rm -rf target

# Compile and make Uberjar

target/bundle/lrsql.jar:
	clojure -X:build uber

# Copy scripts

target/bundle/bin:
	mkdir -p target/bundle
	cp -r bin target/bundle/bin
	chmod +x target/bundle/bin/*.sh

# Create HTML docs (TODO: make this a .zip file)

target/bundle/doc:
	clojure -M:doc -m lrsql.render-doc doc target/bundle/doc

# Copy config

target/bundle/config/lrsql.json.example:
	mkdir -p target/bundle/config
	cp resources/lrsql/config/lrsql.json.example target/bundle/config/lrsql.json.example

target/bundle/config/authority.json.template.example:
	mkdir -p target/bundle/config
	cp resources/lrsql/config/authority.json.template target/bundle/config/authority.json.template.example

target/bundle/config: target/bundle/config/lrsql.json.example target/bundle/config/authority.json.template.example

# Create entire bundle

target/bundle: target/bundle/config target/bundle/doc target/bundle/bin target/bundle/lrsql.jar

bundle: target/bundle

# Create launch4j executables
# https://stackoverflow.com/questions/5618615/check-if-a-program-exists-from-a-makefile

target/bundle/lrsql.exe: target/bundle
ifeq (,$(shell which launch4j))
	$(error "ERROR: launch4j is not installed!")
else
	cp resources/lrsql/build/launch4j/config.xml target/bundle/config.xml
	cp resources/lrsql/build/launch4j/lrsql.ico target/bundle/lrsql.ico
	launch4j target/bundle/config.xml
	rm target/bundle/config.xml target/bundle/lrsql.ico
endif

target/bundle/lrsql_pg.exe: target/bundle
ifeq (,$(shell which launch4j))
	$(error "ERROR: launch4j is not installed!")
else
	cp resources/lrsql/build/launch4j/config_pg.xml target/bundle/config_pg.xml
	cp resources/lrsql/build/launch4j/lrsql.ico target/bundle/lrsql.ico
	launch4j target/bundle/config_pg.xml
	rm target/bundle/config_pg.xml target/bundle/lrsql.ico
endif

bundle-exe: target/bundle/lrsql.exe target/bundle/lrsql_pg.exe

# *** Run build ***

.phony: run-jar-h2, run-jar-sqlite, run-jar-h2-persistent, run-jar-postgres

run-jar-h2: target/bundle
	cd target/bundle; LRSQL_API_KEY_DEFAULT=username LRSQL_API_SECRET_DEFAULT=password bin/run_h2.sh

run-jar-h2-persistent: target/bundle
	cd target/bundle; LRSQL_API_KEY_DEFAULT=username LRSQL_API_SECRET_DEFAULT=password bin/run_h2_persistent.sh

run-jar-sqlite: target/bundle
	cd target/bundle; LRSQL_API_KEY_DEFAULT=username LRSQL_API_SECRET_DEFAULT=password bin/run_sqlite.sh

run-jar-postgres: target/bundle
	cd target/bundle; LRSQL_API_KEY_DEFAULT=username LRSQL_API_SECRET_DEFAULT=password bin/run_postgres.sh
