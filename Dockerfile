FROM alpine:3.19.1

ADD target/bundle /lrsql
ADD .java_modules /lrsql/.java_modules

# replace the linux runtime via jlink
RUN apk update \
        && apk upgrade \
        && apk add ca-certificates \
        && update-ca-certificates \
        && apk add --no-cache openjdk11 \
        && mkdir -p /lrsql/runtimes \
        && jlink --output /lrsql/runtimes/linux/ --add-modules $(cat /lrsql/.java_modules) \
        && apk del openjdk11 \
        && rm -rf /var/cache/apk/*

WORKDIR /lrsql
EXPOSE 8080
EXPOSE 8443
CMD ["/lrsql/bin/run_sqlite.sh"]
