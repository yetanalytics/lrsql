.phony: keystore, ci, ephemeral, persistent, clean, run-jar-h2, run-jar-sqlite, bundle

config/keystore.jks:
	keytool -genkey -noprompt \
		-alias lrsql_keystore \
		-dname "CN=com.yetanalytics.lrsql, OU=Dev, O=Yet Analytics, L=Baltimore, S=Maryland, C=US" \
		-keyalg RSA \
		-keypass lrsql_pass \
		-storepass lrsql_pass \
		-keystore config/keystore.jks

keystore: config/keystore.jks

ci: keystore
	clojure -X:test

ephemeral: keystore
	LRSQL_DB_NAME=ephemeral \
		LRSQL_SEED_API_KEY=username \
		LRSQL_SEED_API_SECRET=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent false

persistent: keystore
	LRSQL_DB_NAME=persistent \
		LRSQL_SEED_API_KEY=username \
		LRSQL_SEED_API_SECRET=password \
		clojure -M:db-h2 -m lrsql.h2.main --persistent true

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
# copy dev keystore in to try build
target/bundle/config/keystore.jks: keystore
	mkdir -p target/bundle/config
	cp config/keystore.jks target/bundle/config/keystore.jks

run-jar-h2: target/bundle target/bundle/config/keystore.jks
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_h2.sh

run-jar-h2-persistent: target/bundle target/bundle/config/keystore.jks
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_h2_persistent.sh

run-jar-sqlite: target/bundle target/bundle/config/keystore.jks
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_sqlite.sh

run-jar-postgres: target/bundle target/bundle/config/keystore.jks
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password bin/run_postgres.sh
