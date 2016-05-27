#!/bin/bash

# Handles execution of JAAM's Visualizer for you!

: ${JAAM_dir:="$(cd $(dirname $(dirname $0)); pwd -P)"}
: ${JAAM_visualizer:="${JAAM_dir}/assembled/jaam-visualizer.jar"}
: ${JAAM_java:="java"}
: ${JAAM_java_opts:=""}

# Use JAVA_OPTS if provided, otherwise use the default from above.
chosen_java_opts=${JAVA_OPTS:-"${JAAM_java_opts}"}
JAVA_OPTS="${chosen_java_opts}"

# Do the execution.
exec "${JAAM_java}" -jar "${JAAM_visualizer}" "$@"