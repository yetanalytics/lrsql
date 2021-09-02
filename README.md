# lrsql

_lrsql (LER-skwəl) - The Learning Record Structured Query Language_

A SQL-based Learning Record Store.

## Overview

A Learning Record Store (LRS) is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in [Part 3 of the xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#partthree). lrsql is distinct from other LRSs developed at Yet Analytics for being SQL-based and supporting multiple SQL database management systems (DBMSs) like H2, SQLite, and PostgreSQL.

Currently, lrsql is installed by pulling the latest commit from its GitHub repo. In the future it will be available as open-source software.

To use lrsql, a user needs to be authorized by an admin. Admin accounts can be created using special RESTful HTTP methods (described later in the README); logging into them will return a JSON Web Token (JWT), a temporary token that can then be used to create or access lrsql credentials. These credentials, which consist of a public API key (the "username"), a secret API key (the "password"), and their scopes ([described in the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#42-oauth-10-authorization-scope)), are then used as headers for LRS-specific methods to authenticate and authorize the request sender.

## Index

- [Environment Variables](doc/env_vars.md) - How to configure lrsql?
- [Makefile Targets](doc/makefile.md) - Using the Makefile during development
- [HTTP Endpoints](doc/endpoints.md) - How to use the lrsql API?
- [TLS/HTTPS](doc/https.md) - How to set up a secure connection with lrsql?

## License

Copyright © 2021 Yet Analytics

Distributed under the Apache License version 2.0.
