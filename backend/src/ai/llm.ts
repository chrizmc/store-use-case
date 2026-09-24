// Local Ollama generation, combining structured graph facts with vector similarity hits (RAG).
export async function generateAnswer(question: string, context: Record<string, unknown>): Promise<string> {
  const prompt = `Answer the store associate's question using only the context below.\n\nQuestion: ${question}\n\nContext: ${JSON.stringify(
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
