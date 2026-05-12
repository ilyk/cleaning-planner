# Spawn-Infra Debug Playbook

> Owner: operator (needs shell + sometimes daemon-restart privileges).
> Distilled from the 2026-05-11 swarm session where 9 of 13 spawned agents zombied or died at t+0s.

## Symptoms checklist

The spawn pipeline is sick when **any** of these is true:

1. A freshly-spawned agent's status file has `status: died`, `reason: "process exited"`, and `updated == spawned_at` (to-the-second). This is the **fast-failure** mode — characteristic of `dev-kotlin` spawns in the May 11 session.
2. A freshly-spawned agent's status file shows `status: working` but `progress` is `""` AND `updated` does not advance over many minutes. This is the **silent-zombie** mode — characteristic of `researcher`, `dev-rust`, and most other agent types.
3. `mcp__skuld__list_agents` returns the agent as "alive" but the agent has never written a `FINDING:` or `DECISION:` line to `.skuld/swarm/scratchpad.md`, and no expected output files appear on disk.
4. Supervisor "idle Ns" timers monotonically increase (no resets to 0s) — the heartbeat tick isn't firing, so the agent process is gone.

The pipeline is **healthy** when:

* `updated` advances on a fresh status file within ~10 seconds of spawn.
* The agent's first scratchpad line (`FINDING: <name>-online`) appears within ~30 seconds.
* Expected output files (per the agent's brief) appear within the time budget.

Note: a momentary `idle 30-60s` between tool calls is **normal** during file reads or thinking. Only sustained idle (>10 minutes) without scratchpad output is dead.

## Diagnostic commands

Run from any directory; paths are absolute.

```bash
# 1. Is the daemon up?
ps aux | grep -E "skuld daemon" | grep -v grep
# Expected: one PID, started long enough ago that you trust it.

# 2. Inventory current agent status files
for f in .skuld/swarm/status/*.json; do
  python3 -c "
import json
d = json.load(open('$f'))
print(f\"{d.get('agent','?'):>20}  status={d.get('status','?'):<10}  \"
      f\"spawned={d.get('spawned_at','?')[:19]}  updated={d.get('updated','?')[:19]}  \"
      f\"reason={d.get('reason') or '-'}\")
"
done | sort -k4
# Look for: died with updated==spawned, or working with frozen updated.

# 3. Time since each "working" agent last ticked
NOW=$(date -u +%s)
for f in .skuld/swarm/status/*.json; do
  python3 -c "
import json, datetime
d = json.load(open('$f'))
if d.get('status') == 'working':
    upd = datetime.datetime.fromisoformat(d['updated'].replace('Z','+00:00'))
    age = (datetime.datetime.now(datetime.timezone.utc) - upd).total_seconds()
    print(f\"{d['agent']}: idle {int(age)}s\")
"
done
# Anything >600s without scratchpad output is a zombie.

# 4. Scratchpad output from a specific agent (replace AGENT)
grep -E "FINDING:|DECISION:" .skuld/swarm/scratchpad.md | grep -i AGENT

# 5. Recent skuld log activity (orchestrator.log is the busiest)
ls -lt ~/.cache/skuld/segments/ | head -5
tail -100 ~/.cache/skuld/segments/orchestrator.log

# 6. Active TCP listeners / daemon socket
ss -lnpx 2>/dev/null | grep skuld | head -5
# Or, for AF_UNIX socket lookups:
lsof -U -a -p $(pgrep -f "skuld daemon") 2>/dev/null | head
```

## Remediation — cheapest to most invasive

### Step 1: Clear stale status / inbox files (safest)

```bash
cd /home/ilyk/ai-projects/active/product/android/cleanFlow/.skuld/swarm/

# Move dead agent status files out of the way; do NOT delete agents currently
# listed as alive in mcp__skuld__list_agents.
mkdir -p status/_processed-$(date +%Y%m%d-%H%M)
for f in status/*.json; do
  python3 -c "
import json, sys, shutil, os
d = json.load(open('$f'))
if d.get('status') == 'died':
    shutil.move('$f', 'status/_processed-$(date +%Y%m%d-%H%M)/' + os.path.basename('$f'))
"
done
ls status/
```

This frees the agent name slots (so `dev-kotlin-2` etc. can be re-used) without touching agents that are currently working.

### Step 2: Try a fresh trivial spawn

```bash
# Through MCP tool from an active session:
#   mcp__skuld__spawn_agent(
#     agent_type="researcher",
#     task="Write the literal string 'spawn-probe-ok' to .skuld/swarm/scratchpad.md and exit.")
# Then wait ~30s and grep the scratchpad. If the line appears, the pipeline works.
```

If a trivial researcher probe lives, agent-type-specific issues are the cause (focus on the broken type's config). If it zombies too, the pipeline is systemically broken — go to step 3.

### Step 3: Inspect the agent runner config

skuld stores agent definitions somewhere under `~/.config/skuldkore/` or as embedded data in the daemon binary. Find them:

```bash
find ~/.config/skuldkore ~/.skuld ~/.local/share/skuld -type f -name "*.toml" -o -name "*.yaml" -o -name "*.json" 2>/dev/null | head -20
strings ~/.local/bin/skuld | grep -E "agent_type|dev-kotlin|researcher" | head
```

Look for the dev-kotlin agent definition. Compare to a known-working agent type (e.g. `researcher`). Common breakages:

* Model name pointing at a retired ID (e.g., `claude-opus-4-6` after Opus was bumped to `claude-opus-4-7`). **Note:** the May 11 session proved that the `model` parameter on `mcp__skuld__spawn_agent` does NOT override a hard-coded value in the agent definition — `dev-kotlin-6` died even with `model=claude-opus-4-7` passed explicitly.
* System prompt referencing tools the binary no longer exposes.
* Executable path that no longer resolves (e.g. `claude` binary moved).

### Step 4: Restart the daemon (more invasive)

```bash
# Find the current daemon PID
SKULD_PID=$(pgrep -f "skuld daemon")
echo "current pid: $SKULD_PID"

# Save its log tail for postmortem
tail -2000 ~/.cache/skuld/segments/orchestrator.log > /tmp/skuld-pre-restart.log

# Graceful stop
kill -TERM $SKULD_PID
sleep 2

# Confirm gone
ps -p $SKULD_PID || echo "stopped"

# Restart (mirrors the original invocation flags)
nohup /home/ilyk/.local/bin/skuld daemon -vvv >> ~/.cache/skuld/segments/daemon-restart.log 2>&1 &
sleep 1
pgrep -f "skuld daemon"
```

After restart, run step 2's trivial probe again.

### Step 5: Reset spawn queues

Only if step 4 didn't help.

```bash
# Skuld spawn-request/response queues (path observed in 2026-05-09 orchestrator log)
SPAWN_DIR=/home/ilyk/ai-projects/active/ops/mcp-servers/.skuld/swarm
ls -la $SPAWN_DIR/spawn-requests $SPAWN_DIR/spawn-responses 2>/dev/null
# Move (don't delete) stale entries
mkdir -p $SPAWN_DIR/_attic-$(date +%Y%m%d-%H%M)
mv $SPAWN_DIR/spawn-responses/*.json $SPAWN_DIR/_attic-$(date +%Y%m%d-%H%M)/ 2>/dev/null
```

### Step 6: Reinstall the binary (last resort)

```bash
which skuld
# Then per its installation source (cargo install, brew, manual, etc.) — reinstall.
# After reinstall, run step 4 again.
```

## When to escalate

* `~/.cache/skuld/segments/orchestrator.log` shows `panic:` or `thread 'main' panicked` — daemon is crashing, not just misconfigured. Pin the stack trace and report upstream.
* Multiple successive trivial probes (step 2) all zombie — pipeline is structurally broken, not a transient flake.
* `dmesg` or `journalctl --user` shows OOM kills around spawn times — host resource exhaustion. Check memory; spawn-loop traps can compound this.
* `df -h ~/.cache /tmp` near 100% — disk-full causes silent fork failures.

## What we learned on 2026-05-11

Definitive observations from the failed session, useful as a baseline for future debugging:

* **9 of 13 spawned agents zombied or died at t+0s** (`dev-kotlin-2/3/4/5/6`, `dev-rust`, `researcher` original, `researcher-2`, `researcher-4`).
* **1 worker (`researcher-3`) lived for 6+ hours** and successfully spawned a sub-agent (`researcher-4`), then ticked supervisor checks without ever producing scratchpad output or writing its expected file.
* `dev-kotlin` fails via the fast path (status flips to `died` within ~3s).
* All other types fail via the silent-zombie path (status stays `working`, `updated` freezes at `spawned_at`, no work product).
* The `mcp__skuld__spawn_agent` `model` parameter does NOT override an agent-runner-level model setting; explicit `model=claude-opus-4-7` produced an identical failure to the no-param case.
* The orchestrator self-execute pattern landed 10 production commits in the same window the spawn pipeline failed — it's a working fallback when the swarm is sick.
