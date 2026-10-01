import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";
import { LANGUAGES, OUTPUTS, TONES } from "@/lib/languages";
import { GatewayError, buildPrompt, translateText } from "@/lib/translate.server";

const Body = z.object({
  text: z.string().trim().min(1).max(2000),
  source: z.enum(LANGUAGES),
  target: z.enum(LANGUAGES),
  tone: z.enum(TONES).default("Natural"),
  output: z.enum(OUTPUTS).default("Native"),
});

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...cors },
  });
}

export const Route = createFileRoute("/api/public/translate")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: cors }),
      POST: async ({ request }) => {
        let raw: unknown;
        try {
          raw = await request.json();
        } catch {
          return json({ error: "Invalid request." }, 400);
        }
        const parsed = Body.safeParse(raw);
        if (!parsed.success) {
          return json({ error: "Enter a message (max 2000 characters) and pick valid languages." }, 400);
        }
        const apiKey = process.env["LOVABLE_API_KEY"];
        if (!apiKey) return json({ error: "Translation service is not configured." }, 500);
        try {
          const translation = await translateText(apiKey, buildPrompt(parsed.data), request.signal);
          return json({ translation });
        } catch (e) {
          if (request.signal.aborted) return new Response(null, { status: 499 });
          if (e instanceof GatewayError) return json({ error: e.message }, e.status);
          console.error(e);
          return json({ error: "Something went wrong. Try again." }, 500);
        }
      },
    },
  },
});
