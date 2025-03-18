# *** Admin Assets ***

# Version of LRS Admin UI to use

LRS_ADMIN_UI_VERSION ?= v0.2.2
LRS_ADMIN_UI_LOCATION ?= https://github.com/yetanalytics/lrs-admin-ui/releases/download/${LRS_ADMIN_UI_VERSION}/lrs-admin-ui.zip
LRS_ADMIN_ZIPFILE ?= lrs-admin-ui-${LRS_ADMIN_UI_VERSION}.zip

# Get the admin UI SPA release from GitHub
resources/public/admin:
	curl -L ${LRS_ADMIN_UI_LOCATION} -o ${LRS_ADMIN_ZIPFILE}
	mkdir -p resources/public/admin
	unzip ${LRS_ADMIN_ZIPFILE} -d resources/public/admin
	rm ${LRS_ADMIN_ZIPFILE}

# *** Development ***

# `clean-dev` removes all development files.
# `test-sqlite`, `test-postgres` run tests on in-mem DB instances.
# `ci` runs all tests and is called with every push to GitHub.
# `bench` runs a query benchmarking session on a lrsql instance.

# All other phony targets run lrsql instances that can be used and tested
# during development. All start up with fixed DB properties and seed creds.

.phony: clean-dev, ci, ephemeral, ephemeral-prod, sqlite, postgres, bench, bench-async, keycloak-demo, ephemeral-oidc, superset-demo, clamav-demo, test-sqlite, test-postgres, test-postgres-11, test-postgres-12, test-postgres-13, test-postgres-14, test-postgres-15

clean-dev:
	rm -rf *.db *.log resources/public tmp

# Tests

test-sqlite:
	clojure -M:test -m lrsql.test-runner --database sqlite

TEST_PG_COMMAND ?= clojure -M:test -m lrsql.test-runner --database postgres

# Without LRSQL_TEST_DB_VERSION, defaults to version 11
test-postgres:
	$(TEST_PG_COMMAND)

test-postgres-11:
	LRSQL_TEST_DB_VERSION=11 $(TEST_PG_COMMAND)

test-postgres-12:
	LRSQL_TEST_DB_VERSION=12 $(TEST_PG_COMMAND)

test-postgres-13:
	LRSQL_TEST_DB_VERSION=13 $(TEST_PG_COMMAND)

test-postgres-14:
	LRSQL_TEST_DB_VERSION=14 $(TEST_PG_COMMAND)

test-postgres-15:
	LRSQL_TEST_DB_VERSION=15 $(TEST_PG_COMMAND)

test-postgres-16:
	LRSQL_TEST_DB_VERSION=16 $(TEST_PG_COMMAND)

ci: test-sqlite test-postgres

# Dev

ephemeral: resources/public/admin
	clojure -X:db-sqlite lrsql.sqlite.main/run-test-sqlite :ephemeral? true

# like ephemeral, but takes env vars
ephemeral-prod: resources/public/admin
	clojure -M:db-sqlite -m lrsql.sqlite.main --ephemeral true

# like ephemeral, but includes OIDC config for use with `keycloak-demo`
ephemeral-oidc: resources/public/admin
	clojure -X:db-sqlite lrsql.sqlite.main/run-test-sqlite :ephemeral? true :override-profile :test-oidc

sqlite: resources/public/admin
	clojure -X:db-sqlite lrsql.sqlite.main/run-test-sqlite

postgres: resources/public/admin # Requires a running Postgres instance
	clojure -X:db-postgres lrsql.postgres.main/run-test-postgres

# Bench - requires a running lrsql instance

bench:
	clojure -M:bench -m lrsql.bench \
	    -e http://0.0.0.0:8080/xapi/statements \
		-i dev-resources/bench/insert_input.json \
		-q dev-resources/bench/query_input.json \
		-u username -p password

bench-async:
	clojure -M:bench -m lrsql.bench \
	    -e http://0.0.0.0:8080/xapi/statements \
		-i dev-resources/bench/insert_input.json \
		-q dev-resources/bench/query_input.json \
		-a true \
		-u username -p password

# Demo instance of Keycloak used for interactive development

keycloak-demo:
	cd dev-resources/keycloak_demo; docker compose up

superset-demo:
	cd dev-resources/superset_demo; docker compose up -d

clamav-demo:
	cd dev-resources/clamav; docker compose up -d

# *** Build ***

# `clean` removes all artifacts constructed during the build process.
# `clean-non-dl` is like `clean` except that it does not delete downloaded
# folders (namely `target/bundle/admin` and `target/bundle/runtimes`).
# `bundle` creates a `target/bundle` directory that contains the entire
# lrsql package, including config, docs, JARs, admin UI files, JREs,
# Windows executables, NOTICE and LICENSE

.phony: clean, clean-non-dl, bundle

clean:
	rm -rf target resources/public pom.xml

# Combo of https://superuser.com/a/1592467
# and https://unix.stackexchange.com/a/15309
clean-non-dl:
	find target/bundle -mindepth 1 -not \( -regex "^target/bundle/runtimes.*" -o -regex "^target/bundle/admin.*" \) -delete

# Compile and make Uberjar

target/bundle/lrsql.jar: resources/public/admin
	clojure -X:build uber

target/bundle/bench.jar:
	clojure -X:build uber-bench

# Copy build scripts

