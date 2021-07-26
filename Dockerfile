FROM openjdk:11-slim
ADD target/bundle /bundle
WORKDIR /bundle
EXPOSE 8080
EXPOSE 8443
CMD ["bin/run_h2.sh"]
