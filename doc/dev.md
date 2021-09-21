[<- Back to Index](index.md)

# Development

The SQL LRS is a Clojure Web Application built on the Pedestal Framework.

### Build

The SQL LRS can be built or run with the following Makefile targets. They can be executed with `make [target]`.

#### Build and Test Targets

| Target | Description |
| --- | --- |
| `ci` | Called when running continuous integration; runs all test cases in all SQL flavors. |
| `test-h2` | Run all tests with H2 database. |
| `test-sqlite` | Run all tests with SQLite database. |
| `test-postgres` | Run all tests with Postgres database. |
| `bundle` | Build a complete distribution of the SQL LRS including the user interface and native runtimes for multiple operating systems. |
| `bench` | Run a load test and benchmark performance, returning performance metrics on predefined test data. Requires a running SQL LRS instance to test against. This test sends requests synchronously on one thread. |
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

### Benchmarking

You can customize and run benchmarks for SQL LRS (or any conformant LRS, though the benchmarking framework was designed for SQL LRS). The benchmarking framework will insert auto-generated Statements into a running SQL LRS instance, then querying them and recording query time statistics.

#### 1. Set up insertion inputs

This step can be skipped if your DB already has Statements stored and you are only running queries.

The insertion input file is a JSON file that follows the [DATASIM](https://github.com/yetanalytics/datasim) input format. The benchmarking framework uses DATASIM to generate a Statement sequence from that input file. See the [DATASIM documentation](https://github.com/yetanalytics/datasim#usage) for more information about the proper input format.

#### 2. Set up query inputs

The query input file is a JSON file containing an array of Statement query parameter objects. The following is an example query input:
```json
[
    {},
    {"verb": "https://w3id.org/xapi/video/verbs/seeked"}
]
```

See the [Statement resource documentation](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#213-get-statements) for a full list of query parameters.

#### 3. Start an LRS instance

Start an instance of the SQL LRS (or any conformant LRS). The above Makefile targets provide a convenient way of doing so.

#### 4. Run the benchmarks

In another terminal, run the benchmarking framework by calling:
```
clojure -M:bench -m lrsql.bench [arguments]
```

The following is the full list of arguments (which can also be accessed by passing the `--help` argument):
| Argument | Default | Description |
| --- | --- | --- |
| `-e`, `--lrs-endpoint URI` | `http://localhost:8080/xapi/statements` | The HTTP(S) endpoint of the (SQL) LRS webserver for Statement POSTs and GETs. |
| `-i`, `--insert-input URI` | None | The location of a JSON file containing a DATASIM input spec. If given, this input is used to insert statements into the DB. |
| `-s`, `--input-size LONG` | `1000` | The total number of statements to insert. Ignored if `-i` is not given. |
| `-b`, `--batch-size LONG` | `10` | The batch size to use for inserting statements. Ignored if `-i` is not given. |
| `-a`, `--async? BOOLEAN` | `false` | Whether to insert asynchronously or not. |
| `-c`, `--concurrency LONG` | `10` | The number of parallel threads to run during statement insertion. Ignored if `-i` is not given or `-a` is `false`. |
| `-r`, `--statement-refs STRING` | `none` | How Statement Refs should be inserted. Valid options are `none`, `half`, and `all`. |
| `-q`, `--query-input URI` | The location of a JSON file containing an array of statement query params. If not given, the benchmark does a single query with no params. |
| `-n`, `--query-number LONG` | `30` The number of times each query is performed. |
| `-u`, `--user STRING` | None | HTTP Basic Auth user. |
| `-p`, `--pass STRING` | None | HTTP Basic Auth password. |
| `-h`, `--help` | None | Help menu. Does not take arguments. |

#### 5. Wait for results

After the bench has run, you should see results that look something like this:
```
********** Query benchmark results for n = 30 (in ms) **********

|                                              :query | :mean | :sd | :max | :min | :total |
|-----------------------------------------------------+-------+-----+------+------+--------|
|                                                  {} |    24 |  14 |   96 |   19 |    722 |
| {"verb" "https://w3id.org/xapi/video/verbs/seeked"} |    20 |   3 |   33 |   18 |    604 |
```

[<- Back to Index](index.md)
