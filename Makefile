# *** Admin Assets ***

# Get and compile the admin UI SPA

lrs-admin-ui:
	git clone git@github.com:yetanalytics/lrs-admin-ui.git
	cd lrs-admin-ui; git checkout 4a990b8c6f8a218268521fd18290566ac9573292

lrs-admin-ui/target/bundle: lrs-admin-ui
	cd lrs-admin-ui; make bundle

resources/public/admin: lrs-admin-ui/target/bundle
	mkdir -p resources/public
	cp -r lrs-admin-ui/target/bundle resources/public/admin

# *** Development ***

.phony: clean-dev, ci, ephemeral, persistent, sqlite, postgres, bench

clean-dev:
	rm -rf *.db *.log resources/public

ci:
	clojure -X:test

ephemeral: resources/public/admin
	LRSQL_DB_NAME=ephemeral \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent false

persistent: resources/public/admin
	LRSQL_DB_NAME=persistent \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent true

sqlite: resources/public/admin
	LRSQL_DB_NAME=lrsql.sqlite.db \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-sqlite -m lrsql.sqlite.main

# NOTE: Requires a running PG instance!
postgres: resources/public/admin
	LRSQL_DB_NAME=lrsql_pg \
		LRSQL_DB_USER=lrsql_user \
		LRSQL_DB_PASSWORD=swordfish \
		LRSQL_DB_PROPERTIES=currentSchema=lrsql \
		LRSQL_API_KEY_DEFAULT=username \
		LRSQL_API_SECRET_DEFAULT=password \
		clojure -M:db-postgres -m lrsql.postgres.main

# Intended for use with `make ephemeral` or `make persistent`
bench:
	clojure -M:bench -m lrsql.bench http://localhost:8080/xapi/statements \
		-i dev-resources/default/insert_input.json \
		-q dev-resources/default/query_input.json \
		-u username -p password

# *** Build ***

.phony: clean, bundle, bundle-exe

clean:
	rm -rf target resources/public

# Compile and make Uberjar

target/bundle/lrsql.jar: resources/public/admin
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

# Make Runtime Environment

# TODO: instead of platform-based jlinks, we'll pull from a remote location with all runtimes

MACHINE_TYPE = $(shell target/bundle/bin/machine.sh)

target/bundle/runtimes: target/bundle/bin
	mkdir target/bundle/runtimes
	jlink --output target/bundle/runtimes/$(MACHINE_TYPE) --add-modules java.base,java.logging,java.naming,java.xml,java.sql,java.transaction.xa,java.security.sasl,java.desktop,java.management

# Copy Admin UI

target/bundle/admin: resources/public/admin
	mkdir -p target/bundle
	cp -r resources/public/admin target/bundle/admin

# Create entire bundle

target/bundle: target/bundle/config target/bundle/doc target/bundle/bin target/bundle/runtimes target/bundle/lrsql.jar target/bundle/admin

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
