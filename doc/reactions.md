[<- Back to Index](index.md)

# Reactions

Reactions allow SQL LRS to watch for patterns in submitted xAPI data and dynamically generate new statements in response.

## Usage

To use Reactions the `LRSQL_ENABLE_REACTIONS` environment variable or the `enableReactions` LRS configuration property must be set to `true`. Reactions are disabled by default.

Reactions are defined in JSON:

``` json

{
  "identityPaths": [
    [
      "actor",
      "mbox"
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

`identityPaths` is an array of zero or more paths that must match across all statements in the sequence. These could include an actor identifier or statement registration.

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

`template` describes the xAPI statement the reaction will produce. It is identical to an xAPI statement except where an object with the `$templatePath` key is found, where this is a path to one of the statements found for a given condition from which to derive a value. In the example above the template statement uses the actor mbox from statement `a`.

[<- Back to Index](index.md)
