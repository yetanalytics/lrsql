# Makefile Targets

| Target | Description |
| --- | --- |
| `ci` | Called when running continuous integration; runs all test cases. |
| `keystore` | Alias for the `config/keystore.jks`, which generates a Java Keystore file with the default alias, password, and file path, as well as a self-signed certificates. This is called during CI and is not recommended for keystore generation in production. |
| `ephemeral` | Makes an in-memory H2 database with the seed API key `username` and seed API secret `password`. This can then be used during development to test/bench lrsql functionality. |
| `persistent` | Similar to `ephemeral`, except that the H2 DB is stored on-disk, not in-memory.
