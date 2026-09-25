import assert from "node:assert/strict";
import { test } from "node:test";
import { locateAdb } from "../adb.js";
import { DimockError } from "../types.js";

/** A fake filesystem: `files` maps a path to its text (adb binaries map to ""). */
const fs = (files: Record<string, string>) => ({
  exists: (p: string) => p in files,
  readText: (p: string) => files[p],
});

test("locateAdb: an explicit ADB wins, used as given", () => {
  const r = locateAdb({ env: { ADB: "/opt/adb", ANDROID_HOME: "/sdk" }, cwd: "/app", platform: "linux", home: "/home/me", ...fs({ "/sdk/platform-tools/adb": "" }) });
  assert.equal(r, "/opt/adb");
});

test("locateAdb: ANDROID_HOME, then ANDROID_SDK_ROOT, skipping ones without adb", () => {
  const env = { ANDROID_HOME: "/missing", ANDROID_SDK_ROOT: "/sdk" };
  assert.equal(locateAdb({ env, cwd: "/app", platform: "linux", home: "/home/me", ...fs({ "/sdk/platform-tools/adb": "" }) }), "/sdk/platform-tools/adb");
});

test("locateAdb: adb on PATH comes before local.properties and the default SDK", () => {
  const r = locateAdb({
    env: { PATH: "/usr/bin:/usr/local/bin" },
    cwd: "/app",
    platform: "darwin",
    home: "/Users/me",
    ...fs({ "/usr/local/bin/adb": "", "/Users/me/Library/Android/sdk/platform-tools/adb": "" }),
  });
  assert.equal(r, "/usr/local/bin/adb");
});

test("locateAdb: sdk.dir in ./local.properties, with Windows escaping undone", () => {
  const r = locateAdb({
    env: { Path: "C:\\Windows" },
    cwd: "C:\\src\\app",
    platform: "win32",
    home: "C:\\Users\\me",
    ...fs({
      "C:\\src\\app\\local.properties": "## generated\nsdk.dir=C\\:\\\\Users\\\\me\\\\Sdk\n",
      "C:\\Users\\me\\Sdk\\platform-tools\\adb.exe": "",
    }),
  });
  assert.equal(r, "C:\\Users\\me\\Sdk\\platform-tools\\adb.exe");
});

test("locateAdb: Android Studio's default SDK per platform", () => {
  assert.equal(
    locateAdb({ env: {}, cwd: "/app", platform: "darwin", home: "/Users/me", ...fs({ "/Users/me/Library/Android/sdk/platform-tools/adb": "" }) }),
    "/Users/me/Library/Android/sdk/platform-tools/adb",
  );
  assert.equal(
    locateAdb({ env: {}, cwd: "/app", platform: "linux", home: "/home/me", ...fs({ "/home/me/Android/Sdk/platform-tools/adb": "" }) }),
    "/home/me/Android/Sdk/platform-tools/adb",
  );
  assert.equal(
    locateAdb({
      env: { LOCALAPPDATA: "C:\\Users\\me\\AppData\\Local" },
      cwd: "C:\\app",
      platform: "win32",
      home: "C:\\Users\\me",
      ...fs({ "C:\\Users\\me\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe": "" }),
    }),
    "C:\\Users\\me\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
  );
});

test("locateAdb: when nothing is found the error lists every place it looked", () => {
  assert.throws(
    () => locateAdb({ env: { ANDROID_HOME: "/nope", PATH: "/usr/bin" }, cwd: "/app", platform: "darwin", home: "/Users/me", ...fs({}) }),
    (e: unknown) =>
      e instanceof DimockError &&
      e.code === "adb_missing" &&
      e.message.includes("/nope/platform-tools/adb") &&
      e.message.includes("/usr/bin/adb") &&
      e.message.includes("/app/local.properties") &&
      e.message.includes("/Users/me/Library/Android/sdk/platform-tools/adb") &&
      /ADB=\/path\/to\/adb/.test(e.message),
  );
});
