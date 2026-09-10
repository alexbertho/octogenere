# Future fixes and additions - `fr.octogenere.security`

Notes from a critical re-read of the sandbox and prompt-injection code after it was merged into `dev`.
Everything below is about the module as it exists today; the things already acknowledged in the report
(no VM layer, heuristic scanner, no offline Maven cache) are only cross-referenced at the bottom, not
repeated.

Ordered roughly by how much it matters. Items marked **[verified]** were reproduced on a real Docker
daemon, not just spotted by reading the code.

---

## 1. Real bugs

### 1.1 Host memory can be exhausted by container output **[verified]** - high

`DockerSandboxExecutor.readFully()` collects stdout and stderr into a `ByteArrayOutputStream` with
`transferTo()` and **no size limit**. The container's `--memory 512m` does not help here: those bytes
travel through a pipe into the *host* JVM, so the limit that applies is the JVM heap, not the cgroup.

Measured: a sandboxed container emits **over 100 MB/s** of stdout (`yes` piped to `head -c`, capped at
200 MB on purpose so as not to actually hurt the machine). With the default 120 s timeout that is
several gigabytes buffered on the host, i.e. an `OutOfMemoryError` in the analysis process.

This directly undercuts the main claim of the module ("an analyzed project cannot exhaust host
resources"), and it is the one issue here I would fix before anyone runs this against a project they
did not write.

Fix: cap capture at something like 2-5 MB per stream, stop reading (or keep draining but discard)
past the cap, and flag the truncation on `SandboxResult` so the caller knows output was cut.

### 1.2 A caller can silently disable the container-side timeout **[verified]** - high

`DockerCommandBuilder.buildRunArgs()` adds `-e SANDBOX_TIMEOUT_SECONDS=<limit>` at line ~56, then
appends the caller's own environment variables **after** it (lines ~60-63). Docker resolves duplicate
`-e KEY=` flags by taking the **last** one:

```
docker run -e SANDBOX_TIMEOUT_SECONDS=30 -e SANDBOX_TIMEOUT_SECONDS=0 ...
  -> resolved value: 0
```

So `ExecutionRequest.builder(dir, cmd).env("SANDBOX_TIMEOUT_SECONDS", "0")` disables the entrypoint's
`timeout` entirely, through an API call that looks completely ordinary. Only the Java-side watchdog
is left, and that one kills the *client*, not the container.

Fix: append the caller's env first and the security-critical variables last, and/or reject reserved
key names (anything starting with `SANDBOX_`) in `ExecutionRequest.Builder.env()`.

### 1.3 A sub-second timeout becomes "no timeout at all" **[verified]** - medium

`SandboxLimits` only rejects null/zero/negative durations, so `Duration.ofMillis(500)` is accepted.
`DockerCommandBuilder` then does `limits.timeout().toSeconds()`, which truncates it to **0**, and
coreutils treats `timeout 0` as "no timeout":

```
timeout --signal=KILL 0 sleep 3   ->   ran the full 3s, exit 0
```

Same end result as 1.2 but reachable by accident rather than on purpose. (My own unit test in
`DockerSandboxExecutorTest` passes `ofMillis(1)`, which is exactly this case; it passes only because
the fake launcher never reaches a real container.)

Fix: require at least one second in the `SandboxLimits` compact constructor, or round up with
`Math.max(1, toSeconds())` when building the argument list. Requiring it is better - failing loudly
beats silently changing what the caller asked for.

### 1.4 Thread pool leak on the interrupted path - medium

In `runAndCollect()`, `streamReaders.shutdownNow()` sits after the try/catch. The
`InterruptedException` branch throws `SandboxException` before ever reaching it, so the two reader
threads are leaked. `Executors.newFixedThreadPool` creates **non-daemon** threads, so enough leaked
pairs would also keep the JVM from shutting down on its own.

Fix: wrap the whole body in try/finally around the executor, or use two plain daemon threads instead
of a pool (nothing here needs a pool).

### 1.5 `collect()` can block forever - medium

`collect()` calls `future.get()` with no timeout. It is reached after `destroyForcibly()`, and the
assumption is that killing the client closes the pipes and `readFully` sees EOF. That is usually
true, but `destroyForcibly()` is explicitly documented as not necessarily immediate, and combined
with 1.1 the call also blocks for as long as a flood takes to drain. A hang here freezes the whole
analysis - the exact failure the timeout exists to prevent.

Fix: `future.get(n, TimeUnit.SECONDS)` and give up on the stream if it expires.

### 1.6 Cleanup process output is never drained, and it is never killed - low

`forceRemoveContainer()` starts `docker rm -f` and calls `waitFor(10s)` without reading its output.
`docker rm -f` prints one short line so it will not deadlock in practice, but this is the same
pipe-buffer trap I deliberately avoided on the main path, and if `waitFor` does time out the process
is left behind instead of being destroyed. `DockerAvailability.probe()` has the same shape.

Fix: `redirectOutput(DISCARD)`/`redirectError(DISCARD)` on both, and `destroyForcibly()` if the wait
expires.

---

## 2. Things that were planned but never implemented

### 2.1 No pre-flight check that the sandbox image exists - medium

The design plan said the executor should fail fast with a clear message if `docker image inspect`
does not find `octogenere/sandbox:1.0`. That never got written. What actually happens today: `docker
run` fails with exit code 125 and a message on stderr, which the caller sees as a normal non-zero
result - so "you forgot to build the image" is indistinguishable from "the analyzed project failed to
compile". The nice error string in the `IOException` branch only fires when the `docker` binary
itself is missing, which is a different situation.

Fix: check once (lazily, cached) before the first run, and throw `SandboxException` with the
`docker build` command in the message.

### 2.2 No logging or traceability - medium

Section 12 of the subject asks for per-analysis traceability: what ran, how long, what failed. This
module logs nothing at all, and `forceRemoveContainer()` swallows its exception into an empty catch,
so a failed cleanup - meaning a leaked container - is completely invisible. There is no logging
framework in the project yet, which is presumably why I skipped it, but "cleanup failed" is exactly
the event someone will want in a log later.

Fix: agree on a logging approach with the team, then at minimum log container name, command, exit
code, duration, and any cleanup failure. Being careful not to log the analyzed project's file
contents.

---

## 3. Security hardening worth doing

### 3.1 Bidirectional Unicode ("Trojan Source") is not stripped - medium

`UntrustedContentWrapper.sanitize()` removes zero-width characters (U+200B-200D, U+FEFF) and C0/C1
controls via `Character.isISOControl`. It does **not** remove the bidi override and isolate
characters (U+202A-U+202E, U+2066-U+2069), which are neither zero-width nor ISO controls. Those are
the basis of the Trojan Source class of attacks (CVE-2021-42574): source that renders one way to a
human reviewer and means something else to the compiler.

For a tool whose entire job is reviewing untrusted source code, this is an odd gap. It is also a
cheap fix and a genuinely interesting paragraph for the defense.

### 3.2 The prompt delimiter is a fixed, guessable string - medium

`BEGIN_TAG`/`END_TAG` are compile-time constants. Occurrences inside the content are replaced, so a
file cannot forge a closing boundary today - that part is fine. But the framing sentence around the
block ("What follows is extracted as-is...") is also fixed and predictable, so a file can imitate the
*wrapper's own voice* and try to look like trusted framing rather than data.

Fix: generate a random nonce per call and put it in both delimiters (`UNTRUSTED_<uuid>_BEGIN`). The
content cannot predict a value chosen after it was read.

### 3.3 The mount source is not canonicalised - low/medium

`ExecutionRequest.Builder` checks `Files.isDirectory(...)` and the command builder then uses
`toAbsolutePath()`. Neither resolves symlinks. If the path handed in is (or contains) a symlink -
plausible once project import starts unpacking archives, since archives can contain symlinks - Docker
bind-mounts whatever it actually resolves to. It is mounted read-only so nothing gets written, but
the *contents* of an unintended directory would be exposed to the analyzed code.

Fix: `toRealPath()` at build time, and consider refusing paths that resolve outside an expected
workspace root.

### 3.4 `/tmp` is mounted `exec` - low

`/workspace` needs `exec` (a Gradle wrapper script has to be runnable). `/tmp` almost certainly does
not, and dropping a binary in `/tmp` and running it is a standard move. Changing `/tmp` to `noexec`
costs nothing and removes one easy path.

### 3.5 Reserved-name collision on container names - very low

Container names are `octogenere-sbx-<uuid>`, so collisions are not a practical concern, but nothing
stops a *different* process on the same host from creating a container with a colliding name and
having our `docker rm -f` remove it. Not worth fixing, noted for completeness.

---

## 4. API and integration gaps

These are not bugs, they are things the analysis engine / GUI will hit as soon as someone actually
wires this in.

- **No way to observe progress.** Output is buffered and only returned when the command finishes. The
  subject requires the GUI to show analysis progress, so a long `mvn test` will look frozen. A
  callback/`Observer` hook that receives lines as they arrive would fix this, and pairs naturally with
  whatever progress mechanism the UI team builds.
- **`SandboxResult` does not say *why* it failed.** The caller gets an `int`. Distinguishing "image
  missing" (125), "command not executable" (126/127), OOM-kill, and timeout currently requires the
  caller to know Docker exit-code trivia. A small `enum` or a few boolean accessors would make the
  analysis engine's job much easier, and would also let us finally separate OOM from timeout (see the
  report's limitation about exit code 137).
- **No public availability check.** `DockerAvailability` is package-private in effect (only the tests
  use it). The GUI cannot ask "is the sandbox usable on this machine?" in order to grey out a button,
  without triggering a failed run first. Expose it via `SandboxService`.
- **The `DockerAvailability` result is cached forever.** If Docker Desktop is started after the first
  probe, the cached `false` sticks for the life of the JVM.
- **No size cap on content handed to `PromptInjectionGuard.protect()`.** A huge file gets wrapped
  whole. Chunking is the LLM team's concern, but a guard here would be cheap.

---

## 5. Test coverage gaps

The suite is 62 unit tests plus 6 Docker integration tests, all passing, but:

- `DockerCommandBuilderTest` does not assert `--memory-swap` (that swap is disabled, which is what
  makes the memory limit meaningful) nor either `--tmpfs` flag. Both were added to the builder after
  the test was written.
- Nothing covers the two bugs in 1.2 and 1.3 - a test asserting that a caller-supplied
  `SANDBOX_TIMEOUT_SECONDS` cannot end up last in the argument list would have caught 1.2 immediately.
- No test for concurrent `run()` calls. Names are UUID-based so it should be fine, but "should be
  fine" is what the `--rm` assumption was too.
- The fork-bomb / `--pids-limit` behaviour was only checked by hand, not in the IT suite. It was left
  out on purpose (slow, and unpleasant if the limit does not hold), which is probably still the right
  call, but it means that particular protection is not regression-tested.

---

## 6. Already documented in the report

Listed here only so this file is a complete picture; the reasoning is in `reportGilles.txt`:

- No VM layer around Docker; a kernel-level container escape defeats everything here.
- Exit code 137 conflates our timeout with an OOM kill (see 4. above for how to actually fix it).
- No pre-warmed Maven repository in the image, so real builds need `networkEnabled(true)`.
- The injection scanner is heuristic and can be evaded by rephrasing.
- Per-container limits only; no global budget across concurrent analyses.

---

## Suggested order if picking this up

1. 1.1 (host memory) and 1.2 (timeout override) - both are security-relevant and both are small fixes.
2. 1.3, 1.4, 1.5 - correctness/robustness, all localised to `DockerSandboxExecutor`/`SandboxLimits`.
3. 2.1 and 3.1 - cheap, and 3.1 is worth talking about at the defense.
4. Section 4 items, once the analysis engine actually exists and we know what it needs.
