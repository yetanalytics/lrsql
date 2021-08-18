# TLS/HTTPS

lrsql will attempt to access certificates used for both HTTPS and signing in the following order:

1. If `LRSQL_KEY_FILE` specifies a valid keystore on disk, it will be used.
2. If `LRSQL_KEY_PKEY_FILE` and `LRSQL_KEY_CERT_CHAIN` specificy valid PEM files on disk, an in-memory keystore will be created and used based on their contents.
3. If no keystore or cert files are found, lrsql will create a self-signed cert and log a warning.

## Generating Dev Certs with `mkcert`

If you install [mkcert](https://github.com/FiloSottile/mkcert) you can generate stable valid certs to use while developing the app:

``` shell

$ cp "$(mkcert -CAROOT)"/rootCA.pem config/cacert.pem
$ mkcert -key-file config/server.key.pem \
         -cert-file config/server.crt.pem \
         example.com "*.example.com" example.test localhost 127.0.0.1 ::1
$ clojure -Mdb-h2 -m lrsql.h2.main
...
11:25:54.085 [main] INFO  lrsql.util.cert - Generated keystore from key and cert(s)...

```
