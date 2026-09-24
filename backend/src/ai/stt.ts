import { spawn } from 'node:child_process';

// Shells out to a local whisper.cpp binary. Homebrew's whisper.cpp does not bundle a
// model; ggml-base.en.bin was downloaded separately into backend/models/whisper/.
const WHISPER_MODEL_PATH = process.env.WHISPER_MODEL_PATH ?? 'models/whisper/ggml-base.en.bin';

export function transcribeAudio(wavFilePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const proc = spawn('whisper-cli', [
      '-m',
      WHISPER_MODEL_PATH,
      '-f',
      wavFilePath,
      '--no-timestamps',
    ]);
    let output = '';
    proc.stdout.on('data', (chunk) => (output += chunk.toString()));
    proc.on('close', (code) =>
      code === 0 ? resolve(output.trim()) : reject(new Error(`whisper exited with ${code}`))
    );
  });
}
