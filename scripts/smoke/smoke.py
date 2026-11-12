#!/usr/bin/env python3
"""Black-box smoke test of an installed build on a device or emulator, driven over adb.

It reads the screen with `uiautomator dump` and taps what a child would tap, so the same script tests
a debug APK, an R8-shrunk release APK and an upgrade from one release to the next. It knows labels and
content descriptions from app/src/main/res/values/strings.xml, not app classes.

  smoke.py play   --package ID --out DIR   # new profile, one lesson in each class, restart, stars remain
  smoke.py verify --package ID --out DIR   # after an upgrade: the stars recorded by `play` are still there

`play` writes DIR/state.json; `verify` reads it (--state). Screenshots, screen dumps and logcat go to DIR.
Answers are chosen without knowing the content: the first option, or the answer the app showed after an
earlier mistake. A lesson therefore ends with one to three stars; the test checks they survive a restart.
"""
import argparse
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ACTIVITY = "eu.wanderclan.wordquest.MainActivity"
LESSON_NODE = re.compile(r"^(?P<unit>.+), lesson 1: (?P<stars>\d) of 3 stars$")
NOT_OPTIONS = {"Stop the lesson", "Hear the word", "Listen", "Start again", "Check", "Continue"}


class SmokeFailure(Exception):
    pass


