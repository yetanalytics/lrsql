[<- Back to Index](index.md)

# Reactions

Reactions allow SQL LRS to watch for patterns in submitted xAPI data and dynamically generate new statements in response.

## Usage

To use Reactions the `LRSQL_ENABLE_REACTIONS` environment variable or the `enableReactions` LRS configuration property must be set to `true`. Reactions are disabled by default.

Reaction "rulesets" are defined in JSON:

``` json

{
  "identityPaths": [
    [
      "actor",
      "mbox"
    ],
    [
      "actor",
      "mbox_sha1sum"
    ],
    [
      "actor",
      "openid"
    ],
    [
      "actor",
      "account",
      "homePage"
    ],
    [
      "actor",
      "account",
      "name"
    ]
  ],
  "conditions": {
    "a": {
      "and": [
        {
          "path": [
            "object",
            "id"
          ],
          "op": "eq",
          "val": "https://example.com/activities/a"
        },
        {
          "path": [
            "verb",
            "id"
          ],
          "op": "eq",
          "val": "https://example.com/verbs/completed"
        },
        {
          "path": [
            "result",
            "success"
          ],
          "op": "eq",
          "val": true
        }
      ]
    },
    "b": {
      "and": [
        {
          "path": [
            "object",
            "id"
          ],
          "op": "eq",
          "val": "https://example.com/activities/b"
        },
        {
          "path": [
            "verb",
            "id"
          ],
          "op": "eq",
          "val": "https://example.com/verbs/completed"
        },
        {
          "path": [
            "result",
            "success"
          ],
          "op": "eq",
          "val": true
        },
        {
          "path": [
            "timestamp"
          ],
          "op": "gt",
          "ref": {
            "condition": "a",
            "path": [
              "timestamp"
            ]
          }
        }
      ]
    }
  },
  "template": {
    "actor": {
      "mbox": {
        "$templatePath": [
          "a",
          "actor",
          "mbox"
        ]
      }
    },
    "verb": {
      "id": "https://example.com/verbs/completed"
    },
    "object": {
      "id": "https://example.com/activities/a-and-b",
      "objectType": "Activity"
    }
  }
}


```

### Identity Paths

Identity Paths (`identityPaths` in the ruleset JSON) are a method of grouping statements for which you are attempting to match conditions. Typically, Reactions may revolve around actor Inverse Functional Identifiers (IFIs), e.g. `["actor", "mbox"]` or `["actor", "account", "name"]` which is equivalent to saying "For a given Actor, look for statements that share IFI values".

Alternative approaches to Identity Path may be used by modifying `identityPaths`, for instance `["context", "registration"]` to group statements by learning session.

### Conditions

`conditions` is a mapping of names to rules for finding significant statements. Rules can be composed with boolean logic.

In the example given above statement `a` must have an object id equal to `https://example.com/activities/a`, a verb id equal to `https://example.com/verbs/completed`, and a result success equal to `true`. Statement `b` must have the same verb and result success but an object id equal to `https://example.com/activities/b` and a timestamp greater than that of `a`.

#### Rules

All rules have a `path` array that indicates a path in an xAPI statement and an `op` that is one of the following operators:

* `gt` - Greater than
* `lt` - Less than
* `gte` - Greater than or equal
* `lte` - Less than or equal
* `eq` - Equal
* `noteq` - Not equal
* `like` - Fuzzy match using SQL `%` syntax. For example, `bo%` matches `bob` and `boz`.
* `contains` - Array contains

Rules either have a `val` literal value or a `ref` which is a path into a statement found for another condition.

#### Booleans

Booleans compose multiple rules together. Booleans are objects with a single key:

* `and` - Array of rules which must all be true
* `or` - Array of rules of which one must be true
* `not` - Rule to nullify

### Template

`template` describes the xAPI statement the reaction will produce. It is identical to an xAPI statement, except that object properties may be substituted with `$templatePath`. This is a path that points to a value in a statement matched by `conditions`, using the same syntax as an `identityPaths` path. In the above example, the `$templatePath` points to the actor `mbox` for the actor matched by condition `a`.

## Example

Given the reaction specified above, if the following statements are posted to the LRS:

``` json
[
  {
    "actor": {
      "mbox": "mailto:bob@example.com"
    },
    "verb": {
      "id": "https://example.com/verbs/completed"
    },
    "object": {
      "id": "https://example.com/activities/a",
      "objectType": "Activity"
    },
    "result": {
      "success": true
    },
    "timestamp": "2024-01-23T01:00:00.000Z"
  },
  {
    "actor": {
      "mbox": "mailto:bob@example.com"
    },
    "verb": {
      "id": "https://example.com/verbs/completed"
    },
    "object": {
      "id": "https://example.com/activities/b",
      "objectType": "Activity"
    },
    "result": {
      "success": true
    },
    "timestamp": "2024-01-23T02:00:00.000Z"
  }
]

```

Then the following statement will be added subsequently (note that some unrelated fields are removed for clarity):

``` json
{
  "actor": {
    "mbox": "mailto:bob@example.com"
  },
  "verb": {
    "id": "https://example.com/verbs/completed"
  },
  "object": {
    "id": "https://example.com/activities/a-and-b",
    "objectType": "Activity"
  }
}

```

[<- Back to Index](index.md)
