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
		clojure -M:db-h2 -m lrsql.h2.main -p false

persistent: keystore
	LRSQL_DB_NAME=persistent \
		LRSQL_SEED_API_KEY=username \
		LRSQL_SEED_API_SECRET=password \
		clojure -M:db-h2 -m lrsql.h2.main -p true
