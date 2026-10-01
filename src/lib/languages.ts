export const LANGUAGES = [
  "English", "Hinglish", "Hindi", "Urdu", "Arabic", "Chinese", "Japanese", "Korean",
  "Russian", "Spanish", "Portuguese", "French", "German", "Swiss German", "Italian",
  "Turkish", "Dutch", "Swedish", "Norwegian", "Danish", "Polish", "Greek", "Indonesian",
  "Malay", "Bengali", "Punjabi", "Persian", "Thai", "Vietnamese", "Ukrainian",
] as const;

export const TONES = [
  "Natural", "Casual", "Gen Z", "Slang", "Match Original", "Friendly", "Flirty", "Formal",
] as const;

export const OUTPUTS = ["Native", "Romanized", "Both"] as const;

export type Language = (typeof LANGUAGES)[number];
export type Tone = (typeof TONES)[number];
export type OutputMode = (typeof OUTPUTS)[number];
