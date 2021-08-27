#!/bin/sh

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=linux;;
    *)          machine=macos;;
esac

echo ${machine}
