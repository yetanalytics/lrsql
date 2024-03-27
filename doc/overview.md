[<- Back to Index](index.md)

# SQL LRS Overview

SQL LRS is a Learning Record Store (LRS) application, which is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in Part 3 of the [xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md). SQL LRS is distinct from other LRSs developed at Yet Analytics for being SQL-based and supporting multiple SQL database management systems (DBMSs) like SQLite, and Postgres.

Features include:
- A user interface that features API credential management, admin account management, statement browsing and management, and LRS monitoring
- A [Docker image](docker.md)
- Custom [Statement authority configuration](authority.md)
- Authentication via [OpenID](oidc.md)
- Support for BI platforms like [Apache Superset](superset.md)
- [Reactions](reactions.md)

### How to use SQL LRS?

SQL LRS admin accounts can be created using the user interface (see [Getting Started](startup.md)) or API calls (see [Endpoints](endpoints.md)). Accounts can be used to create or access SQL LRS credentials. These credentials, which consist of an API key, a secret API key, and their scopes ([described in the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#42-oauth-10-authorization-scope)), are then used as headers for LRS-specific methods to authenticate and authorize the request sender.

[<- Back to Index](index.md)
