[<- Back to Index](index.md)

# SQL LRS Overview

### What is SQL LRS?

A Learning Record Store (LRS) is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in Part 3 of the [xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md). SQL LRS is distinct from other LRSs developed at Yet Analytics for being SQL-based and supporting multiple SQL database management systems (DBMSs) like SQLite, and Postgres.

In addition, SQL LRS includes several additional features, such as:
- A UI that features API key and admin management, statement browsing, and much more
- A [Docker image](docker.md)
- Custom [authority configuration](authority.md)
- Authentication via [OpenID](oidc.md)
- Support for BI platforms like [Apache Superset](superset.md)
- [Reactions](reactions.md)

### What is SQL LRS not?

SQL LRS, as an LRS, is purely a database storage app for xAPI statements, documents, and other related data. It is _not_ a Learning Management System (LMS) that generates xAPI statements from learning data, nor is it a combined platform that includes an LMS.

In addition, there are several features that SQL LRS does not support (but were supported by Yet's past Cloud LRS products), such as:
- Tenancy (the entire database can be considered to be a single default tenant)
- Async operations (all operations in SQL LRS are synchronous)

### How to use SQL LRS?

SQL LRS Accounts can be created using the user interface (see [Getting Started](startup.md)) or API calls (see [Endpoints](endpoints.md)). Accounts can be used to create or access SQL LRS credentials. These credentials, which consist of an API key, a secret API key, and their scopes ([described in the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#42-oauth-10-authorization-scope)), are then used as headers for LRS-specific methods to authenticate and authorize the request sender.

[<- Back to Index](index.md)
