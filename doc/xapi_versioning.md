[<- Back to Index](index.md)

# xAPI Versioning

SQL LRS supports xAPI versions 1.0.3 and 2.0.0. The version used is determined by the `X-Experience-API-Version` header in the request. Use the LRSQL_SUPPORTED_VERSIONS environment variable or the `supportedVersions` LRS configuration property to set which versions are supported. By default, both versions are supported. For more information see [the configuration documentation](env_vars.md).

## Strict Versioning

Note that by default, responses to requests with an `X-Experience-API-Version` header set to `1.0.3` may contain statements in `2.0.0` format. To ensure that `2.0.0` statements are downgraded to `1.0.3` format use the `LRSQL_ENABLE_STRICT_VERSION` environment variable or the `enableStrictVersion` LRS configuration property. For more information see [the configuration documentation](env_vars.md).

## Reaction Versioning

Reactions generate version `1.0.3` statements by default but you can configure this by setting the `LRSQL_REACTION_VERSION` environment variable or the `reactionVersion` LRS configuration property. For more information see [the configuration documentation](env_vars.md). Note that creating reactions under `2.0.0` and then restricting the LRS to version `1.0.3` only will lead to errors in the LRS Admin UI frontend. To recover from this situation reenable `2.0.0` support and delete any incompatible reactions before returning to `1.0.3` only.

[<- Back to Index](index.md)
