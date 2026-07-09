// Cloudflare Worker — бесплатный прокси к Cloudflare Workers AI.
// Ключей не нужно: модель крутится на стороне Cloudflare (env.AI).
// Клиент (GitHub Pages / APK) звонит сюда, а не напрямую к API.

const MODEL = '@cf/deepseek/deepseek-r1-distill-qwen-32b';
// Альтернативы (бесплатно, на выбор):
//   '@cf/meta/llama-3.1-8b-instruct'  — быстрее, без рассуждений
//   '@cf/meta/llama-3.3-70b-instruct' — качественнее (может требовать план)

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  };
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...corsHeaders() }
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders() });
    }

    if (request.method === 'POST' && url.pathname === '/chat') {
      let body;
      try {
        body = await request.json();
      } catch (e) {
        return json({ error: 'Invalid JSON' }, 400);
      }

      const messages = Array.isArray(body.messages) ? body.messages : [];
      if (!messages.length) return json({ error: 'No messages' }, 400);

      try {
        const result = await env.AI.run(MODEL, { messages });
        let text = (result && (result.response || result.content || '')) + '';
        // убираем теги рассуждений <think>...</think>, если модель их выдаёт
        text = text.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
        if (!text) text = '...';
        return json({ choices: [{ message: { role: 'assistant', content: text } }] });
      } catch (e) {
        return json({ error: e.message || 'Workers AI error' }, 500);
      }
    }

    return json({ error: 'Not found' }, 404);
  }
};
