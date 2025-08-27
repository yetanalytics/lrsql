#!/bin/sh

az deployment sub create --location eastus --template-file main.bicep --parameters @parameters.json