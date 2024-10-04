[<- Back to Index](index.md)

# Developer Documentation

The SQL LRS is a Clojure Web Application built on the Pedestal Framework.

### Build

The SQL LRS can be built or run with the following Makefile targets. They can be executed with `make [target]`.

#### Build and Test Targets

| Target             | Description                                                                                                                                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ci`               | Called when running continuous integration; runs all test cases in all SQL flavors.                                                                                                                          |
| `test-sqlite`      | Run all tests with SQLite database.                                                                                                                                                                          |
| `test-postgres`    | Run all tests with Postgres database version 11. Set the `LRSQL_TEST_DB_VERSION` env var to a valid Postgres docker tag to use another version.                                                              |
| `test-postgres-11` | Run all tests with Postgres database version 11.                                                                                                                                                             |
| `test-postgres-12` | Run all tests with Postgres database version 12.                                                                                                                                                             |
| `test-postgres-13` | Run all tests with Postgres database version 13.                                                                                                                                                             |
| `test-postgres-14` | Run all tests with Postgres database version 14.                                                                                                                                                             |
| `test-postgres-15` | Run all tests with Postgres database version 15.                                                                                                                                                             |
| `bundle`           | Build a complete distribution of the SQL LRS including the user interface and native runtimes for multiple operating systems.                                                                                |
| `bench`            | Run a load test and benchmark performance, returning performance metrics on predefined test data. Requires a running SQL LRS instance to test against. This test sends requests synchronously on one thread. |
| `bench-async`      | Same as `bench` but it runs with concurrent requests on multiple threads.                                                                                                                                    |
| `check-vuln`       | Run the [nvd-clojure](https://github.com/rm-hull/nvd-clojure) tool, which checks for vulnerabilities against the [National Vulnerability Database](https://nvd.nist.gov/).                                   |

#### Run Targets

| Target                     | Description                                                                                                                                |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `ephemeral`                | Start an in-memory SQL LRS based on SQLite DB.                                                                                             |
| `ephemeral-prod`           | Similar to `ephemeral`, except that the `:prod` profile is used, enabling the use of environment variables without full compilation.       |
| `sqlite`                   | Start a SQLite-based SQL LRS.                                                                                                              |
| `postgres`                 | Start a Postgres SQL LRS. Requires a running Postgres instance.                                                                            |
| `run-jar-sqlite`           | Similar to `sqlite` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables.    |
| `run-jar-sqlite-ephemeral` | Similar to `ephemeral` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables. |
| `run-jar-postgres`         | Similar to `postgres` but it runs the finished Jar instead of directly from Clojure. Runs with a predefined default set of env variables.  |

#### Cleanup Targets

| Target         | Description                                                        |
| -------------- | ------------------------------------------------------------------ |
| `clean`        | Removes all build artifacts.                                       |
| `clean-non-dl` | Same as `clean` except that it does not delete downloaded folders. |
| `clean-dev`    | Removes development files.                                         |
| `clean-exe`    | Removes the finished Windows executables, allowing recreation.     |

### Benchmarking

You can customize and run benchmarks for SQL LRS (or any conformant LRS, though the benchmarking framework was designed for SQL LRS). The benchmarking framework will insert auto-generated Statements into a running SQL LRS instance, then querying them and recording query time statistics.

#### 1. Set up insertion inputs

This step can be skipped if your DB already has Statements stored and you are only running queries.

The insertion input file is a JSON file that follows the [DATASIM](https://github.com/yetanalytics/datasim) input format. The benchmarking framework uses DATASIM to generate a Statement sequence from that input file. See the [DATASIM documentation](https://github.com/yetanalytics/datasim#usage) for more information about the proper input format.

#### 2. Set up query inputs

The query input file is a JSON file containing an array of Statement query parameter objects. The following is an example query input:

```json
[{}, { "verb": "https://w3id.org/xapi/video/verbs/seeked" }]
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
| Argument | Value | Default | Description |
| --- | --- | --- | --- |
| `-e`, `--lrs-endpoint` | URI | <details>`http://0.0.0.0:8080/xapi/statements`<summary>(URI)</summary></details> | The HTTP(S) endpoint of the (SQL) LRS webserver for Statement POSTs and GETs. |
| `-i`, `--insert-input` | Filepath | None | The location of a JSON file containing a DATASIM input spec. If present, this input is used to insert statements into the DB. |
| `-s`, `--input-size` | Integer | `1000` | The total number of statements to insert. Ignored if `-i` is not present. |
| `-b`, `--batch-size` | Integer | `10` | The batch size to use for inserting statements. Ignored if `-i` is not present. |
| `-a`, `--async` | No args | N/A | If provided, insert statements asynchronously. |
| `-c`, `--concurrency` | Integer | `10` | The number of parallel threads to run during statement insertion and querying. Ignored if `-a` is not present. |
| `-r`, `--statement-refs` | Keyword | `none` | How Statement References should be generated and inserted. Valid options are `none` (no Statement References), `half` (half of the Statements have StatementRef objects), and `all` (all Statements have StatementRef objects). |
| `-q`, `--query-input` | Filepath | None | The location of a JSON file containing an array of statement query params. If not present, the benchmark does a single query with no params. |
| `-n`, `--query-number` | Integer | `30` | The number of times each query is performed. |
| `-u`, `--user` | String | None | HTTP Basic Auth user. |
| `-p`, `--pass` | String | None | HTTP Basic Auth password. |
| `-h`, `--help` | No args | N/A | Help menu. |

