[<- Back to Index](index.md)

# Overview

## What is SQL LRS?

A Learning Record Store (LRS) is a persistent store for xAPI statements and associated attachments and documents. The full LRS specification can be found in Part 3 of the ([xAPI specification](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md)). SQL LRS is distinct from other LRSs developed at Yet Analytics for being SQL-based and supporting multiple SQL database management systems (DBMSs) like H2, SQLite, and PostgreSQL.

## How to use SQL LRS?

To use SQL LRS, a user needs to be authorized by an admin. Admin accounts can be created using special RESTful HTTP methods (described later in the README); logging into them will return a JSON Web Token (JWT), a temporary token that can then be used to create or access SQL LRS credentials. These credentials, which consist of an API key, a secret API key, and their scopes ([described in the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#42-oauth-10-authorization-scope)), are then used as headers for LRS-specific methods to authenticate and authorize the request sender.

## Differences from Cloud LRS

If you previously used Yet's Cloud LRS products, it is important to be aware of certain differences:
- Tenancy is not supported in SQL LRS; the entire database can be considered to be a single default tenant.
- All operations in SQL LRS are synchronous; async operations are not supported.
- `stored` timestamps are not strictly monotonic in SQL LRS; two or more Statements may be assigned the same timestamp if stored in quick succession.

[<- Back to Index](index.md)
