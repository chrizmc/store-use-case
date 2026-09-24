import { spawn } from 'node:child_process';

// Piper's Homebrew formula is unreliable; installed via `pip install piper-tts` instead,
// which requires invoking it as a Python module with a local .onnx voice model
// (see README: `python3 -m piper.download_voices`).
const PIPER_PYTHON = process.env.PIPER_PYTHON ?? '/usr/bin/python3';
const PIPER_MODEL_PATH = process.env.PIPER_MODEL_PATH ?? 'models/en_US-lessac-medium.onnx';

export function synthesizeSpeech(text: string, outFilePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const proc = spawn(PIPER_PYTHON, ['-m', 'piper', '--model', PIPER_MODEL_PATH, '--output-file', outFilePath]);
    proc.stdin.write(text);
    proc.stdin.end();
    proc.on('close', (code) =>
      code === 0 ? resolve(outFilePath) : reject(new Error(`piper exited with ${code}`))
    );
  });
}
