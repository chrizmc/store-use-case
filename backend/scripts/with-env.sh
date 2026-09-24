#!/usr/bin/env bash
# Loads backend/.env into the shell env before running a command. psql/cypher-shell
# are separate processes and don't get .env values automatically the way dotenv/config
# does for the Node process — npm scripts that shell out to them need this.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a
source .env
set +a
eval "$1"