class Device:
    def __init__(self, package: str, out: Path):
        self.package = package
        self.out = out
        self.step = 0
        self.width = 0
        self.log = open(out / "steps.log", "a", encoding="utf-8")

    def say(self, message: str):
        line = f"{time.strftime('%H:%M:%S')} {message}"
        print(line, flush=True)
        self.log.write(line + "\n")
        self.log.flush()

    def adb(self, *args: str, check: bool = True) -> str:
        result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=120)
        if check and result.returncode != 0:
            raise SmokeFailure(f"adb {' '.join(args)} failed: {result.stdout}{result.stderr}")
        return result.stdout

    def shell(self, *args: str, check: bool = True) -> str:
        return self.adb("shell", *args, check=check)

    # Screen.

    def dump(self) -> list:
        for _ in range(10):
            output = self.shell("uiautomator", "dump", "/sdcard/window.xml", check=False)
            if "dumped" in output:
                xml = self.adb("exec-out", "cat", "/sdcard/window.xml")
                try:
                    root = ET.fromstring(xml)
                except ET.ParseError:
                    time.sleep(1)
                    continue
                nodes = []
                for element in root.iter("node"):
                    if element.get("package") != self.package:
                        continue
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", element.get("bounds", "[0,0][0,0]")))
                    nodes.append({
                        "text": element.get("text", ""), "desc": element.get("content-desc", ""),
                        "class": element.get("class", ""), "clickable": element.get("clickable") == "true",
                        "enabled": element.get("enabled") == "true", "bounds": (x1, y1, x2, y2),
                    })
                    self.width = max(self.width, x2)
                self.last_xml = xml
                return nodes
            time.sleep(1)
        raise SmokeFailure(f"uiautomator dump failed: {output}")

    def screenshot(self, name: str):
        self.step += 1
        path = self.out / f"{self.step:03d}-{name}.png"
        with open(path, "wb") as png:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=png, check=True, timeout=60)
        dump = getattr(self, "last_xml", None)
        if dump:
            (self.out / f"{self.step:03d}-{name}.xml").write_text(dump, encoding="utf-8")

    def wait(self, predicate, what: str, timeout: float = 30) -> dict:
        deadline = time.time() + timeout
        while True:
            nodes = self.dump()
            for node in nodes:
                if predicate(node):
                    return node
            if time.time() > deadline:
                self.screenshot("timeout")
                raise SmokeFailure(f"timed out after {timeout:.0f} s waiting for {what}")
            time.sleep(0.5)

    def wait_text(self, text: str, timeout: float = 30) -> dict:
        return self.wait(lambda n: n["text"] == text or n["desc"] == text, f"'{text}'", timeout)

    def tap(self, node: dict):
        x1, y1, x2, y2 = node["bounds"]
        self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.6)

    def tap_text(self, text: str, timeout: float = 30):
        self.tap(self.wait_text(text, timeout))

    def scroll_down(self):
        width = self.width or 1080
        self.shell("input", "swipe", str(width // 2), "1600", str(width // 2), "600", "300")
        time.sleep(0.8)

    # App.

    def launch(self):
        self.shell("am", "start", "-W", "-n", f"{self.package}/{ACTIVITY}")

    def restart(self):
        """A process restart, not an activity recreation: the database is read back from disk."""
        self.shell("am", "force-stop", self.package)
        time.sleep(1)
        if self.package in self.shell("pidof", self.package, check=False):
            raise SmokeFailure("the app process survived force-stop")
        self.launch()


def first_lesson(device: Device, timeout: float = 30) -> tuple:
    node = device.wait(lambda n: LESSON_NODE.match(n["desc"]) is not None, "lesson 1 on the path", timeout)
    match = LESSON_NODE.match(node["desc"])
    return node, match.group("unit"), int(match.group("stars"))


def play_lesson(device: Device, label: str) -> dict:
    """Plays the lesson on screen to the end. Returns what was observed."""
    known_answers = set()
    seen = {"questions": 0, "mistakes": 0, "types": set(), "speaker_after_answer": 0}
    typed = False
    for _ in range(300):
        nodes = device.dump()
        texts = {n["text"] for n in nodes} | {n["desc"] for n in nodes}
        if "Lesson complete!" not in texts and "Stop the lesson" not in texts:
            time.sleep(0.5)  # Still on the path, or between screens.
            continue
        if "Lesson complete!" in texts:
            device.screenshot(f"{label}-result")
            stars = next((int(m.group(1)) for n in nodes if (m := re.match(r"^(\d) of 3 stars$", n["desc"]))), None)
            device.tap_text("Continue")
            seen["types"] = sorted(seen["types"])
            seen["result_stars"] = stars
            return seen
        feedback = next((t for t in texts if t == "Well done!" or t.startswith("Not quite") or t.startswith("Try these pairs")), None)
        if feedback:
            seen["questions"] += 1
            typed = False
            if feedback != "Well done!":
                seen["mistakes"] += 1
            if feedback.startswith("Not quite. It is: "):
                known_answers.add(feedback.removeprefix("Not quite. It is: "))
            if "Hear the word" in texts:
                seen["speaker_after_answer"] += 1
            if seen["questions"] <= 2:
                device.screenshot(f"{label}-feedback-{seen['questions']}")
            continue_button = next((n for n in nodes if n["text"] == "Continue" and n["enabled"]), None)
            if continue_button is None:
                device.scroll_down()
            else:
                device.tap(continue_button)
            continue
        if seen["questions"] < 2:
            device.screenshot(f"{label}-question-{seen['questions'] + 1}")
        clickable = [n for n in nodes if n["clickable"] and n["enabled"]
                     and n["text"] not in NOT_OPTIONS and n["desc"] not in NOT_OPTIONS]
        check = next((n for n in nodes if n["text"] == "Check" and n["enabled"]), None)
        edit = next((n for n in nodes if "EditText" in n["class"]), None)
        if edit is not None:
            seen["types"].add("typing")
            if not typed:
                device.tap(edit)
                device.shell("input", "text", (sorted(known_answers) or ["a"])[0].replace(" ", "%s"))
                device.shell("input", "keyevent", "66")  # Enter submits (ImeAction.Done).
                time.sleep(0.6)
                typed = True
            else:
                device.tap(check) if check else device.scroll_down()
            continue
        if "Build the word" in texts:
            seen["types"].add("tiles")
            tiles = [n for n in clickable if len(n["text"]) == 1]
            for tile in tiles:
                device.tap(tile)
            if not tiles:
                device.tap(check) if check else device.scroll_down()
            continue
        if "Match the words and pictures" in texts or "Match the words and definitions" in texts:
            seen["types"].add("pairs")
            middle = (device.width or 1080) // 2
            words = [n for n in clickable if n["bounds"][2] <= middle + 10 and n["text"]]
            if words:
                device.tap(words[0])
                meanings = [n for n in device.dump() if n["clickable"] and n["enabled"] and n["bounds"][0] >= middle - 10
                            and n["text"] not in NOT_OPTIONS and n["desc"] not in NOT_OPTIONS]
                if not meanings:
                    raise SmokeFailure("pairs: no meaning could be chosen")
                device.tap(meanings[0])
            else:
                device.tap(check) if check else device.scroll_down()
            continue
        if clickable:
            seen["types"].add("choice-picture" if clickable[0]["desc"].startswith("Picture") else "choice-text")
            device.tap(next((n for n in clickable if n["text"] in known_answers), clickable[0]))
            continue
        device.scroll_down()
    raise SmokeFailure(f"{label}: the lesson did not finish after 300 screens")


def choose_class(device: Device, grade: str):
    device.wait_text("Which class are you in?")
    device.screenshot(f"class-picker-{grade}")
    device.tap_text(grade)


def play(device: Device, state_path: Path) -> dict:
    device.launch()
    device.tap_text("New player", timeout=60)
    device.screenshot("profiles")
    edit = device.wait(lambda n: "EditText" in n["class"], "the nickname field")
    device.tap(edit)
    device.shell("input", "text", "Mia")
    avatar = device.wait(lambda n: n["desc"].startswith("Avatar: ") and n["clickable"], "an avatar")
    device.tap(avatar)
    device.screenshot("new-player")
    create = device.wait(lambda n: n["text"] == "Let's go", "Let's go")
    if not create["enabled"]:
        raise SmokeFailure("Let's go is disabled after a nickname and an avatar were given")
    device.tap(create)

    lessons = {}
    for grade in ("P1", "P3"):
        if lessons:
            device.tap_text("Change class")
        choose_class(device, grade)
        node, unit, stars = first_lesson(device)
        device.screenshot(f"{grade}-path")
        if stars != 0:
            raise SmokeFailure(f"{grade}: a new profile shows {stars} stars on {unit}")
        device.tap(node)
        observed = play_lesson(device, grade)
        _, unit_after, stars_after = first_lesson(device)
        device.screenshot(f"{grade}-path-after")
        if unit_after != unit or not 1 <= stars_after <= 3:
            raise SmokeFailure(f"{grade}: after the lesson the path shows {unit_after} with {stars_after} stars")
        if observed.get("result_stars") not in (None, stars_after):
            raise SmokeFailure(f"{grade}: the result screen said {observed['result_stars']} stars, the path {stars_after}")
        lessons[grade] = {"unit": unit, "stars": stars_after, **observed}
        device.say(f"{grade}: {unit}, lesson 1: {stars_after} stars; {observed}")

    state = {"package": device.package, "lessons": lessons}
    state_path.write_text(json.dumps(state, indent=2), encoding="utf-8")
    device.restart()
    verify(device, state)
    return state


def verify(device: Device, state: dict):
    """After a restart or an upgrade the single profile opens on its last class, P3, and P1 still has its stars."""
    lessons = state["lessons"]
    for index, grade in enumerate(("P3", "P1")):
        if index:
            device.tap_text("Change class")
            choose_class(device, grade)
        _, unit, stars = first_lesson(device, timeout=60)
        device.screenshot(f"{grade}-after-restart")
        expected = lessons[grade]
        if (unit, stars) != (expected["unit"], expected["stars"]):
            raise SmokeFailure(f"{grade}: expected {expected['unit']} with {expected['stars']} stars, found {unit} with {stars}")
        device.say(f"{grade}: {unit}, lesson 1: {stars} stars after restart, as before")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=("play", "verify"))
    parser.add_argument("--package", required=True)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--state", type=Path, help="state.json from `play` (default: OUT/state.json)")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    state_path = args.state or args.out / "state.json"
    device = Device(args.package, args.out)
    device.adb("logcat", "-c", check=False)
    device.say(f"device: API {device.shell('getprop', 'ro.build.version.sdk').strip()}, "
               f"{device.shell('wm', 'size').strip()}, {device.shell('wm', 'density').strip()}")
    engines = sorted(line.removeprefix("package:") for line in device.shell("pm", "list", "packages").split()
                     if re.search(r"tts|pico|speech", line, re.I))
    device.say("speech engines: " + (", ".join(engines) or "none"))
    ok = False
    try:
        if args.mode == "play":
            play(device, state_path)
        else:
            device.launch()
            verify(device, json.loads(state_path.read_text(encoding="utf-8")))
        ok = True
    except SmokeFailure as failure:
        device.say(f"FAILED: {failure}")
    finally:
        (args.out / "logcat.txt").write_text(device.adb("logcat", "-d", "-v", "threadtime", check=False), encoding="utf-8")
        crashes = device.adb("logcat", "-d", "-b", "crash", check=False)
        (args.out / "logcat-crash.txt").write_text(crashes, encoding="utf-8")
        if device.package in crashes:
            device.say("FAILED: the crash log names the app")
            ok = False
    device.say("PASSED" if ok else "FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
