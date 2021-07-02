.phony: keystore, secret, ci, ephemeral

config/keystore.jks:
	keytool -genkey -noprompt \
		-alias lrsql_keystore \
		-dname "CN=com.yetanalytics.lrsql, OU=Dev, O=Yet Analytics, L=Baltimore, S=Maryland, C=US" \
		-keyalg RSA \
		-keypass lrsql_pass \
		-storepass lrsql_pass \
		-keystore config/keystore.jks

config/jwt_secret.key:
	openssl rand 128 > config/jwt_secret.key

keystore: config/keystore.jks

secret: config/jwt_secret.key

ci: keystore secret 
	clojure -Xdb-h2:test

ephemeral:
	ENV=:dev \
        DB_TYPE=h2:mem \
        DB_NAME=ephemeral \
        API_KEY=username \
        API_SECRET=password \
        clojure -Mdb-h2 -m lrsql.main
