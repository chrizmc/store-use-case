// Calls a local Ollama instance for embeddings — no API key, no external account.
export async function embedText(text: string): Promise<number[]> {
  const res = await fetch('http://localhost:11434/api/embeddings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: 'nomic-embed-text', prompt: text }),
  });
  const data = (await res.json()) as { embedding: number[] };
  return data.embedding;
}
