#!/usr/bin/env python3
"""Frames per second from `dumpsys SurfaceFlinger --latency` samples (see fps-probe.sh).

Each sample file: "layer: <name>", the refresh period in ns, then one line per frame
"desiredPresent actualPresent frameReady" (ns). Samples overlap, so frames are de-duplicated by
their actual present time. A gap longer than IDLE_MS means nothing was drawing (the map was not
moving), so fps is computed over the busy stretches only and the idle time is reported apart.

    python3 -I fps_stats.py shots/fps-<label>      # per-layer table
    python3 -I fps_stats.py --selftest             # checks itself on synthetic frames
"""
import os
import sys

IDLE_MS = 500.0
PENDING = 2**63 - 1  # SurfaceFlinger writes INT64_MAX for a frame not yet presented


def read_sample(path):
    layer, period, frames = None, None, []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if line.startswith("layer: "):
                layer = line[len("layer: "):]
                continue
            parts = line.split()
            if len(parts) == 1 and parts[0].isdigit() and period is None:
                period = int(parts[0])
            elif len(parts) == 3 and all(p.isdigit() for p in parts):
                present = int(parts[1])
                if 0 < present < PENDING:
                    frames.append(present)
    return layer, period, frames


def stats(presents, period_ns):
    t = sorted(set(presents))
    if len(t) < 2:
        return None
    iv = [(b - a) / 1e6 for a, b in zip(t, t[1:])]
    busy_iv = [x for x in iv if x <= IDLE_MS]
    busy_ms = sum(busy_iv)
    idle_ms = sum(x for x in iv if x > IDLE_MS)
    s = sorted(busy_iv) or [0.0]
    pct = lambda p: s[min(len(s) - 1, int(round(p / 100.0 * (len(s) - 1))))]
    refresh = period_ns / 1e6 if period_ns else None
    return {
        "frames": len(t),
        "fps": (len(busy_iv) / (busy_ms / 1000.0)) if busy_ms > 0 else 0.0,
        "p50_ms": pct(50),
        "p95_ms": pct(95),
        "max_ms": max(busy_iv) if busy_iv else 0.0,
        "busy_s": busy_ms / 1000.0,
        "idle_s": idle_ms / 1000.0,
        "refresh_ms": refresh,
    }


def collect(folder):
    layers = {}
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".txt"):
            continue
        layer, period, frames = read_sample(os.path.join(folder, name))
        if not layer:
            continue
        entry = layers.setdefault(layer, {"period": period, "presents": [], "samples": 0})
        entry["presents"].extend(frames)
        entry["samples"] += 1
        if period and not entry["period"]:
            entry["period"] = period
    return layers


def report(folder):
    layers = collect(folder)
    if not layers:
        print(f"no samples in {folder}")
        return 1
    print(f"{'layer':58} {'fps':>5} {'p50ms':>6} {'p95ms':>6} {'maxms':>6} {'busy s':>6} {'idle s':>6} {'frames':>6}")
    empty = 0
    for layer, e in sorted(layers.items()):
        st = stats(e["presents"], e["period"])
        if st is None:
            empty += 1  # container layers (AppWindowToken, Background for ...) never present frames
            continue
        print(f"{layer[:58]:58} {st['fps']:5.1f} {st['p50_ms']:6.1f} {st['p95_ms']:6.1f} {st['max_ms']:6.1f}"
              f" {st['busy_s']:6.1f} {st['idle_s']:6.1f} {st['frames']:6d}")
    if empty:
        print(f"({empty} matching layers presented no frames: containers, or windows that never drew)")
    period = next((e["period"] for e in layers.values() if e["period"]), None)
    if period:
        print(f"display refresh: {period / 1e6:.2f} ms ({1e9 / period:.1f} Hz)")
    skipped = os.path.join(folder, "skipped")
    if os.path.exists(skipped):
        with open(skipped, encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()
        print(f"WARN: {len(lines)} swipes skipped, something else had the focus; first: {lines[0] if lines else ''}")
    done = os.path.join(folder, "DONE")
    if not os.path.exists(done):
        print("WARN: the probe had not finished when pulled")
    return 0


def selftest():
    """The stats must recover known rates, ignore pending frames and duplicates, and split idle time."""
    ms = 1_000_000
    t0 = 10**12
    f60 = [t0 + int(i * 16.667 * ms) for i in range(120)]           # 2 s at 60 fps
    f30 = [f60[-1] + 2000 * ms + i * 33 * ms for i in range(60)]     # 2 s idle, then 2 s at ~30 fps
    stall = [f30[-1] + 200 * ms]                                      # one 200 ms hitch
    frames = f60 + f30 + stall
    st = stats(frames + frames[:50] + [PENDING][:0], 16_667_000)     # duplicates must not count
    checks = [
        ("frame count", st["frames"] == 181),
        ("idle split", abs(st["idle_s"] - 2.0) < 0.05),
        ("busy fps between 30 and 60", 30 < st["fps"] < 60),
        ("max interval = the hitch", abs(st["max_ms"] - 200) < 1),
        ("p50 = the 60 fps interval", abs(st["p50_ms"] - 16.7) < 0.2),
    ]
    only60 = stats(f60, 16_667_000)
    checks.append(("pure 60 fps reads 60", abs(only60["fps"] - 60) < 0.5))
    only30 = stats(f30, 16_667_000)
    checks.append(("pure 30 fps reads ~30", abs(only30["fps"] - 30.3) < 0.5))
    # read_sample must drop INT64_MAX (pending) frames.
    import tempfile
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
        f.write("layer: X\n16666666\n1 100 1\n1 %d 1\n1 200 1\n" % PENDING)
    _, period, got = read_sample(f.name)
    os.unlink(f.name)
    checks.append(("pending frame dropped", got == [100, 200] and period == 16666666))
    bad = [n for n, ok in checks if not ok]
    for n, ok in checks:
        print(("ok   " if ok else "FAIL ") + n)
    return 1 if bad else 0


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        sys.exit(selftest())
    if len(sys.argv) != 2 or not os.path.isdir(sys.argv[1]):
        print(__doc__)
        sys.exit(2)
    sys.exit(report(sys.argv[1]))
