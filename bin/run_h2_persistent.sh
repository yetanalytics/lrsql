#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -server -cp lrsql.jar lrsql.h2.main --persistent true $@