#### 5. Wait for results

After the bench has run, you should see results that look something like this:

```
********** Query benchmark results for n = 30 (in ms) **********

|                                              :query | :mean | :sd | :max | :min | :total |
|-----------------------------------------------------+-------+-----+------+------+--------|
|                                                  {} |    24 |  14 |   96 |   19 |    722 |
| {"verb" "https://w3id.org/xapi/video/verbs/seeked"} |    20 |   3 |   33 |   18 |    604 |
```

#### 6. Compiled Benchmark Utility

A version of the benchmark utility is included with the release distribution bundle. The arguments are the same, running it is just slightly different:

```
java -cp bench.jar lrsql.bench [arguments]
```

Sample insert and query inputs can be found in the distribution at `bench/`

### Reaction JSON

Reactions are internally stored in SQL LRS as JSON object. Each reaction contains an `id`, `title`, `active`, `created`, `modified`, and `ruleset` property. Each `ruleset` is a JSON object that in turn contains the properties `conditions`, `template`, and `identityPaths`.

The following is the example reaction from [the tutorial](reactions.md), in JSON format:
```json
{
    "id": "019257f4-d533-8c0b-8730-28b82f383982",
    "created": "2024-10-04T14:35:16Z",
    "modified": "2024-10-04T14:35:16Z",
    "title": "reaction_example",
    "active": true,
    "ruleset": {
        "conditions": {
            "condition_alpha": {
                "and": [
                    {
                        "path": [ "object", "id" ],
                        "op": "eq",
                        "val": "https://example.com/activities/alpha"
                    },
                    {
                        "path": [ "verb", "id" ],
                        "op": "eq",
                        "val": "https://example.com/verbs/completed"
                    },
                    {
                        "path": [ "result", "success" ],
                        "op": "eq",
                        "val": true
                    }
                ]
            },
            "condition_beta": {
                "and": [
                    {
                        "path": [ "object", "id" ],
                        "op": "eq",
                        "val": "https://example.com/activities/beta"
                    },
                    {
                        "path": [ "verb", "id" ],
                        "op": "eq",
                        "val": "https://example.com/verbs/completed"
                    },
                    {
                        "path": [ "result", "success" ],
                        "op": "eq",
                        "val": true
                    },
                    {
                        "path": [ "timestamp" ],
                        "op": "gt",
                        "ref": {
                            "condition": "condition_alpha",
                            "path": [ "timestamp" ]
                        }
                    }
                ]
            }
        },
        "template": {
            "actor": {
                "mbox": {
                    "$templatePath": [ "condition_alpha", "actor", "mbox" ]
                }
            },
            "verb": {
                "id": "https://example.com/verbs/completed"
            },
            "object": {
                "id": "https://example.com/activities/alpha-and-beta",
                "objectType": "Activity"
            }
        },
        "identityPaths": [
            [ "actor", "mbox" ],
            [ "actor", "mbox_sha1sum" ],
            [ "actor", "openid" ],
            [ "actor", "account", "homePage" ],
            [ "actor", "account", "name" ]
        ]
    }
}
```

[<- Back to Index](index.md)
