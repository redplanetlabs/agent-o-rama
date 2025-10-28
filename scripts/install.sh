#!/bin/sh

set -eu

sh scripts/build-ui.sh
lein install
