.phony: keystore, ci, ephemeral

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
	clojure -Xdb-h2:test

ephemeral:
	ENV=:dev \
        LRSQL_DB_TYPE=h2:mem \
        LRSQL_DB_NAME=ephemeral \
        LRSQL_SEED_API_KEY=username \
        LRSQL_SEED_API_SECRET=password \
        clojure -Mdb-h2 -m lrsql.main

lrsql.jar:
	clojure -X:uberjar :jar lrsql.jar :main-class lrsql.main :aliases '[:db-h2]'
