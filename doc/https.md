# TLS/HTTPS

SQL LRS has a number of ways of configuring a certificate for HTTPS. The system will attempt to use certificates in the following order when it starts up, based on configuration variables:

### 1. Custom Keystore

If you have created a keystore containing a certificate you wish to use with the SQL LRS, specify the following variables in `config/lrsql.json` (or as environment variables). See the guide on [configuration variables](env_vars.md) for more information.

- Set `LRSQL_KEY_FILE` (`keyFile` in `config/lrsql.json`) to the location of a valid keystore on disk
- Set `LRSQL_KEY_ALIAS` and `LRSQL_KEY_PASSWORD` (`keyAlias` and `keyPassword` respectively in config file)

Your `config/lrsql.json` should resemble the following:

``` json
{
  ...
  "webserver" : {
    ...
    "keyFile" : "my_keystore_location.jks",
    "keyAlias" : "my_certificate_alias",
    "keyPassword" : "my_key_password"
  }
}
```
### 2. Custom PEM Files

If you did not set the keystore variables in the previous section, the SQL LRS will then look for pem files set with the following variables:

- Set `LRSQL_KEY_PKEY_FILE` (`keyPkeyFile` in config file) to the location of your PEM private key
- Set `LRSQL_KEY_CERT_CHAIN` (`keyCertChain` in config file) to the location of the certificate PEM file and optionally additional cert chain pems (comma separated) provided by your registrar.

```json
{
  ...
  "webserver" : {
    ...
    "keyPkeyFile" : "config/my_private.key.pem",
    "keyCertChain" : "config/my_certificate.crt.pem,config/my_cert_chain.pem"    
  }
}
```

### 3. Self-Signed Temporary TLS Certificate

If no keystore or cert files are found, SQL LRS will create a self-signed cert and log a warning.

## Generating Dev Certs with `mkcert`

If you install [mkcert](https://github.com/FiloSottile/mkcert) you can generate stable "valid" certs to use while developing the app. These should only be used locally for development purposes:

``` shell

$ cp "$(mkcert -CAROOT)"/rootCA.pem config/cacert.pem
$ mkcert -key-file config/server.key.pem \
         -cert-file config/server.crt.pem \
         example.com "*.example.com" example.test localhost 127.0.0.1 ::1
$ clojure -Mdb-h2 -m lrsql.h2.main
...
11:25:54.085 [main] INFO  lrsql.util.cert - Generated keystore from key and cert(s)...

```


//
- If you are using Java Keystores, `keyFile` should point to a valid Keystore file; `keyAlias` and **especially** `keyPassword` should be overridden.
