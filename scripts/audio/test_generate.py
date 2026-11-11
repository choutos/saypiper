"""Integration checks against the real, cached Cori model. Run after scripts/make-audio.

AUDIO_CACHE_DIR=/path/to/cache /path/to/cache/venv/bin/python -m unittest discover -s scripts/audio
"""
import contextlib
import hashlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path

from generate import generate


class AudioGenerationTest(unittest.TestCase):
    def setUp(self):
        self.cache = Path(os.environ.get("AUDIO_CACHE_DIR", Path.home() / ".cache/wordquest-audio"))
        self.temporary = tempfile.TemporaryDirectory(dir=self.cache)
        self.addCleanup(self.temporary.cleanup)
        self.content = Path(self.temporary.name)
        (self.content / "packs").mkdir()

    def content_with(self, words, band="A"):
        (self.content / "grades.json").write_text(json.dumps({"grades": [{"id": "test", "band": band, "pack": "packs/test.json"}]}))
        (self.content / "packs/test.json").write_text(json.dumps({"units": [{"words": [{"id": key, "text": text} for key, text in words]}]}))

    def run_generator(self, overrides=None):
        with contextlib.redirect_stdout(io.StringIO()):
            return generate(self.content, self.cache, overrides or {})

    def clip(self, word_id):
        return (self.content / "audio" / f"{word_id}.ogg").read_bytes()

    def test_adding_an_earlier_word_does_not_change_an_existing_clip(self):
        self.content_with([("dog", "dog")])
        self.run_generator()
        original = self.clip("dog")
        # FRL-15's approved dog was first in a fresh session with seed 42.
        self.assertEqual("25088b9ff882082c9422025fca0c151c85cd9a187b0c989d8b2d1841ad3ed342", hashlib.sha256(original).hexdigest())
        self.content_with([("apple", "apple"), ("dog", "dog"), ("same-text", "dog")])
        self.run_generator()
        self.assertEqual(original, self.clip("dog"))
        self.assertEqual(original, self.clip("same-text"))

    def test_overriding_another_word_does_not_change_dog(self):
        self.content_with([("cat", "cat"), ("dog", "dog")])
        self.run_generator()
        dog = self.clip("dog")
        cat = self.clip("cat")
        self.run_generator({"cat": "kitten"})
        self.assertEqual(dog, self.clip("dog"))
        self.assertNotEqual(cat, self.clip("cat"))

    def test_moving_a_word_out_of_band_a_removes_its_clip(self):
        self.content_with([("dog", "dog")])
        self.run_generator()
        self.content_with([("dog", "dog")], band="B")
        self.run_generator()
        self.assertFalse((self.content / "audio/dog.ogg").exists())


if __name__ == "__main__":
    unittest.main()
