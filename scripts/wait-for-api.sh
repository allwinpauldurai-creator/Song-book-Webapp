#!/usr/bin/env bash
# Waits until the Spring Boot readiness endpoint reports {"status":"UP"}.
#
# Usage: scripts/wait-for-api.sh <origin> <resourceGroup> <webAppName> [slot]
#
# App Service answers HTTP 200 with a Java "parking page" on every path while no app.jar is running,
# so an HTTP status check alone passes too early. This script requires the actuator JSON instead.
# If the parking page is still served after PARKING_RESTART_AFTER attempts, the app (or slot) is
# restarted once: `az webapp deploy --restart true` does not always replace a container that started
# before the jar was uploaded.
set -uo pipefail

origin="${1:?origin}"; rg="${2:?resource group}"; app="${3:?web app name}"; slot="${4:-}"
attempts="${ATTEMPTS:-60}"; interval="${INTERVAL:-10}"; restart_after="${PARKING_RESTART_AFTER:-18}"
url="${origin%/}/actuator/health/readiness"
slot_args=(); [ -n "$slot" ] && slot_args=(--slot "$slot")
restarted=false

for i in $(seq 1 "$attempts"); do
  body=$(curl -s -m 20 "$url" || true)
  if printf '%s' "$body" | grep -q '"status" *: *"UP"'; then
    echo "Ready after $i attempt(s): $body"
    exit 0
  fi
  if printf '%s' "$body" | grep -qi '<html'; then
    state="App Service parking page (app.jar not running yet)"
    if [ "$i" -ge "$restart_after" ] && [ "$restarted" = false ]; then
      echo "Still on the parking page after $i attempts: restarting $app ${slot:+(slot $slot)}"
      az webapp restart --resource-group "$rg" --name "$app" "${slot_args[@]}"
      restarted=true
    fi
  else
    state="${body:0:120}"
  fi
  echo "Attempt $i/$attempts not ready: ${state:-no response}"
  sleep "$interval"
done

echo "API not ready at $url after $attempts attempts"
exit 1
