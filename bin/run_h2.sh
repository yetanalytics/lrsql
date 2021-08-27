#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -server -cp lrsql.jar clojure.main -m lrsql.h2.main $@
