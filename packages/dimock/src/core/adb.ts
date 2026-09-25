import { execFile } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { posix, win32 } from "node:path";
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

export interface AdbLookup {
  env: Record<string, string | undefined>;
  cwd: string;
  platform: NodeJS.Platform;
  home: string;
  exists: (path: string) => boolean;
  readText: (path: string) => string | undefined;
}

/**
 * Where adb lives, in order: `ADB`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `PATH`, `sdk.dir` in ./local.properties,
 * then Android Studio's default SDK for the platform. Throws `adb_missing` naming every place it looked.
 */
export function locateAdb(l: AdbLookup): string {
  if (l.env.ADB) return l.env.ADB;
  const win = l.platform === "win32";
  const p = win ? win32 : posix;
  const exe = win ? "adb.exe" : "adb";
  const fromSdk = (sdk: string) => p.join(sdk, "platform-tools", exe);
  // Windows env vars are case-insensitive (`Path`, `LocalAppData`).
  const env = (name: string) => l.env[name] ?? (win ? Object.entries(l.env).find(([k]) => k.toUpperCase() === name)?.[1] : undefined);

  const tried: string[] = [];
  const candidates: string[] = [];
  for (const name of ["ANDROID_HOME", "ANDROID_SDK_ROOT"]) {
    const sdk = env(name);
    if (sdk) candidates.push(fromSdk(sdk));
  }
  for (const dir of (env("PATH") ?? "").split(win ? ";" : ":").filter(Boolean)) candidates.push(p.join(dir, exe));
  for (const path of candidates) {
    if (l.exists(path)) return path;
    tried.push(path);
  }

  const properties = p.join(l.cwd, "local.properties");
  const sdkDir = sdkDirFrom(l.readText(properties));
  tried.push(sdkDir ? `${properties} (sdk.dir=${sdkDir})` : properties);
  if (sdkDir && l.exists(fromSdk(sdkDir))) return fromSdk(sdkDir);

  const localAppData = env("LOCALAPPDATA");
  const fallback = win
    ? fromSdk(p.join(localAppData ?? p.join(l.home, "AppData", "Local"), "Android", "Sdk"))
    : fromSdk(l.platform === "darwin" ? p.join(l.home, "Library", "Android", "sdk") : p.join(l.home, "Android", "Sdk"));
  if (l.exists(fallback)) return fallback;
  tried.push(fallback);

  throw new DimockError(
    `adb not found. Looked in:\n${tried.map((t) => `  ${t}`).join("\n")}\nInstall Android platform-tools, put adb on PATH, set ANDROID_HOME, or set ADB=/path/to/adb.`,
    "adb_missing",
  );
}

/** `sdk.dir` from a local.properties text, with Java properties escaping (`C\:\\Users`) undone. */
function sdkDirFrom(text: string | undefined): string | undefined {
  const line = text?.split(/\r?\n/).find((l) => /^\s*sdk\.dir\s*[=:]/.test(l));
  if (!line) return undefined;
  return line.replace(/^\s*sdk\.dir\s*[=:]\s*/, "").trim().replace(/\\(.)/g, "$1") || undefined;
}

let adbPath: string | undefined;

export const defaultRunner: Runner = (args, options) =>
  new Promise((resolvePromise, reject) => {
    try {
      adbPath ??= locateAdb({
        env: process.env,
        cwd: process.cwd(),
        platform: process.platform,
        home: homedir(),
        exists: (path) => existsSync(path),
        readText: (path) => (existsSync(path) ? readFileSync(path, "utf8") : undefined),
      });
    } catch (e) {
      return reject(e);
    }
    execFile(adbPath, args, { timeout: options?.timeoutMs ?? 15_000, maxBuffer: 16 * 1024 * 1024 }, (err, stdout, stderr) => {
      if (err) {
        const code = (err as NodeJS.ErrnoException).code;
        if (code === "ENOENT") {
          return reject(new DimockError(`adb not found at ${adbPath}. Fix ADB=/path/to/adb, or unset it to search PATH and the Android SDK.`, "adb_missing"));
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
