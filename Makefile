.phony: keystore, ci, ephemeral, persistent

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
	LRSQL_DB_NAME=persistent
		LRSQL_SEED_API_KEY=username
		LRSQL_SEED_API_SECRET=password
		clojure -M:db-h2 -m lrsql.h2.main --persistent true

sqlite: keystore
	LRSQL_DB_NAME=sqlite \
                LRSQL_SEED_API_KEY=username \
                LRSQL_SEED_API_SECRET=password \
                clojure -M:db-sqlite -m lrsql.sqlite.main

pg: keystore
	LRSQL_DB_TYPE=postgres \
	LRSQL_DB_NAME=lrsql1 \
	LRSQL_DB_USER=postgres \
	LRSQL_DB_PASSWORD=cod3dre@ms \
	LRSQL_DB_PORT=5432 \
	LRSQL_SEED_API_KEY=username \
	LRSQL_SEED_API_SECRET=password \
	clojure -M:db-postgres -m lrsql.postgres.main
