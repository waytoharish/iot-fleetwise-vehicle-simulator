#!/bin/bash
set -euo pipefail

VEHICLE_ID_PREFIX=""
parse_args() {
    while [ "$#" -gt 0 ]; do
        case $1 in
        --vehicle-prefix)
            VEHICLE_ID_PREFIX=$2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTION]"
            echo "  --vehicle-id <ID>               Vehicle ID prefix"
            exit 0
            ;;
        esac
        shift
    done
}

echo "=================================================="
echo " compose vehicle-simulator-workshop-input.json"
echo "=================================================="

parse_args "$@"

sed -i.bak "s/PREFIX_/${VEHICLE_ID_PREFIX}_/g" vehicle-simulator-workshop-input.json
sed -i.bak 's?PWD?'`pwd`'?' vehicle-simulator-workshop-input.json

echo "complete"