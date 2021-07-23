.phony: keystore, ci, ephemeral, persistent, clean, run-jar-h2, run-jar-sqlite

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

clean:
	rm -rf target

target/bundle/lrsql.jar:
	clojure -Xbuild uber

# copy dev keystore in to try build
target/bundle/config/keystore.jks: keystore
	mkdir -p target/bundle/config
	cp config/keystore.jks target/bundle/config/keystore.jks

run-jar-h2: target/bundle/lrsql.jar target/bundle/config/keystore.jks
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password java -cp lrsql.jar clojure.main -m lrsql.h2.main

run-jar-sqlite: target/bundle/lrsql.jar target/bundle/config/keystore.jks
	cd target/bundle; LRSQL_SEED_API_KEY=username LRSQL_SEED_API_SECRET=password java -cp lrsql.jar clojure.main -m lrsql.sqlite.main
