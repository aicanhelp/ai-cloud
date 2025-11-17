#!/usr/bin/env bash

git -c protocol.version=2 submodule add --depth 1 $1 resources/$2