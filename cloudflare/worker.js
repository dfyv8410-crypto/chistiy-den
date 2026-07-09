// Cloudflare Worker — бесплатный прокси к Cloudflare Workers AI.
// Ключей не нужно: модель крутится на стороне Cloudflare (env.AI).
// Клиент (GitHub Pages / APK) звонит сюда, а не напрямую к API.

// Пробуем модели по очереди, пока не найдём доступную в аккаунте.
const MODELS = [
  '@cf/deepseek/deepseek-r1-distill-qwen-32b',
  '@cf/meta/llama-3.3-70b-instruct-fp8-fast',
  '@cf/meta/llama-3.1-8b-instruct',
  '@cf/deepseek/deepseek-v3-0324'
];

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
        let result, usedModel;
        for (const m of MODELS) {
          try {
            result = await env.AI.run(m, { messages });
            usedModel = m;
            break;
          } catch (err) {
            // если модель недоступна — пробуем следующую
            if (/No such model|5007/.test(err.message || '')) continue;
            throw err;
          }
        }
        if (!result) return json({ error: 'No available model in this account' }, 500);
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
