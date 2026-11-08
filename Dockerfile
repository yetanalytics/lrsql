FROM alpine:3.23
# Fail closed if the Alpine repositories cannot provide patched zlib (CVE-2026-4738).

ADD target/bundle /lrsql
ADD .java_modules /lrsql/.java_modules

# replace the linux runtime via jlink
RUN apk update \
        && apk upgrade \
        && apk add --no-cache 'zlib>=1.4-r0' \
        && apk add ca-certificates \
        && update-ca-certificates \
        && apk add --no-cache openjdk21 \
        && java -version \
        && mkdir -p /lrsql/runtimes \
        && jlink --output /lrsql/runtimes/linux/ --add-modules $(cat /lrsql/.java_modules) \
        && apk del openjdk21 \
        && rm -rf /var/cache/apk/*

# delete bench utils for leaner container
RUN rm -rf /lrsql/bench*

WORKDIR /lrsql
EXPOSE 8080
EXPOSE 8443
CMD ["/lrsql/bin/run_sqlite.sh"]
