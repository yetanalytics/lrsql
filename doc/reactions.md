[<- Back to Index](index.md)

# Reactions

Reactions allow SQL LRS to watch for patterns in submitted xAPI data and dynamically generate new statements in response.

## Usage

To use Reactions the `LRSQL_ENABLE_REACTIONS` environment variable or the `enableReactions` LRS configuration property must be set to `true`. Reactions are disabled by default.

Each condition has a title, active status, and ruleset. Each ruleset in turn contains the following properties: conditions, template, and identity paths.

<!-- TODO: Screenshot of example condition -->

### Conditions

Each condition is a set of rules for finding significant statements. Each condition has a unique name followed by its rules, which can be composed with boolean logic.

In the example given, statement `a` must have an object `id` equal to `https://example.com/activities/a`, a verb `id` equal to `https://example.com/verbs/completed`, and its result `success` property equal to `true`. Statement `b` must have the same verb and result success but an object `id` equal to `https://example.com/activities/b` and a timestamp greater than that of `a`.

#### Rules

All rules have a path array that indicates a path in an xAPI statement and an operator that is one of the following:

* Greater than
* Less than
* Greater than or equal
* Less than or equal
* Equal
* Not equal
* Like (fuzzy match using SQL `%` syntax; for example, `bo%` matches `bob` and `boz`.)
* Array contains

Rules either have a `val` literal value or a `ref` which is a path into a statement found for another condition.

<!-- TODO: Screenshot of how to add/edit statement criteria -->

#### Booleans

Booleans compose multiple rules together. Booleans are objects with a single key:

* AND: Array of rules which must all be true
* OR: Array of rules of which one must be true
* NOT: Rule to nullify

<!-- TODO: Screenshot of how to change condition to boolean -->

### Template

The template describes the xAPI statement the reaction will produce. It is identical to an xAPI statement, except that object properties may be substituted with `$templatePath`. This is a path that points to a value in a statement matched by `conditions`, using a JSON array of xAPI statement properties. In the above example, the `$templatePath` points to the actor `mbox` for the actor matched by condition `a`.

<!-- TODO: Screenshot of how to create template path/dynamic variable -->
<!-- TODO: Screenshot of how to edit template JSON -->

### Identity Paths

Identity Paths are a method of grouping statements for which you are attempting to match conditions. Typically, Reactions may revolve around actor Inverse Functional Identifiers (IFIs), e.g. actor `mbox` or account `name` strings. This is equivalent to saying "For a given Actor, look for statements that share IFI values".

Alternative approaches to Identity Path may be used by modifying `identityPaths`, for instance using the `registration` context property to group statements by learning session.

<!-- TODO: Screenshot of how to edit identity paths -->

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
