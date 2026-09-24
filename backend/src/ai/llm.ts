// Local Ollama generation, combining structured graph facts with the curated substitutes table (RAG).
export async function generateAnswer(question: string, context: Record<string, unknown>): Promise<string> {
  const prompt = `Answer the store associate's question using only the context below. "substitutes" is
the store's confirmed list of accepted alternatives for "likelyProduct" - if it is empty, say
there is no known alternative rather than guessing one.\n\nQuestion: ${question}\n\nContext: ${JSON.stringify(
    context
  )}\n\nAnswer concisely:`;

  const res = await fetch('http://localhost:11434/api/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: 'llama3.2', prompt, stream: false }),
  });
  const data = (await res.json()) as { response: string };
  return data.response;
}
