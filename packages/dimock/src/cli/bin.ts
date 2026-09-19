#!/usr/bin/env node
import { buildProgram } from "./cli.js";

buildProgram().parseAsync(process.argv).catch((e) => {
  console.error(`dimock: ${(e as Error).message}`);
  process.exitCode = 1;
});
