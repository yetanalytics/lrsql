[<- Back to Index](index.md)

# Authority Configuration

The SQL LRS allows for configuration of the Authority included in xAPI Statements that are written to the LRS.

### Configuring a custom Authority template

On startup the SQL LRS looks for the file `config/authority.json.template` and bases the authority on the template it finds there.

#### Static Custom Authority

The authority template file should contain an xAPI Agent object representing the desired authority. The object may contain static values, which would result in all statements in the LRS having the same authority.

Here is an example of a static Authority template file

```JSON
{
    "account": {
        "homePage":"https://www.yetanalytics.com",
        "name":"Yet Analytics"
    },
    "objectType":"Agent"
}
```

#### Dynamic Custom Authority

Alternatively, it can make use a few provided variables to make the Authority more dynamic with the Account or API Key used to write the statement. The following table contains the variables available for use in the Authority template:

| Variable | Description |
| --- | --- |
| `authority-url` | `LRSQL_AUTHORITY_URL` (`authorityUrl`) config variable value set by an environment variable or `config/lrsql.json`. Default is `http://example.org`. |
| `cred-id` | LRS Credential Pair ID (UUID). This can be used to form a unique Authority for each API Key. |
| `account-id` | LRS Admin Account ID (UUID). This can be used to make a unique Authority for each Account, but not necessarily for each API Key. |

Here is an example of an Authority template making use of some of these variables:

```json
{
    "account": {
        "homePage":"{{authority-url}}",
        "name":"{{cred-id}}"
    },
    "objectType":"Agent"
}
```

### Default Authority

If you do not configure a template, the default is to use the `cred-id` and `authority-url` variables like so:

```json
{
    "account": {
        "homePage":"{{authority-url}}",
        "name":"{{cred-id}}"
    },
    "objectType":"Agent"
}
```

### OIDC Authority

When SQL LRS is [configured for OIDC](oidc.md) an alternative template is used that forms the authority from token claims:

``` json
{
  "objectType": "Group",
  "member": [
    {
      "account": {
        "homePage": "{{iss}}",
        "name": "{{lrsql/resolved-client-id}}"
      }
    },
    {
      "account": {
        "homePage": "{{iss}}",
        "name": "{{sub}}"
      }
    }
  ]
}
```

Possible claims vary by identity provider, see the [comprehensive list here](https://www.iana.org/assignments/jwt/jwt.xhtml). SQL LRS provides one additional value, `lrsql/resolved-client-id` that resolves to the following claims in order of precedence:

1. `client_id`
2. `azp`
3. `aud` (if `aud` is a string)
4. `aud[0]` (if `aud` is an array)

Note that the request will fail if expected claims are not present on the token.

As with the normal authority template, you can provide a custom version by setting the `LRSQL_OIDC_AUTHORITY_TEMPLATE` (`oidcAuthorityTemplate`) config variable.

[<- Back to Index](index.md)
