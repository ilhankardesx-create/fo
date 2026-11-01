from pathlib import Path

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

s = s.replace(
    '"⚡ Hızlı · Qwen3 1.7B · ~0,9 GB\nDaha az RAM, daha hızlı cevap",',
    '"⚡ Hızlı · Qwen3 1.7B · ~0,9 GB\\nDaha az RAM, daha hızlı cevap",'
)
s = s.replace(
    '"🧠 Güçlü · Qwen3 4B · ~2,5 GB\nDaha iyi cevaplar, daha fazla RAM"',
    '"🧠 Güçlü · Qwen3 4B · ~2,5 GB\\nDaha iyi cevaplar, daha fazla RAM"'
)
s = s.replace(
    'bellek yetersiz kalabilir.\n\n" +',
    'bellek yetersiz kalabilir.\\n\\n" +'
)

p.write_text(s)
print("0.7.1 Kotlin newline compile fix applied")