target/bundle/bin:
	mkdir -p target/bundle
	cp -r bin target/bundle/bin
	chmod +x target/bundle/bin/*.sh

# Create HTML docs

target/bundle/doc:
	clojure -X:doc

target/bundle/bench:
	mkdir -p target/bundle/bench
	cp -r dev-resources/bench/. target/bundle/bench

# Copy LICENSE and NOTICE

target/bundle/LICENSE:
	cp LICENSE target/bundle/LICENSE

target/bundle/NOTICE:
	cp NOTICE target/bundle/NOTICE

# Copy config

target/bundle/config/lrsql.json.example:
	mkdir -p target/bundle/config
	cp resources/lrsql/config/lrsql.json.example target/bundle/config/lrsql.json.example

target/bundle/config/authority.json.template.example:
	mkdir -p target/bundle/config
	cp resources/lrsql/config/authority.json.template target/bundle/config/authority.json.template.example

target/bundle/config: target/bundle/config/lrsql.json.example target/bundle/config/authority.json.template.example

target/bundle/customization:
	mkdir -p target/bundle/customization/lrsql/localization
	cp resources/lrsql/localization/language.json target/bundle/customization/lrsql/localization/language.json

# Make Runtime Environment (i.e. JREs)

JAVA_MODULES ?= $(shell cat .java_modules)

# Will only produce a single jre for macos/linux matching your machine
MACHINE ?= $(shell bin/machine.sh)

target/bundle/runtimes:
	mkdir -p target/bundle/runtimes
	jlink --output target/bundle/runtimes/${MACHINE}/ --add-modules ${JAVA_MODULES}

# Copy Windows EXEs

target/bundle/lrsql.exe: exe/lrsql.exe
	mkdir -p target/bundle
	cp exe/lrsql.exe target/bundle/lrsql.exe

target/bundle/lrsql_pg.exe: exe/lrsql_pg.exe
	mkdir -p target/bundle
	cp exe/lrsql_pg.exe target/bundle/lrsql_pg.exe

# Copy Admin UI

target/bundle/admin: resources/public/admin
	mkdir -p target/bundle
	cp -r resources/public/admin target/bundle/admin

# Create entire bundle

BUNDLE_RUNTIMES ?= true

ifeq ($(BUNDLE_RUNTIMES),true)
target/bundle: target/bundle/config target/bundle/doc target/bundle/bin target/bundle/lrsql.jar target/bundle/admin target/bundle/lrsql.exe target/bundle/lrsql_pg.exe target/bundle/LICENSE target/bundle/NOTICE target/bundle/customization target/bundle/bench.jar target/bundle/bench target/bundle/runtimes 
else
target/bundle: target/bundle/config target/bundle/doc target/bundle/bin target/bundle/lrsql.jar target/bundle/admin target/bundle/lrsql.exe target/bundle/lrsql_pg.exe target/bundle/LICENSE target/bundle/NOTICE target/bundle/customization target/bundle/bench.jar target/bundle/bench
endif

bundle: target/bundle

# *** Build Windows EXEs with launch4j ***

# `clean-exe` removes all pre-existing executables, so that they can be rebuilt.
# This is not done as part of the regular `clean` target because we do not want
# to rebuild the EXEs across multiple builds.

# To build a new set of EXEs to commit, perform the following:
# % make bundle # if you haven't built the new JAR yet
# % make clean-exe
# % make exe
# Note that `make bundle` also builds the EXEs automatically, and also copies
# them to `target/bundle`.

.phony: clean-exe

# Building the executables require launch4j to be pre-installed:
# https://stackoverflow.com/questions/5618615/check-if-a-program-exists-from-a-makefile

# The executables are assumed to be checked in and thus available to the bundle.
# BUT these targets can be used to re-generate them with the JAR if needed.

clean-exe:
	rm exe/*.exe

exe/lrsql.exe:
ifeq (,$(shell which launch4j))
	$(error "ERROR: launch4j is not installed!")
else
	launch4j exe/config.xml
endif

exe/lrsql_pg.exe:
ifeq (,$(shell which launch4j))
	$(error "ERROR: launch4j is not installed!")
else
	launch4j exe/config_pg.xml
endif

exe: exe/lrsql.exe exe/lrsql_pg.exe

# *** Run build ***

# These targets create a bundle containing a lrsql JAR and then runs
# the JAR to create the specific lrsql instance.

.phony: run-jar-sqlite, run-jar-sqlite-ephemeral, run-jar-postgres

run-jar-sqlite-ephemeral: target/bundle
	cd target/bundle; \
	LRSQL_ADMIN_USER_DEFAULT=username \
	LRSQL_ADMIN_PASS_DEFAULT=password \
	LRSQL_API_KEY_DEFAULT=username \
	LRSQL_API_SECRET_DEFAULT=password \
	bin/run_sqlite_ephemeral.sh

run-jar-sqlite: target/bundle
	cd target/bundle; \
	LRSQL_ADMIN_USER_DEFAULT=username \
	LRSQL_ADMIN_PASS_DEFAULT=password \
	LRSQL_API_KEY_DEFAULT=username \
	LRSQL_API_SECRET_DEFAULT=password \
	bin/run_sqlite.sh

# NOTE: Requires a running Postgres instance!
run-jar-postgres: target/bundle
	cd target/bundle; \
	LRSQL_ADMIN_USER_DEFAULT=username \
	LRSQL_ADMIN_PASS_DEFAULT=password \
	LRSQL_API_KEY_DEFAULT=username \
	LRSQL_API_SECRET_DEFAULT=password \
	bin/run_postgres.sh

# *** Report Dependency Graph to GitHub ***

## This pom.xml file is generated solely in an action to populate the GitHub
## Dependency Graph. This allows generation of an accurate SBOM.

pom.xml:
	clojure -Adb-sqlite:db-postgres -Spom

# TODO: Add a local vulnerability checker.
# Note that we removed our previous one that used nvd-clojure as that app was
# becoming too unreliable; perhaps look into clj-watson?
