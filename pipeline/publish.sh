#!/usr/bin/env bash
# Fetch the data.gov.in prices and force-push them to the `data` branch.
#
# This runs on a host with an Indian IP address, from cron, because
# api.data.gov.in refuses connections from GitHub's (non-Indian) Actions runners.
# See README, "How the data gets to the app".
#
# Needs:
#   DATA_GOV_IN_API_KEY  the data.gov.in API key
#   push access to MANDI_REMOTE (default: the SSH URL below), e.g. a deploy key
#   selected with GIT_SSH_COMMAND
set -euo pipefail

remote=${MANDI_REMOTE:-git@github.com:vivekg7/mandi.git}
repo_dir=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cd "$repo_dir"
python3 -m unittest discover -s pipeline -q

# The previous run's files are merged with this fetch (history, and series that
# have not reported yet today). First run: start empty.
mkdir "$work/out"
if git ls-remote --exit-code --heads "$remote" data >/dev/null; then
  git fetch -q --depth 1 "$remote" data
  git archive FETCH_HEAD | tar -x -C "$work/out"
fi

python3 pipeline/fetch_mandi.py --out "$work/out"
cp pipeline/DATA_BRANCH_README.md "$work/out/README.md"

# A single orphan commit, force-pushed: history lives inside the JSON files, so
# keeping every run's commit would only grow the clone.
cd "$work/out"
git init -q -b data
git config user.name "Mandi data"
git config user.email "vivek@ivy.homes"
git add -A
git commit -q -m "Prices as of $(date -u +%Y-%m-%dT%H:%MZ)"
git push -q -f "$remote" data
echo "published $(date -u +%Y-%m-%dT%H:%MZ)"
