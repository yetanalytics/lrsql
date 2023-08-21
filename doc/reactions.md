[<- Back to Index](index.md)

# Reactions

Reactions allow SQL LRS to watch for patterns in submitted xAPI data and dynamically generate new statements in response.

## Usage

To use Reactions the `LRSQL_ENABLE_REACTIONS` environment variable or the `enableReactions` LRS configuration property must be set to `true`. Reactions are disabled by default.

Reactions are defined as JSON:

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

### Conditions

### Template

[<- Back to Index](index.md)
