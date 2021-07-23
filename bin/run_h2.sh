#!/bin/sh

java -server -cp lrsql.jar clojure.main -m lrsql.h2.main $@
