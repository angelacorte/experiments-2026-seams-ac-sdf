#!/bin/bash
# Runs the three dynamic-population batches, one after the other.
#   ./run-batches.sh       # seeds 0..5 (the default MAX_SEED of the yaml)
#   ./run-batches.sh 20    # seeds 0..20
set -e
cd "$(dirname "$0")"
if [ $# -gt 0 ]; then
  [[ $1 =~ ^[0-9]+$ ]] || { echo "usage: $0 [max seed]" >&2; exit 1; }
  export MAX_SEED="$1"
fi
for scenario in Distance Displacement Position; do
  ./gradlew "runDynamicPopulation${scenario}BasedBatch"
done
