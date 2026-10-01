import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { ArrowLeftRight, Copy, Check, X, Loader2 } from "lucide-react";
import { LANGUAGES, OUTPUTS, TONES } from "@/lib/languages";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Float Translate — natural chat translator" },
      { name: "description", content: "Personal floating translator for Android. Hinglish, slang and 30 languages, translated the way people actually text." },
      { property: "og:title", content: "Float Translate — natural chat translator" },
      { property: "og:description", content: "Hinglish, slang and 30 languages, translated the way people actually text." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: Index,
});

function Select({ label, value, options, onChange }: { label: string; value: string; options: readonly string[]; onChange: (v: string) => void }) {
  return (
    <label className="flex flex-1 flex-col gap-1 rounded-2xl bg-secondary px-4 py-2.5">
      <span className="text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)} className="bg-transparent text-[15px] font-medium text-foreground outline-none">
        {options.map((o) => (
          <option key={o} value={o} className="bg-card">{o}</option>
        ))}
      </select>
    </label>
  );
}

function Index() {
  const [from, setFrom] = useState("Hinglish");
  const [to, setTo] = useState("English");
  const [tone, setTone] = useState("Natural");
  const [output, setOutput] = useState("Native");
  const [text, setText] = useState("");
  const [result, setResult] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  async function translate() {
    if (!text.trim() || loading) return;
    setLoading(true);
    setError("");
    try {
      const r = await fetch("/api/public/translate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, source: from, target: to, tone, output }),
      });
      const j = (await r.json().catch(() => ({}))) as { translation?: string; error?: string };
      if (!r.ok || !j.translation) throw new Error(j.error ?? "Something went wrong.");
      setResult(j.translation);
    } catch (e) {
      setResult("");
      setError(e instanceof TypeError ? "No connection. Check your internet." : (e as Error).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="min-h-screen bg-background px-4 py-10 text-foreground">
      <div className="mx-auto max-w-lg">
        <h1 className="text-2xl font-semibold tracking-tight">Float Translate</h1>
        <p className="mt-1 text-sm text-muted-foreground">Web preview of the Android floating translator.</p>

        <section className="mt-6 rounded-[28px] bg-card p-5 shadow-[var(--shadow-sheet)]">
          <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-muted" />
          <div className="flex items-center gap-2">
            <Select label="From" value={from} options={LANGUAGES} onChange={setFrom} />
            <button aria-label="Swap languages" onClick={() => { setFrom(to); setTo(from); }} className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-secondary text-primary">
              <ArrowLeftRight className="h-4 w-4" />
            </button>
            <Select label="To" value={to} options={LANGUAGES} onChange={setTo} />
          </div>

          <div className="mt-3 rounded-2xl bg-secondary p-4">
            <textarea value={text} maxLength={2000} onChange={(e) => setText(e.target.value)} rows={3} placeholder="Type or paste your message..." className="w-full resize-none bg-transparent text-[15px] outline-none placeholder:text-muted-foreground" />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{text.length}/2000</span>
              {text && (
                <button onClick={() => { setText(""); setResult(""); setError(""); }} className="flex items-center gap-1 hover:text-foreground"><X className="h-3 w-3" />Clear</button>
              )}
            </div>
          </div>

          <div className="mt-3 flex gap-2">
            <Select label="Tone" value={tone} options={TONES} onChange={setTone} />
            <Select label="Output" value={output} options={OUTPUTS} onChange={setOutput} />
          </div>

          <button onClick={translate} disabled={!text.trim() || loading} className="mt-3 flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-primary font-semibold text-primary-foreground transition disabled:opacity-40">
            {loading && <Loader2 className="h-4 w-4 animate-spin" />}
            {loading ? "Translating" : "Translate"}
          </button>

          {error && <p className="mt-3 rounded-2xl bg-destructive/15 px-4 py-3 text-sm text-destructive">{error}</p>}
          {result && (
            <div className="mt-3 rounded-2xl border border-border bg-secondary/60 p-4">
              <span className="text-[10px] font-semibold uppercase tracking-widest text-primary">Translation</span>
              <p className="mt-1 whitespace-pre-wrap text-[16px] leading-relaxed">{result}</p>
              <button onClick={async () => { await navigator.clipboard.writeText(result); setCopied(true); setTimeout(() => setCopied(false), 1500); }} className="mt-3 flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-primary/15 text-sm font-semibold text-primary">
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                {copied ? "Copied" : "Copy"}
              </button>
            </div>
          )}
        </section>
        <p className="mt-6 text-center text-xs text-muted-foreground">The Android app is built from the repository's GitHub Actions workflow.</p>
      </div>
    </main>
  );
}
