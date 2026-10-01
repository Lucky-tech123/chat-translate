import type { Language, OutputMode, Tone } from "./languages";

const ROMANIZATION: Partial<Record<Language, string>> = {
  Chinese: "Hanyu Pinyin with tone marks (e.g. Nǐ hǎo)",
  Japanese: "modified Hepburn (e.g. Konnichiwa)",
  Korean: "Revised Romanization of Korean (e.g. Annyeonghaseyo)",
  Hindi: "readable Roman Hindi as people type it in chats",
  Urdu: "readable Roman Urdu as people type it in chats",
  Punjabi: "readable Roman Punjabi as people type it in chats",
  Bengali: "readable standard Bengali romanization",
  Arabic: "readable standard Arabic romanization (no numerals-as-letters)",
  Persian: "readable standard Persian (Finglish-free) romanization",
  Russian: "BGN/PCGN romanization",
  Ukrainian: "Ukrainian national romanization (2010)",
  Greek: "ELOT 743 romanization",
  Thai: "Royal Thai General System of Transcription (RTGS)",
};

const TONE_GUIDE: Record<Tone, string> = {
  Natural: "Sound like a real person naturally typing in a chat. Keep the original's level of informality.",
  Casual: "Relaxed, everyday chat style. Contractions are fine.",
  "Gen Z": "Modern Gen Z texting style, but only use slang/abbreviations where they fit naturally. Never overdo it.",
  Slang: "Informal and slangy where it fits naturally for the target language. Don't force it into every phrase.",
  "Match Original": "Mirror the original's exact register, slang, abbreviations, punctuation style, and energy.",
  Friendly: "Warm and friendly, still natural.",
  Flirty: "Light, playful warmth — but never add flirting, compliments, or meaning that isn't implied by the original.",
  Formal: "Polite, formal, grammatically clean.",
};

export function buildPrompt(p: {
  text: string;
  source: Language;
  target: Language;
  tone: Tone;
  output: OutputMode;
}) {
  const rom = ROMANIZATION[p.target];
  let outputRule: string;
  if (p.target === "Hinglish") {
    outputRule = "Hinglish is Hindi/Urdu mixed with English, written ONLY in Roman (Latin) script, the way people type on WhatsApp.";
  } else if (!rom) {
    outputRule = "The target uses Latin script; output normal text.";
  } else if (p.output === "Native") {
    outputRule = `Output in the native script of ${p.target} only.`;
  } else if (p.output === "Romanized") {
    outputRule = `Output ONLY the romanized form using ${rom}. No native script.`;
  } else {
    outputRule = `Output two lines: first line in native ${p.target} script, second line the same text romanized using ${rom}. Nothing else.`;
  }

  const instructions = [
    "You are a chat-message translator. You translate like a bilingual friend, not a dictionary.",
    "Preserve meaning, context, tone, emotion, informality, slang, abbreviations (lol, ngl, u, tbh), emojis and swearing. Do not sanitize profanity.",
    "Never invent additional meaning, emotions, flirting, insults or context. Never explain. Never add quotes, notes, labels or alternatives.",
    "Hinglish/Roman Urdu/Roman Hindi input is written in Latin script; understand it fully (e.g. 'nhi' = nahi, 'kyun' = why, 'yaar' ≈ bro/dude).",
    `Source language: ${p.source === "English" || p.source === "Hinglish" ? p.source : p.source} (if the text is clearly another language, translate it anyway).`,
    `Target language: ${p.target}${p.target === "Swiss German" ? " (Schweizerdeutsch dialect spelling, not Standard German)" : ""}.`,
    `Tone: ${p.tone}. ${TONE_GUIDE[p.tone]}`,
    outputRule,
    "Reply with only the translated message text.",
  ].join("\n");

  return { instructions, input: p.text };
}

export class GatewayError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export async function translateText(apiKey: string, prompt: { instructions: string; input: string }, signal?: AbortSignal) {
  const res = await fetch("https://ai.gateway.lovable.dev/v1/responses", {
    method: "POST",
    signal: signal ?? null,
    headers: {
      "Content-Type": "application/json",
      "Lovable-API-Key": apiKey,
      "X-Lovable-AIG-SDK": "fetch",
    },
    body: JSON.stringify({
      model: "openai/gpt-6-astra",
      instructions: prompt.instructions,
      input: prompt.input,
      stream: true,
      store: false,
      reasoning: { effort: "low", summary: "auto" },
      include: ["reasoning.encrypted_content"],
    }),
  });

  if (!res.ok || !res.body) {
    let msg = "";
    try {
      const j = (await res.json()) as { message?: string; error?: { message?: string } };
      msg = j.message ?? j.error?.message ?? "";
    } catch {
      /* ignore */
    }
    if (res.status === 429) throw new GatewayError(429, "Too many requests. Wait a moment and try again.");
    if (res.status === 402) throw new GatewayError(402, msg || "AI credits are used up for this workspace.");
    if (res.status === 403) throw new GatewayError(403, msg || "Translation is currently blocked for this workspace.");
    throw new GatewayError(502, "Translation service is unavailable. Try again shortly.");
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = "";
  let out = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buf.indexOf("\n")) >= 0) {
      const line = buf.slice(0, idx).trim();
      buf = buf.slice(idx + 1);
      if (!line.startsWith("data:")) continue;
      const data = line.slice(5).trim();
      if (!data || data === "[DONE]") continue;
      let evt: { type?: string; delta?: string; message?: string };
      try {
        evt = JSON.parse(data);
      } catch {
        continue;
      }
      if (evt.type === "response.output_text.delta" && evt.delta) out += evt.delta;
      else if (evt.type === "error" || evt.type === "response.failed") {
        throw new GatewayError(502, "Translation failed. Try again.");
      }
    }
  }

  const clean = out.trim().replace(/^["“](.*)["”]$/s, "$1").trim();
  if (!clean) throw new GatewayError(502, "Got an empty translation. Try again.");
  return clean;
}
