[<- Back to Index](index.md)

# Development

The SQL LRS is a Clojure Web Application built on the Pedestal Framework.

## Build

The SQL LRS can be built or run with the following Makefile targets. They can be executed with `make [target]`.

#### Build and Test Targets

| Target | Description |
| --- | --- |
| `ci` | Called when running continuous integration; runs all test cases in all SQL flavors. |
| `test-h2` | Run all tests with H2 database. |
| `test-sqlite` | Run all tests with SQLite database. |
| `test-postgres` | Run all tests with Postgres database. |
| `bundle` | Build a complete distribution of the SQL LRS including the user interface and native runtimes for multiple operating systems. |
| `bench` | Run a load test and benchmark performance, returning performance metrics on predefined test data. Requires a running SQL LRS instance to test against. This test sends requests synchronously one one thread. |
| `bench-async` | Same as `bench` but it runs with concurrent requests on multiple threads. |

#### Run Targets

| Target | Description |
| --- | --- |
| `ephemeral` | Start an in-memory SQL LRS based on H2 DB. |
| `persistent` | Similar to `ephemeral`, except that the H2 DB is stored on-disk, not in-memory. |
| `sqlite` | Start a SQLite-based SQL LRS. |
| `postgres` | Start a Postgres SQL LRS. Requires a running Postgres instance. |
| `run-jar-h2` | Similar to `ephemeral` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables. |
| `run-jar-sqlite` | Similar to `sqlite` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables. |
| `run-jar-h2-persistent` | Similar to `persistent` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables. |
| `run-jar-postgres` | Similar to `postgres` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables. |

#### Cleanup Targets

| Target | Description |
| --- | --- |
| `clean` | Removes all build artifacts. |
| `clean-dev` | Removes development files. |
| `clean-exe` | Removes the finished Windows executables, allowing recreation. |

[<- Back to Index](index.md)
