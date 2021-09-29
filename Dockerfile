FROM openjdk:11-slim
ADD target/bundle /lrsql
WORKDIR /lrsql
EXPOSE 8080
EXPOSE 8443
CMD ["/lrsql/bin/run_sqlite.sh"]
