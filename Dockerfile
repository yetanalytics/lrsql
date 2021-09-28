FROM openjdk:11-slim
ADD target/bundle /lrsql
WORKDIR /lrsql
EXPOSE 8080
EXPOSE 8443
CMD ["bin/run_h2.sh"]
