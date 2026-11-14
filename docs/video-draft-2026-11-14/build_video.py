"""문앞 러프 영상 재생성. Python 3.9+, Pillow, imageio-ffmpeg, macOS say(Yuna).

가족 그림은 imagegen으로 만든 단일 스토리보드 원본이다.
이 스크립트는 원본을 바꾸지 않고 영화 프레임으로 배치·전환한다.
실제 구동 검증용 docs/문앞_시연영상.mp4와 구별되는 시나리오 영상이다.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import array
import bisect
import json
import math
import shutil
import subprocess
import sys
import tempfile
import wave
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "assets"
STORY = json.loads((ROOT / "timeline.json").read_text())
WIDTH, HEIGHT = 1280, 720
FPS = STORY["fps"]
SECONDS = STORY["duration"]
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
FONTROOT = ROOT.parent / "ux-concept-2026-11-14" / "assets" / "fonts"
UIROOT = ROOT.parent / "ux-concept-2026-11-14" / "screenshots"
WORK = Path(tempfile.mkdtemp(prefix="atthedoor-film-"))
OUTPUT = ROOT / "문앞_스토리영상_러프_v1.mp4"


def font(size, bold=False):
    name = "AstaSans-SemiBold.ttf" if bold else "AstaSans-Regular.ttf"
    return ImageFont.truetype(str(FONTROOT / name), size)


def run(args):
    return subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def clock(t, sep=":", fraction=False):
    ms = round(t * 1000)
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02}{sep}{m:02}{sep}{s:02}" + (f",{ms:03}" if fraction else "")


def make_script():
    lines = [STORY["title"], "러프 시나리오 영상 v1 · 2026.11.14 · 3분 · 1280×720 / 24fps", STORY["disclosure"], "", "연출 메모", "대사는 생활 말투로, 회상은 짧게. 말이 끝난 뒤 표정과 침묵을 남긴다.", "배우 녹음 전 임시 음성이다. 한 명의 한국어 TTS로 역할별 속도만 달리했다.", "일러스트 정지 컷에 완만한 이동과 디졸브를 붙인 애니매틱이다.", "주차와 도착은 사람이 직접 확인하며, 차량 신호로 자동 확정하지 않는다.", "UI 컷은 신규 시안이다. 실제 APK 화면 녹화나 SDK 동작 증빙이 아니다.", "시설·거리·입구는 demo 데이터이며 실제 장소 정보를 보장하지 않는다.", "공연 후 가족 장면은 가상 이야기다. 앱의 안내 범위는 건물 입구까지다.", "BGM은 빌드 스크립트로 합성한 임시 배경음이다.", "", "장면과 대사"]
    subs = []
    for shot in STORY["shots"]:
        start, end = shot["start"], shot["end"]
        lines.append(f"\n{clock(start)[3:]}–{clock(end)[3:]} | {shot['chapter']}")
        if "ui" in shot:
            lines.append("화면: UI 시안 / " + shot["ui"] + " / " + shot.get("title", ""))
        else:
            lines.append("화면: 가상 가족 일러스트 " + str(shot["panel"] + 1))
        for cue in STORY["dialogue"]:
            if start <= cue["start"] < end:
                lines.append(cue["speaker"] + ": “" + cue["text"].replace("\n", " ") + "”")
    for i, cue in enumerate(STORY["dialogue"], 1):
        subs.append(f"{i}\n{clock(cue['start'], fraction=True)} --> {clock(cue['end'], fraction=True)}\n{cue['speaker']}: {cue['text']}\n")
    (ROOT / "영상대본.txt").write_text("\n".join(lines) + "\n")
    (ROOT / "대사자막.srt").write_text("\n".join(subs))


def make_audio():
    sample_rate = 22050
    length = sample_rate * SECONDS
    dialogue = array.array("f", [0.0]) * length
    report = []
    for i, cue in enumerate(STORY["dialogue"]):
        aiff, wav = WORK / f"voice-{i}.aiff", WORK / f"voice-{i}.wav"
        text = cue["text"].replace("…", "、").replace("\n", " ")
        run(["say", "-v", "Yuna", "-r", str(cue.get("rate", 175)), "-o", str(aiff), text])
        run([FFMPEG, "-y", "-i", str(aiff), "-ar", str(sample_rate), "-ac", "1", "-c:a", "pcm_s16le", str(wav)])
        with wave.open(str(wav), "rb") as f:
            samples = array.array("h", f.readframes(f.getnframes()))
        # 앞뒤 무음을 줄여 대화가 자연스럽게 이어지게 한다.
        first = next((j for j, x in enumerate(samples) if abs(x) > 120), 0)
        last = next((j for j in range(len(samples) - 1, -1, -1) if abs(samples[j]) > 120), len(samples) - 1)
        samples = samples[max(0, first - 900):min(len(samples), last + 1800)]
        slot = cue["end"] - cue["start"] - 0.12
        original_duration = len(samples) / sample_rate
        speed = max(1.0, original_duration / slot)
        if speed > 1.0:
            # 자막이 사라지기 전에 발화가 끝나도록 최소한만 속도를 보정한다.
            with wave.open(str(wav), "wb") as f:
                f.setnchannels(1); f.setsampwidth(2); f.setframerate(sample_rate); f.writeframes(samples.tobytes())
            adjusted = WORK / f"voice-{i}-fit.wav"
            run([FFMPEG, "-y", "-i", str(wav), "-af", f"atempo={speed:.5f}", str(adjusted)])
            with wave.open(str(adjusted), "rb") as f:
                samples = array.array("h", f.readframes(f.getnframes()))
        peak = max(max(abs(x) for x in samples), 1)
        gain = 0.53 / peak
        offset = int(cue["start"] * sample_rate)
        for j, value in enumerate(samples):
            dialogue[offset + j] += value * gain
        report.append({"speaker":cue["speaker"], "start":cue["start"], "voice_seconds":round(len(samples) / sample_rate, 3), "slot_seconds":round(slot, 3), "tempo_adjustment":round(speed, 3)})
        print(f"voice {i + 1}/{len(STORY['dialogue'])}", flush=True)

    # 원곡 샘플 없이 합성한 조용한 패드. 발화 중에는 볼륨을 더 낮춘다.
    chords = [(130.8128,164.8138,195.9977), (110.0,130.8128,164.8138), (87.3071,130.8128,174.6141), (97.9989,146.8324,195.9977)]
    music = array.array("f", [0.0]) * length
    for block in range(math.ceil(SECONDS / 9)):
        freqs = chords[block % 4]
        begin, finish = block * 9 * sample_rate, min(length, (block + 1) * 9 * sample_rate)
        for k in range(begin, finish):
            local = (k - begin) / sample_rate
            envelope = min(1.0, local / 1.4, (9 - local) / 2.0)
            t = k / sample_rate
            value = sum(math.sin(2 * math.pi * hz * t) + 0.18 * math.sin(2 * math.pi * hz * 2 * t) for hz in freqs) / 3
            music[k] = 0.025 * envelope * value
    for cue in STORY["dialogue"]:
        for k in range(max(0, int((cue["start"] - .25) * sample_rate)), min(length, int((cue["end"] + .25) * sample_rate))):
            music[k] *= .42
    pcm = array.array("h")
    for k in range(length):
        t = k / sample_rate
        fade = min(1.0, t / 2, (SECONDS - t) / 2)
        pcm.append(round(max(-.95, min(.95, dialogue[k] + music[k] * fade)) * 32767))
    path = WORK / "mix.wav"
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(sample_rate); f.writeframes(pcm.tobytes())
    (ROOT / "audio-timing.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    return path


def paint_ui(shot):
    canvas = Image.new("RGB", (WIDTH, HEIGHT), "#e9ecec")
    d = ImageDraw.Draw(canvas)
    source = Image.open(UIROOT / shot["ui"]).convert("RGB")
    if shot.get("phone"):
        fitted = ImageOps.contain(source, (258, 550), Image.Resampling.LANCZOS)
        x, y = 880 - fitted.width // 2, 67
        text_x, text_y, title_size = 96, 188, 42
    else:
        fitted = ImageOps.contain(source, (765, 543), Image.Resampling.LANCZOS)
        x, y = 40 + (765 - fitted.width) // 2, 70
        text_x, text_y, title_size = 842, 205, 29
    d.rounded_rectangle((x - 7, y - 7, x + fitted.width + 7, y + fitted.height + 7), 16, fill="#cfd4d4")
    canvas.paste(fitted, (x, y))
    d.text((text_x, text_y - 45), "UI 시안 · 시연 데이터", font=font(19), fill="#54706b")
    title = shot["title"]
    if not shot.get("phone") and len(title) > 13:
        title = title.replace("수 있는", "수\n있는").replace("휴대폰에서", "휴대폰에서\n")
    d.multiline_text((text_x, text_y), title, font=font(title_size, True), fill="#192826", spacing=9)
    title_lines = title.count("\n") + 1
    detail_y = text_y + title_lines * (title_size + 9) + 23
    d.multiline_text((text_x, detail_y), shot.get("detail", ""), font=font(21), fill="#576661", spacing=13)
    if "tap" in shot:
        tap = shot["tap"]
        shot["tap_pixels"] = (x + tap["x"] * fitted.width, y + tap["y"] * fitted.height)
    return canvas


def prepare_frames():
    atlas = Image.open(ASSETS / "story-atlas.png").convert("RGB")
    bases = []
    for shot in STORY["shots"]:
        if "ui" in shot:
            base = paint_ui(shot)
        else:
            i = shot["panel"]
            x0, y0 = round((i % 3) * atlas.width / 3), round((i // 3) * atlas.height / 3)
            x1, y1 = round((i % 3 + 1) * atlas.width / 3), round((i // 3 + 1) * atlas.height / 3)
            # 영상 안의 컷 배치이며 원본 스토리보드 파일은 보존한다.
            base = ImageOps.fit(atlas.crop((x0 + 1, y0 + 1, x1 - 1, y1 - 1)), (WIDTH, HEIGHT), method=Image.Resampling.LANCZOS)
        bases.append(base)
    return bases


def header(shot):
    layer = Image.new("RGBA", (WIDTH, HEIGHT))
    d = ImageDraw.Draw(layer)
    if "ui" in shot:
        d.text((38, 20), "문앞  /  " + shot["chapter"], font=font(20, True), fill="#30423e")
        d.text((910, 22), "시나리오 영상 · UI 시안", font=font(17), fill="#576661")
    else:
        d.rectangle((0, 0, WIDTH, 65), fill=(10, 18, 16, 75))
        d.text((38, 20), "문앞  /  " + shot["chapter"], font=font(20, True), fill="white")
        d.text((900, 22), "가상 가족 · AI 일러스트", font=font(17), fill=(255,255,255,230))
    if "title" in shot and "ui" not in shot:
        d.rectangle((0, 0, WIDTH, HEIGHT), fill=(12, 20, 17, 125))
        d.multiline_text((WIDTH / 2, 300), shot["title"], font=font(53, True), fill="white", anchor="ma", align="center", spacing=16)
        text = STORY["disclosure"] if shot["start"] == 0 else "가상 가족과 UI 시안으로 구성한 러프 시나리오 영상"
        d.text((WIDTH / 2, 460), text, font=font(19), anchor="mt", fill=(255,255,255,225))
    return layer


def subtitles():
    layers = []
    for cue in STORY["dialogue"]:
        layer = Image.new("RGBA", (WIDTH, HEIGHT))
        d = ImageDraw.Draw(layer)
        d.rectangle((0, 604, WIDTH, HEIGHT), fill=(8, 15, 12, 213))
        d.text((44, 622), cue["speaker"], font=font(20, True), fill="#c3d7c5")
        d.multiline_text((260, 617), cue["text"], font=font(29, True), fill="white", spacing=8, stroke_width=0)
        layers.append(layer)
    return layers


def render_video(bases, audio):
    headers, caps = [header(s) for s in STORY["shots"]], subtitles()
    starts = [s["start"] for s in STORY["shots"]]
    capture = {1, 20, 28, 50, 67, 95, 102, 110, 118, 125, 138, 144, 151, 162, 173, 178}
    stills = WORK / "checks"
    stills.mkdir()
    args = [FFMPEG, "-y", "-hide_banner", "-loglevel", "warning", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{WIDTH}x{HEIGHT}", "-r", str(FPS), "-i", "pipe:0", "-i", str(audio), "-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p", "-threads", "3", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-t", str(SECONDS), str(OUTPUT)]
    with (WORK / "ffmpeg.log").open("w") as log:
        proc = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=log)
        last_shot = -1
        for frame_no in range(SECONDS * FPS):
            t = frame_no / FPS
            idx = bisect.bisect_right(starts, t) - 1
            shot = STORY["shots"][idx]
            base = bases[idx]
            if "ui" not in shot:
                progress = (t - shot["start"]) / (shot["end"] - shot["start"])
                zoom = 1.0 + .035 * progress
                w, h = WIDTH / zoom, HEIGHT / zoom
                base = base.resize((WIDTH, HEIGHT), Image.Resampling.BICUBIC, box=((WIDTH - w) / 2, (HEIGHT - h) / 2, (WIDTH + w) / 2, (HEIGHT + h) / 2))
            else:
                base = base.copy()
            transition = t - shot["start"]
            if idx > 0 and transition < .45:
                base = Image.blend(bases[idx - 1], base, transition / .45)
            base.paste(headers[idx], (0, 0), headers[idx])
            if "tap_pixels" in shot and 0 <= t - shot["tap"]["at"] <= .7:
                td = ImageDraw.Draw(base)
                tx, ty = shot["tap_pixels"]
                r = 12 + 20 * (t - shot["tap"]["at"]) / .7
                td.ellipse((tx - r, ty - r, tx + r, ty + r), outline="#72c4b1", width=4)
            for cue, cap in zip(STORY["dialogue"], caps):
                if cue["start"] <= t < cue["end"]:
                    base.paste(cap, (0, 0), cap)
                    break
            if frame_no % FPS == 0 and int(t) in capture:
                base.save(stills / f"{int(t):03}.jpg", quality=90)
            if t < 1 or t > SECONDS - .8:
                amount = t if t < 1 else max(0, (SECONDS - t) / .8)
                base = Image.blend(Image.new("RGB", (WIDTH, HEIGHT), "#101915"), base, amount)
            proc.stdin.write(base.tobytes())
            if idx != last_shot:
                print(f"video {clock(t)} / 00:03:00 · {shot['chapter']}", flush=True)
                last_shot = idx
        proc.stdin.close()
        if proc.wait() != 0:
            raise RuntimeError((WORK / "ffmpeg.log").read_text())
    # 검토용 콘택트 시트와 포스터. 배포 파일은 하나의 MP4다.
    thumbs = sorted(stills.glob("*.jpg"))
    sheet = Image.new("RGB", (1280, math.ceil(len(thumbs) / 4) * 205), "#e9ecec")
    d = ImageDraw.Draw(sheet)
    for i, path in enumerate(thumbs):
        x, y = i % 4 * 320, i // 4 * 205
        thumb = Image.open(path).resize((320, 180))
        sheet.paste(thumb, (x, y))
        d.text((x + 8, y + 183), clock(int(path.stem))[3:], font=font(14), fill="#30423e")
    sheet.save(ROOT / "검토용_장면모음.jpg", quality=90)
    shutil.copyfile(stills / "001.jpg", ROOT / "poster.jpg")


if __name__ == "__main__":
    print("work:", WORK, flush=True)
    try:
        make_script()
        audio = make_audio()
        render_video(prepare_frames(), audio)
        print("DONE", OUTPUT, OUTPUT.stat().st_size, flush=True)
    except Exception:
        print("작업 중간 파일:", WORK, file=sys.stderr)
        raise
    else:
        shutil.rmtree(WORK)
