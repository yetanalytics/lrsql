# Makefile Targets

For conciseness, only phony targets are listed here. The only non-phony targets in the Makefile are called when `make bundle` is called.

## Development

| Target | Description |
| --- | --- |
| `ci` | Called when running continuous integration; runs all test cases. |
| `clean-dev` | Remove all `.db` and `.log` files.
| `keystore` | Alias for the `config/keystore.jks`, which generates a Java Keystore file with the default alias, password, and file path, as well as a self-signed certificates. This is called during CI and is not recommended for keystore generation in production. |
| `ephemeral` | Makes an in-memory H2 database with the seed API key `username` and seed API secret `password`. This can then be used during development to test/bench lrsql functionality. |
| `persistent` | Similar to `ephemeral`, except that the H2 DB is stored on-disk, not in-memory. |
| `sqlite` | Similar to `persistent`, except that SQLite is the underlying DBMS instead of H2. |
| `bench` | Run the in-built benchmarking utility for lrsql at `http://localhost:8080`. The insertion and query inputs are fixed. A DB must already be running (e.g. after calling the `ephemeral`, `persistent`, or `sqlite` targets) for this to work. |

## Build

| Target | Description |
| --- | --- |
| `clean` | Removes the `target` directory (i.e. all artifacts constructed during the build process). |
| `bundle` | Create the `target/bundle` directory, which will contain `bin` (for build scripts), `doc`, `config`, and `lrsql.jar` (the AOT compilation artifact). |
| `bundle-exe` | Same as `bundle` except that it also generates Windows executables in `target/bundle`. Requires [launch4j](http://launch4j.sourceforge.net/index.html) to be installed.
| `run-jar-h2` | Compile `lrsql.jar` using `bundle` and run an H2 in-mem database with `username` and `password` credentials. |
| `run-jar-h2-persistent` | Compile `lrsql.jar` using `bundle` and run an H2 persistent database with `username` and `password` credentials. |
| `run-jar-sqlite` | Compile `lrsql.jar` using `bundle` and run a SQLite database with `username` and `password` credentials. |
| `run-jar-postgres` | Compile `lrsql.jar` using `bundle` and run a PostgreSQL database with `username` and `password` credentials. Note that a PostgreSQL DB with the default name `pg_lrsql` must already exist. |

