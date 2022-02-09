#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -server -cp lrsql.jar lrsql.sqlite.main $@
