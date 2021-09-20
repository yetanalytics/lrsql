[<- Back to Index](index.md)

# Development

The SQL LRS is a Clojure Web Application built on the Pedestal Framework.

### Build

The SQL LRS can be built or run with the following Makefile targets. They can be executed with `make [target]`.

| Target | Description |
| --- | --- |
| `ci` | Called when running continuous integration; runs all test cases. |
| `ephemeral` | Start an in-memory SQL LRS based on H2 DB. |
| `persistent` | Similar to `ephemeral`, except that the H2 DB is stored on-disk, not in-memory. |
| `sqlite` | Start a SQLite-based SQL LRS. |
| `bundle` | Build a complete distribution of the SQL LRS including the user interface and native runtimes for multiple operating systems. |

[<- Back to Index](index.md)
