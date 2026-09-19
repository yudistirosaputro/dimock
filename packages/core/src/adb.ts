import { execFile } from "node:child_process";
import { DimockError } from "./types.js";

export interface Device {
  serial: string;
  state: "device" | "offline" | "unauthorized" | "unknown";
  model?: string;
  product?: string;
  transportId?: string;
  /** Filled by `describe()` when reachable. */
  apiLevel?: number;
}

/** Runs one adb invocation and returns stdout. Injected in tests. */
export type Runner = (args: string[], options?: { timeoutMs?: number }) => Promise<string>;

export const defaultRunner: Runner = (args, options) =>
  new Promise((resolvePromise, reject) => {
    const bin = process.env.ADB ?? (process.env.ANDROID_HOME ? `${process.env.ANDROID_HOME}/platform-tools/adb` : "adb");
    execFile(bin, args, { timeout: options?.timeoutMs ?? 15_000, maxBuffer: 16 * 1024 * 1024 }, (err, stdout, stderr) => {
      if (err) {
        const code = (err as NodeJS.ErrnoException).code;
        if (code === "ENOENT") {
          return reject(
            new DimockError(
              "adb not found. Install Android platform-tools and put adb on PATH, or set ANDROID_HOME (or ADB=/path/to/adb).",
              "adb_missing",
            ),
          );
        }
        return reject(new DimockError(`adb ${args.join(" ")} failed: ${stderr.trim() || err.message}`, "adb_failed"));
      }
      resolvePromise(stdout);
    });
  });

/** Small typed wrapper over the adb calls dimock needs. */
export class Adb {
  constructor(private readonly run: Runner = defaultRunner) {}

  async devices(): Promise<Device[]> {
    const out = await this.run(["devices", "-l"]);
    return parseDevices(out);
  }

  async describe(device: Device): Promise<Device> {
    if (device.state !== "device") return device;
    const api = await this.shell(device.serial, ["getprop", "ro.build.version.sdk"]).catch(() => "");
    const model = device.model ?? (await this.shell(device.serial, ["getprop", "ro.product.model"]).catch(() => ""))?.trim();
    return { ...device, apiLevel: Number.parseInt(api.trim(), 10) || undefined, model: model || device.model };
  }

  shell(serial: string, cmd: string[], timeoutMs?: number): Promise<string> {
    return this.run(["-s", serial, "shell", ...cmd], { timeoutMs });
  }

  /** `adb forward tcp:<local> tcp:<remote>`; local 0 lets adb pick and returns the chosen port. */
  async forward(serial: string, remotePort: number, localPort = 0): Promise<number> {
    const out = await this.run(["-s", serial, "forward", `tcp:${localPort}`, `tcp:${remotePort}`]);
    if (localPort !== 0) return localPort;
    const picked = Number.parseInt(out.trim(), 10);
    if (Number.isNaN(picked)) {
      const list = await this.run(["-s", serial, "forward", "--list"]);
      const line = list.split("\n").find((l) => l.includes(`tcp:${remotePort}`) && l.startsWith(serial));
      const m = line?.match(/tcp:(\d+)\s+tcp:/);
      if (!m) throw new DimockError(`could not determine the local port adb picked for ${serial}:${remotePort}`, "adb_failed");
      return Number.parseInt(m[1]!, 10);
    }
    return picked;
  }

  async removeForward(serial: string, localPort: number): Promise<void> {
    await this.run(["-s", serial, "forward", "--remove", `tcp:${localPort}`]).catch(() => undefined);
  }

  async pidOf(serial: string, packageName: string): Promise<number | undefined> {
    const out = await this.shell(serial, ["pidof", "-s", packageName]).catch(() => "");
    const pid = Number.parseInt(out.trim(), 10);
    return Number.isNaN(pid) ? undefined : pid;
  }

  /** Last `lines` logcat lines for one app (by pid), optionally filtered by a regex. */
  async logcatTail(serial: string, packageName: string, options: { pattern?: string; lines?: number } = {}): Promise<string[]> {
    const pid = await this.pidOf(serial, packageName);
    if (!pid) throw new DimockError(`${packageName} is not running on ${serial}`, "app_not_running");
    const lines = options.lines ?? 200;
    const raw = await this.run(["-s", serial, "logcat", "-d", "--pid", String(pid), "-t", String(Math.max(lines * 4, 400)), "-v", "time"], { timeoutMs: 20_000 });
    let out = raw.split("\n").filter((l) => l.trim() !== "" && !l.startsWith("--------- beginning"));
    if (options.pattern) {
      const re = new RegExp(options.pattern, "i");
      out = out.filter((l) => re.test(l));
    }
    return out.slice(-lines);
  }

  async installedPackages(serial: string, filter?: string): Promise<string[]> {
    const out = await this.shell(serial, ["pm", "list", "packages", "-3"]);
    return out
      .split("\n")
      .map((l) => l.trim().replace(/^package:/, ""))
      .filter((p) => p && (!filter || p.includes(filter)))
      .sort();
  }
}

export function parseDevices(output: string): Device[] {
  return output
    .split("\n")
    .slice(1) // "List of devices attached"
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("*"))
    .map((line) => {
      const [serial, state, ...rest] = line.split(/\s+/);
      const props = Object.fromEntries(rest.map((kv) => kv.split(":") as [string, string]));
      const s = (["device", "offline", "unauthorized"].includes(state ?? "") ? state : "unknown") as Device["state"];
      return {
        serial: serial!,
        state: s,
        model: props.model?.replace(/_/g, " "),
        product: props.product,
        transportId: props.transport_id,
      };
    });
}
