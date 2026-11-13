#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Piper Cori pipeline for the bundled speech clips; only dependency/model downloads use the network.

All synthesis runs locally, on one CPU thread. Each word starts a fresh seeded
session. Reproducibility is checked on Linux x86_64; other CPUs are not promised.
"""
import argparse
import hashlib
import io
import json
import math
import subprocess
import tempfile
import urllib.request
from pathlib import Path

import imageio_ffmpeg
import numpy as np
import onnxruntime as ort
import soundfile as sf
from piper import PiperVoice
from piper.config import PiperConfig, SynthesisConfig

ROOT = Path(__file__).resolve().parents[2]
REV = "cf910fb59f1d79ab3b625a63993a108751c6c25d"
MODEL_BASE = f"https://huggingface.co/rhasspy/piper-voices/resolve/{REV}/en/en_GB/cori/medium/"
FILES = {
    "en_GB-cori-medium.onnx": "2e0a55e140de672aa19b182602b407607bd2e6c35fc6020c0b1516053c02e995",
    "en_GB-cori-medium.onnx.json": "f89b01c5e483c75ce9085ff89c79446c1a95a1cb4a362f629dac3b5a4b5dde75",
}


def check_model(cache):
    cache.mkdir(parents=True, exist_ok=True)
    for name, checksum in FILES.items():
        path = cache / name
        if not path.exists():
            partial = path.with_suffix(path.suffix + ".part")
            urllib.request.urlretrieve(MODEL_BASE + name, partial)
            if hashlib.sha256(partial.read_bytes()).hexdigest() != checksum:
                raise ValueError(f"Model hash mismatch: {name}")
            partial.replace(path)
        if hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
            raise ValueError(f"Model hash mismatch: {name}")
    return cache / "en_GB-cori-medium.onnx"


def load_voice(model):
    # Random operators keep state inside the ONNX session. Reset both seed and
    # session for every word so an unrelated content edit cannot change its clip.
    ort.set_seed(42)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    config = PiperConfig.from_dict(json.loads(model.with_suffix(".onnx.json").read_text()))
    assert config.espeak_voice == "en-gb-x-rp"
    session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
    return PiperVoice(session=session, config=config)


def words_in(content):
    words = {}
    all_ids = set()
    grades = json.loads((content / "grades.json").read_text("utf-8"))["grades"]
    for grade in grades:
        pack = content / grade["pack"]
        if not pack.exists():
            continue
        for unit in json.loads(pack.read_text("utf-8"))["units"]:
            for word in unit["words"]:
                word_id = word["id"]
                if not word_id or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in word_id):
                    raise ValueError(f"Invalid word id: {word_id}")
                if word_id in all_ids:
                    raise ValueError(f"Duplicate word id: {word_id}")
                all_ids.add(word_id)
                if grade["band"] == "A":
                    words[word_id] = word["text"]
    return words


def process(raw, rate, word, output, cache):
    flags = []
    if not len(raw) or not np.isfinite(raw).all():
        raise ValueError(f"Invalid samples: {word}")
    if np.any(np.abs(raw) >= 1):
        flags.append("raw clipping")
    window = round(rate * .010)
    padded = np.pad(raw, (0, (-len(raw)) % window))
    levels = np.sqrt(np.mean(padded.reshape(-1, window) ** 2, axis=1))
    active = np.flatnonzero(levels > 10 ** (-45 / 20))
    if not len(active):
        print(f"FLAG {word}: silence", flush=True)
        raise ValueError(f"Silent synthesis: {word}")
    start = max(0, active[0] * window - round(rate * .040))
    end = min(len(raw), (active[-1] + 1) * window + round(rate * .040))
    signal = raw[start:end].copy()
    rms = float(np.sqrt(np.mean(signal ** 2)))
    peak = float(np.max(np.abs(signal)))
    signal *= min(10 ** (-20 / 20) / rms, 10 ** (-3 / 20) / peak)
    fade = min(round(rate * .002), len(signal) // 2)
    signal[:fade] *= np.linspace(0, 1, fade)
    signal[-fade:] *= np.linspace(1, 0, fade)
    # Temporary WAV/OGG stay outside the content directory; only complete clips replace assets.
    with tempfile.TemporaryDirectory(dir=cache) as temporary:
        wav, ogg = Path(temporary) / "clip.wav", Path(temporary) / "clip.ogg"
        sf.write(wav, signal, rate, subtype="PCM_16")
        subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-nostdin", "-y", "-threads", "1",
                        "-i", str(wav), "-map_metadata", "-1", "-ac", "1", "-ar", str(rate),
                        "-c:a", "libvorbis", "-q:a", "3", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(ogg)], check=True)
        data = ogg.read_bytes()
    decoded, decoded_rate = sf.read(io.BytesIO(data), always_2d=True)
    assert decoded.shape[1] == 1 and decoded_rate == 22050
    info = sf.info(io.BytesIO(data))
    assert info.format == "OGG" and info.subtype == "VORBIS"
    duration = len(decoded) / decoded_rate
    decoded_peak = float(np.max(np.abs(decoded)))
    decoded_rms = float(np.sqrt(np.mean(decoded ** 2)))
    if decoded_peak >= .99:
        flags.append("decoded clipping")
    if decoded_rms < .01:
        flags.append("near silence")
    # Broad guardrails, not a judgement of pronunciation or consonant preservation.
    letters = sum(c.isalpha() for c in word)
    if not max(.15, .025 * letters) <= duration <= max(1.25, .18 * letters + .4):
        flags.append("implausible duration")
    if not output.exists() or output.read_bytes() != data:
        output.write_bytes(data)
    return dict(duration_s=round(duration, 4), bytes=len(data),
                peak_dbfs=round(20 * math.log10(max(decoded_peak, 1e-10)), 3),
                rms_dbfs=round(20 * math.log10(max(decoded_rms, 1e-10)), 3),
                flags=flags, sha256=hashlib.sha256(data).hexdigest())


def generate(content, cache, overrides):
    words = words_in(content)
    audio = content / "audio"
    audio.mkdir(exist_ok=True)
    model = check_model(cache) if words else None
    settings = SynthesisConfig(length_scale=1.10, noise_scale=.5, noise_w_scale=.333, normalize_audio=False)
    rows = []
    for word_id, text in sorted(words.items()):
        spoken = overrides.get(word_id, text)
        if not isinstance(spoken, str) or not spoken.strip():
            raise ValueError(f"Invalid pronunciation override: {word_id}")
        voice = load_voice(model)
        chunks = list(voice.synthesize(spoken + ".", settings))
        raw = np.concatenate([c.audio_float_array for c in chunks]).astype(np.float64)
        row = dict(id=word_id, text=text, spoken=spoken, **process(raw, voice.config.sample_rate, text, audio / f"{word_id}.ogg", cache))
        del voice
        rows.append(row)
        print(f"{content}: {word_id}.ogg {row['duration_s']:.4f}s {row['bytes']} bytes" +
              (f" FLAG: {', '.join(row['flags'])}" if row["flags"] else " OK"), flush=True)
    for existing in sorted(audio.glob("*.ogg")):
        if existing.stem not in words:
            existing.unlink()
            print(f"{content}: removed unused audio/{existing.name}")
    print(f"{content}: {len(rows)} clips, {sum(r['bytes'] for r in rows)} bytes, {sum(bool(r['flags']) for r in rows)} flagged")
    return dict(content=str(content), clips=rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--report", type=Path, help="optional metrics JSON outside content/")
    parser.add_argument("content", nargs="*", type=Path)
    args = parser.parse_args()
    overrides = json.loads(Path(__file__).with_name("overrides.json").read_text("utf-8"))
    reports = [generate(c.resolve(), args.cache, overrides) for c in args.content or [ROOT / "app/src/main/assets/content"]]
    if args.report:
        args.report.write_text(json.dumps(reports, ensure_ascii=False, indent=2) + "\n", "utf-8")
