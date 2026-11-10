export const DEFAULT_TEXT_MODELS = [
  'meta-llama/llama-3.3-70b-instruct:free',
  'deepseek/deepseek-chat-v3-0324:free',
  'google/gemma-3-27b-it:free',
];

export const DEFAULT_VISION_MODELS = [
  'google/gemma-3-27b-it:free',
  'mistralai/mistral-small-3.1-24b-instruct:free',
];

export async function askN8nWebhook(webhookUrl: string, prompt: string): Promise<string> {
  const res = await fetch(webhookUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      perintah: prompt,
      sumber: 'kobra-os',
      waktu: new Date().toISOString(),
    }),
  });

  if (!res.ok) {
    throw new Error(`n8n error: status ${res.status}`);
  }

  const data = await res.json();
  if (data && typeof data.balasan === 'string') return data.balasan;
  if (data && typeof data.message === 'string') return data.message;
  return 'Otomatisasi n8n selesai diproses.';
}

export async function askOpenRouterAi(
  apiKey: string,
  userPrompt: string,
  model?: string,
  imageBase64?: string
): Promise<string> {
  const chosenModel = model || DEFAULT_TEXT_MODELS[0];
  const systemPrompt =
    'Anda adalah Kobra, asisten suara dan mata kedua untuk tunanetra di Indonesia. ' +
    'Jawab dalam Bahasa Indonesia yang singkat, ramah, dan jelas (maksimal 2-3 kalimat). ' +
    'JANGAN gunakan markdown, asteris bintang, tanda pagar, atau daftar bernomor karena jawaban Anda akan dibacakan langsung oleh suara TTS.';

  const messages: any[] = [{ role: 'system', content: systemPrompt }];

  if (imageBase64) {
    messages.push({
      role: 'user',
      content: [
        { type: 'text', text: userPrompt || 'Deskripsikan gambar ini untuk orang tunanetra secara singkat.' },
        {
          type: 'image_url',
          image_url: {
            url: imageBase64.startsWith('data:') ? imageBase64 : `data:image/jpeg;base64,${imageBase64}`,
          },
        },
      ],
    });
  } else {
    messages.push({ role: 'user', content: userPrompt });
  }

  const res = await fetch('https://openrouter.ai/api/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
      'HTTP-Referer': 'https://kobra-os.bantu',
      'X-Title': 'Kobra OS Asisten Bantu',
    },
    body: JSON.stringify({
      model: chosenModel,
      messages,
      max_tokens: 150,
      temperature: 0.7,
    }),
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`OpenRouter gagal (${res.status}): ${errText}`);
  }

  const data = await res.json();
  const reply = data.choices?.[0]?.message?.content;
  if (!reply) {
    throw new Error('Tidak ada respon dari model AI');
  }

  return reply.replace(/[*#_`~[\]]/g, '').trim();
}
