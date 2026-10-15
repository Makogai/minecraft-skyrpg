#!/usr/bin/env python3
"""Makes the brainrots' voice lines: brainrots/src/main/resources/assets/skyrpg_brainrots/sounds/voice/<kind>.ogg,
plus sounds.json.

    python tools/art/make_brainrot_voices.py [--only kind,kind] [--tts] [--voice NAME]

Each kind's recording in tools/art/brainrots/voices/<kind>.(mp3|wav|ogg|m4a) is used when there is one (the real
voice lines, supplied by the owners): leading silence trimmed, loudness evened out across all of them (aimed at a
-16 dB average, peaks kept under -1 dB) and written as mono Ogg Vorbis (Minecraft only plays Ogg, and plays mono
sounds positionally, from the brainrot).

A kind without a recording (or every kind with --tts) is spoken by Windows speech (System.Speech) instead: an Italian
voice if one is installed, else Microsoft David reading Italian-ish phonetic spellings, then lowered a little with a
short echo. Needs ffmpeg on the PATH. Nothing is downloaded.
"""
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "brainrots/src/main/resources/assets/skyrpg_brainrots"
OUT = ASSETS / "sounds/voice"
RECORDINGS = ROOT / "tools/art/brainrots/voices"
TARGET_MEAN = -16.0
TARGET_PEAK = -1.0

# kind: (what an Italian voice reads, what an English voice reads), in album order.
LINES = {
    "tung_tung_sahur": ("Tung tung tung tung tung tung tung tung tung, sahur!", "Tung tung tung tung tung tung tung tung tung, sah hoor!"),
    "tralalero_tralala": ("Tralalero tralala!", "Trah lah leh roh, trah lah lah!"),
    "bombardiro_crocodilo": ("Bombardiro crocodilo!", "Bom bar dee roh, crow coh dee loh!"),
    "ballerina_cappuccina": ("Ballerina cappuccina, mi mi mi!", "Bah leh ree nah, cap poo chee nah! Mee mee mee!"),
    "brr_brr_patapim": ("Brr brr patapim!", "Brrrrr. Brrrrr. Pah tah peem!"),
    "lirili_larila": ("Lirilì larilà!", "Lee ree lee, lah ree lah!"),
    "chimpanzini_bananini": ("Chimpanzini bananini!", "Chim pahn zee nee, bah nah nee nee!"),
    "bombombini_gusini": ("Bombombini gusini!", "Bom bom bee nee, goo zee nee!"),
    "trippi_troppi": ("Trippi troppi, troppa trippa!", "Trip pee, trop pee, trop pah trip pah!"),
    "cappuccino_assassino": ("Cappuccino assassino!", "Cap poo chee noh, ah sah see noh!"),
    "frigo_camelo": ("Frigo camelo!", "Free goh, cah meh loh!"),
    "la_vaca_saturno": ("La vaca saturno saturnita!", "Lah vah cah, sah toor noh, sah toor nee tah!"),
    "boneca_ambalabu": ("Boneca ambalabu!", "Boh neh cah, ahm bah lah boo!"),
    "glorbo_fruttodrillo": ("Glorbo fruttodrillo!", "Glor boh, froot toh dreel loh!"),
    "burbaloni_luliloli": ("Burbaloni luliloli!", "Boor bah loh nee, loo lee loh lee!"),
}

PS = r"""
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$want = $env:BR_VOICE
$voices = $s.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo }
$v = $null
if ($want) { $v = $voices | Where-Object { $_.Name -eq $want } | Select-Object -First 1 }
if (-not $v) { $v = $voices | Where-Object { $_.Culture.Name -like 'it-*' } | Select-Object -First 1 }
if (-not $v) { $v = $voices | Where-Object { $_.Name -like '*David*' } | Select-Object -First 1 }
if (-not $v) { $v = $voices | Select-Object -First 1 }
$s.SelectVoice($v.Name)
Write-Output ("VOICE " + $v.Name + " " + $v.Culture.Name)
$s.Rate = -1
foreach ($line in Get-Content -Encoding UTF8 $env:BR_LIST) {
  $parts = $line -split "`t"
  $s.SetOutputToWaveFile($parts[0])
  $text = if ($v.Culture.Name -like 'it-*') { $parts[1] } else { $parts[2] }
  $s.Speak($text)
}
$s.SetOutputToNull()
"""

# Lower a touch, a short slap echo and a longer tail, then loud and even.
FILTER = ("asetrate=22050*0.93,aresample=44100,atempo=1.04,highpass=f=70,"
          "aecho=0.85:0.55:45|110:0.28|0.14,loudnorm=I=-13:TP=-1.5:LRA=7")


def recording(kind):
    for ext in ("mp3", "wav", "ogg", "m4a"):
        path = RECORDINGS / f"{kind}.{ext}"
        if path.exists():
            return path
    return None


def levels(path, pre=""):
    """(mean, peak) volume in dB, after the filter chain ``pre``."""
    args = ["ffmpeg", "-hide_banner", "-i", str(path), "-af", (pre + "," if pre else "") + "volumedetect", "-f", "null", "-"]
    out = subprocess.run(args, capture_output=True, text=True).stderr
    mean = float(re.search(r"mean_volume: (-?[\d.]+) dB", out).group(1))
    peak = float(re.search(r"max_volume: (-?[\d.]+) dB", out).group(1))
    return mean, peak


def convert(path, kind):
    """
    A supplied recording -> mono Ogg: leading silence off, gain to the shared loudness (measured after the mono
    downmix, which adds about 3 dB to a stereo clip), a limiter for the odd peak. Audio only: MP3s often carry cover
    art, which would otherwise become a video stream in the Ogg.
    """
    pre = "aformat=channel_layouts=mono,silenceremove=start_periods=1:start_threshold=-45dB"
    mean, peak = levels(path, pre)
    gain = min(TARGET_MEAN - mean, TARGET_PEAK - peak + 3)
    ogg = OUT / f"{kind}.ogg"
    chain = f"{pre},volume={gain:.2f}dB,alimiter=limit={10 ** (TARGET_PEAK / 20):.3f}:level=disabled,aresample=44100"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(path), "-map", "0:a:0", "-vn", "-af", chain,
                    "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", str(ogg)], check=True)
    print(f"  {kind}: from {path.name}, {gain:+.1f} dB, {ogg.stat().st_size // 1024} KB")


def main():
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg isn't on the PATH")
    only = None
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split(","))
    voice = sys.argv[sys.argv.index("--voice") + 1] if "--voice" in sys.argv else ""
    tts_all = "--tts" in sys.argv
    OUT.mkdir(parents=True, exist_ok=True)
    kinds = [k for k in LINES if not only or k in only]
    for kind in kinds:
        if not tts_all and recording(kind):
            convert(recording(kind), kind)
    spoken = [k for k in kinds if tts_all or not recording(k)]
    if spoken:
        speak(spoken, voice)
    write_sounds_json()


def speak(kinds, voice):
    """Windows speech for the kinds without a recording."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        listing = tmp / "list.txt"
        listing.write_text("\n".join(f"{tmp / (k + '.wav')}\t{LINES[k][0]}\t{LINES[k][1]}" for k in kinds), encoding="utf-8")
        script = tmp / "speak.ps1"
        script.write_text(PS, encoding="utf-8-sig")
        env = dict(**__import__("os").environ, BR_LIST=str(listing), BR_VOICE=voice)
        result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                                capture_output=True, text=True, env=env)
        print(result.stdout.strip().splitlines()[0] if result.stdout.strip() else result.stderr.strip())
        for kind in kinds:
            wav = tmp / f"{kind}.wav"
            if not wav.exists():
                print(f"  {kind}: no speech made")
                continue
            ogg = OUT / f"{kind}.ogg"
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-vn", "-af", FILTER, "-ac", "1", "-ar", "44100",
                            "-c:a", "libvorbis", "-q:a", "5", str(ogg)], check=True)
            print(f"  {kind}: spoken, {ogg.stat().st_size // 1024} KB")


def write_sounds_json():
    """One sound event per voice line, subtitled with the brainrot's name."""
    sounds = {f"voice.{k}": {"sounds": [f"skyrpg_brainrots:voice/{k}"], "subtitle": f"subtitles.skyrpg_brainrots.voice.{k}"} for k in LINES}
    (ASSETS / "sounds.json").write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")
    print("wrote sounds.json")


if __name__ == "__main__":
    main()
