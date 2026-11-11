import type { FC } from "hono/jsx";
import type { PhoneEvent } from "./contract.ts";
import type { StoredTrip } from "./store.ts";

/** 휴대폰에서 보이는 단계. 서버에 기록된 이벤트로 정한다. 사람의 도착은 사용자가 버튼으로 확인한다. */
export type GuideStage = "guide" | "visit" | "return";

export function stageOf(events: PhoneEvent[]): GuideStage {
  let stage: GuideStage = "guide";
  for (const e of events) {
    if (e.type === "arrived" && stage === "guide") stage = "visit";
    else if (e.type === "arrival_undone" && stage === "visit") stage = "guide";
    else if (e.type === "returning" && stage === "visit") stage = "return";
  }
  return stage;
}

/**
 * 휴대폰 안내 페이지. 읽기 중심이며 로그인·설치가 없다.
 * 실내 위치 추적이나 AR 방향은 띄우지 않는다.
 */
/** 휴대폰에서 남긴 최신 주차 메모가 있으면 그것을, 없으면 차량에서 넘긴 메모를 쓴다 */
export function parkingNoteOf(trip: StoredTrip, events: PhoneEvent[]): string | null {
  const latest = [...events].reverse().find((e) => e.type === "parking_note" && e.note);
  return latest?.note ?? trip.snapshot.parkingNote;
}

export const GuidePage: FC<{ trip: StoredTrip; events: PhoneEvent[] }> = ({ trip, events }) => {
  const s = { ...trip.snapshot, parkingNote: parkingNoteOf(trip, events) };
  const stage = stageOf(events);
  const hasReturnSteps = s.returnSteps.length > 0;
  const steps = stage === "return" ? (hasReturnSteps ? s.returnSteps : [...s.steps].reverse()) : s.steps;
  // 계획 이름("후문 일반 주차 구역 → 정문 경사로")에서 차를 세운 곳만 뗀다.
  const spot = s.planName.split(" → ")[0];
  const noteForm = (
    <details class="row">
      <summary>
        <span>주차 위치</span>
        <b id="parking-note">{s.parkingNote ?? "지금 남기기"}</b>
      </summary>
      <form data-type="parking_note">
        <input name="note" maxlength={40} placeholder="예: 지하 2층 B-14" aria-label="주차 위치 메모" />
        <button type="submit">저장</button>
      </form>
    </details>
  );
  const spotCard = (
    <section class="spot">
      <p class="label">주차한 곳</p>
      <p class="mid">{spot}</p>
      {s.parkingNote && <p class="big">{s.parkingNote}</p>}
      <p class="label">이용한 입구 · {s.entranceName}</p>
    </section>
  );
  return (
    <html lang="ko">
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="robots" content="noindex" />
        <title>{`${s.entranceName} 안내 · 문앞`}</title>
        <style>{css}</style>
      </head>
      <body>
        <header class="top">
          <strong>문앞</strong>
          <span class="venue">{s.venueName}</span>
          {s.demo && <span class="badge">시연 데이터</span>}
        </header>
        <main data-stage={stage}>
          {s.noAlternative ? (
            <>
              <h1>확인된 다른 입구가 없어요</h1>
              <p class="sub">같은 조건으로 갈 수 있는 길을 찾지 못했어요. 없는 길을 만들어 안내하지 않아요.</p>
              {spotCard}
              {s.contact && <p class="sub">시설 연락처 · {s.contact}{s.demo && " (시연용 번호)"}</p>}
            </>
          ) : stage === "visit" ? (
            <>
              <h1>입구 도착을 확인했어요</h1>
              <p class="sub">좋은 시간 보내세요. 돌아갈 때 아래 버튼을 눌러 주세요.</p>
              {spotCard}
              {!s.parkingNote && noteForm}
            </>
          ) : (
            <>
              {stage === "guide" && s.changeNote && <p class="changed">ⓘ {s.changeNote}</p>}
              <h1>{stage === "return" ? "차로 돌아가는 길" : `${s.entranceName}${/[가-힣]$/.test(s.entranceName) && ((s.entranceName.charCodeAt(s.entranceName.length - 1) - 0xac00) % 28 === 0 || (s.entranceName.charCodeAt(s.entranceName.length - 1) - 0xac00) % 28 === 8) ? "로" : "으로"} 가요`}</h1>
              <p class="sub">{stage === "return" ? `${spot}으로 돌아가요` : s.walkSummary}</p>

              {stage === "return" && spotCard}

              <h2>{stage === "return" ? (hasReturnSteps ? "돌아가는 순서" : "올 때 지나온 길 (거꾸로 따라가세요)") : "이동 순서"}</h2>
              <ol class="steps">{steps.map((step) => <li>{step}</li>)}</ol>

              {s.entrancePhotos.length > 0 && (
                <figure class="photo">
                  <figcaption>{stage === "return" ? "나갈 문" : "도착할 입구"} · {s.entranceName}</figcaption>
                  {s.photosAreIllustrations && <span class="illust">시연 일러스트 · 실제 사진 아님</span>}
                  <img src={`/photos/${s.entrancePhotos[0]}`} alt={s.photosAreIllustrations ? `${s.entranceName} 시연용 일러스트(실제 사진 아님)` : `${s.entranceName} 사진`} loading="lazy" />
                </figure>
              )}

              {stage === "guide" && noteForm}

              <details class="confirm">
                <summary class="report">{stage === "return" ? "길이 막혀 있어요 · 다른 길 찾기" : "입구가 막혀 있어요 · 다른 입구 찾기"}</summary>
                <p>차량 앱이 같은 조건으로 {stage === "return" ? "다른 길을" : "다른 입구를"} 찾을까요?</p>
                <div class="confirm-row">
                  <button data-type="entrance_blocked" class="report-go">찾기</button>
                  <button type="button" data-close>취소</button>
                </div>
              </details>
            </>
          )}
          <p id="status" role="status" aria-live="polite"></p>

          <details class="info">
            <summary>정보 출처와 안내 ▾</summary>
            <p>정보 출처: {s.evidenceLabel}</p>
            <p>실제 통행 가능 여부는 현장에서 확인해 주세요. 이 링크는 24시간 뒤 사라져요. 이름·건강 정보·이동 조건·GPS 위치는 담지 않고, 직접 남긴 주차 메모만 담아요.</p>
            {s.contact && <p>막혀 있고 대안이 없으면 시설에 문의해 주세요: <a href={`tel:${s.contact}`}>{s.contact}</a>{s.demo && " (시연용 번호)"}</p>}
          </details>
        </main>
        <nav class="bar">
          {s.noAlternative && s.contact && <a class="primary" href={`tel:${s.contact}`}>시설에 전화</a>}
          {!s.noAlternative && stage === "guide" && <button data-type="arrived" class="primary">입구에 도착했어요</button>}
          {!s.noAlternative && stage === "visit" && <button data-type="returning" class="primary">차로 돌아가기</button>}
          {stage === "visit" && <button data-type="arrival_undone" class="quiet">아직 도착하지 않았어요</button>}
          {!s.noAlternative && stage === "return" && <p class="hint">차에 타면 차량 화면에서 ‘차에 탔어요’를 눌러 마무리해요</p>}
        </nav>
        <script dangerouslySetInnerHTML={{ __html: script(trip.token, trip.version) }} />
      </body>
    </html>
  );
};

export const ExpiredPage: FC = () => (
  <html lang="ko">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <meta name="robots" content="noindex" />
      <title>만료된 안내 · 문앞</title>
      <style>{css}</style>
    </head>
    <body>
      <main>
        <h1>안내가 끝났거나 만료됐어요</h1>
        <p>차량 화면에서 새 QR을 열어 주세요.</p>
      </main>
    </body>
  </html>
);

// 토큰은 base64url 문자만 쓰므로 스크립트 문자열에 그대로 넣어도 안전하다(라우트에서 형식을 검사한다).
const script = (token: string, version: number) => `
const status = document.getElementById("status");
const labels = { arrival_undone: "안내로 돌아갈게요", arrived: "도착을 알렸어요", entrance_blocked: "차량 앱이 같은 조건으로 다른 길을 찾고 있어요. 잠시 기다려 주세요.", returning: "복귀 안내로 바꿀게요", parking_note: "메모를 저장했어요" };
let version = ${version};
async function send(type, note) {
  try {
    const res = await fetch("/api/trips/${token}/events", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(note ? { type, note } : { type }) });
    if (!res.ok) { status.textContent = res.status === 404 ? "안내가 끝났거나 만료됐어요." : "전송하지 못했어요. 다시 눌러 주세요."; return; }
    status.textContent = labels[type];
    if (type === "parking_note") document.getElementById("parking-note").textContent = note;
    if (type === "arrived" || type === "returning" || type === "arrival_undone") setTimeout(() => location.reload(), 600);
    if (type === "entrance_blocked") waitForNewPlan();
  } catch { status.textContent = "연결이 끊겼어요. 화면의 사진과 순서를 참고해 주세요."; }
}
async function waitForNewPlan() {
  for (let i = 0; i < 6; i++) {
    await new Promise((r) => setTimeout(r, 5000));
    try {
      const res = await fetch("/api/trips/${token}/plan", { cache: "no-store" });
      if (!res.ok) break;
      const body = await res.json();
      if (body.version !== version) { location.reload(); return; }
    } catch {}
  }
  status.textContent = "아직 새 안내가 없어요. 차량 화면을 확인하거나 시설에 문의해 주세요.";
  location.reload();
}
document.querySelectorAll("button[data-type]").forEach((b) => b.addEventListener("click", () => send(b.dataset.type)));
document.querySelectorAll("button[data-close]").forEach((b) => b.addEventListener("click", () => b.closest("details").removeAttribute("open")));
const form = document.querySelector("form[data-type]");
if (form) form.addEventListener("submit", (e) => {
  e.preventDefault();
  const note = e.target.note.value.trim();
  if (note) send("parking_note", note);
});
`;

const css = `
:root { color-scheme: light; --bg:#F0F0F0; --surface:#FFFFFF; --line:#D6D6D6; --fg:#111111; --muted:#555555; --primary:#002C5F; --on-primary:#FFFFFF; --report:#A84300; --demo-bg:#FFE9A8; --demo-fg:#6B4E00; }
* { box-sizing: border-box; }
html, body { overflow-x: hidden; }
body { margin:0; font: 18px/1.55 system-ui, -apple-system, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif; color:var(--fg); background:var(--bg); font-variant-numeric: tabular-nums; }
.top { position: sticky; top:0; z-index:2; display:flex; align-items:center; gap:8px; padding: 12px 16px; background: var(--surface); border-bottom: 1px solid var(--line); font-size: 15px; }
.top .venue { color: var(--muted); flex:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.badge { background: var(--demo-bg); color: var(--demo-fg); padding: 2px 8px; border-radius: 2px; font-size: 13px; font-weight: 700; }
main { max-width: 560px; margin: 0 auto; padding: 20px 16px 120px; word-break: keep-all; overflow-wrap: anywhere; }
h1 { font-size: 28px; line-height: 1.3; margin: 0 0 4px; } h2 { font-size: 15px; color: var(--muted); margin: 24px 0 4px; font-weight: 600; }
.sub { color: var(--muted); margin: 0; }
.steps { list-style: none; counter-reset: s; padding: 0; margin: 0; background: var(--surface); border: 1px solid var(--line); }
.steps li { counter-increment: s; display:flex; gap: 12px; padding: 14px 16px; font-size: 20px; border-bottom: 1px solid var(--line); min-height: 56px; }
.steps li:last-child { border-bottom: 0; }
.steps li::before { content: counter(s); flex: 0 0 28px; height: 28px; border: 2px solid var(--primary); color: var(--primary); border-radius: 50%; font-size: 15px; font-weight: 700; display:flex; align-items:center; justify-content:center; margin-top: 2px; }
.photo { margin: 16px 0 0; position: relative; } .photo .illust { position: absolute; left: 8px; top: 34px; background: var(--demo-bg); color: var(--demo-fg); font-size: 13px; font-weight: 700; padding: 2px 8px; border-radius: 2px; } .photo figcaption { font-size: 15px; color: var(--muted); margin-bottom: 6px; }
.photo img { width: 100%; display:block; border: 1px solid var(--line); }
.spot { background: var(--surface); border: 1px solid var(--line); padding: 20px 16px; margin-top: 16px; }
.label { margin: 0; color: var(--muted); font-size: 15px; } .big { font-size: 40px; font-weight: 700; margin: 4px 0 8px; line-height: 1.2; } .mid { font-size: 22px; font-weight: 700; margin: 2px 0 6px; }
details.row { margin-top: 16px; background: var(--surface); border: 1px solid var(--line); }
details.row summary { list-style: none; display:flex; justify-content: space-between; align-items:center; padding: 0 16px; min-height: 56px; cursor: pointer; }
details.row summary b { color: var(--fg); } details.row summary b::after { content: "  바꾸기 ›"; color: var(--primary); font-weight: 500; }
.changed { background: #E6EDF5; color: #002C5F; border-left: 4px solid #002C5F; padding: 12px 14px; margin: 0 0 12px; min-height: 48px; font-weight: 600; border-radius: 4px; }
details.row form { display:flex; flex-wrap: wrap; gap: 8px; padding: 0 16px 16px; }
details.row input { flex: 1; min-width: 0; font-size: 17px; padding: 12px; border: 1px solid var(--line); border-radius: 4px; background: transparent; color: var(--fg); }
details.row form button { flex: 0 0 auto; }
button { font: inherit; font-size: 17px; padding: 0 16px; min-height: 48px; border-radius: 4px; border: 1px solid var(--line); background: var(--surface); color: var(--fg); }
details.confirm { margin-top: 16px; } details.confirm summary { list-style: none; }
.report { display:flex; align-items:center; justify-content:center; min-height: 56px; border: 1px solid var(--report); border-radius: 4px; color: var(--report); font-weight: 600; cursor: pointer; }
details.confirm p { margin: 12px 0 8px; } .confirm-row { display:flex; gap: 8px; } .confirm-row button { flex: 1; min-height: 56px; } .report-go { background: var(--report); color: #fff; border: 0; font-weight: 700; }
details.info { margin-top: 32px; color: var(--muted); font-size: 14px; } details.info summary { min-height: 44px; display:flex; align-items:center; }
.bar { position: fixed; left:0; right:0; bottom:0; display:flex; flex-direction: column; gap: 4px; padding: 10px 16px calc(10px + env(safe-area-inset-bottom)); background: var(--surface); border-top: 1px solid var(--line); }
.bar .primary { display:flex; align-items:center; justify-content:center; text-decoration: none; border-radius: 4px; }
.bar button.primary, .bar a.primary { min-height: 60px; font-size: 19px; font-weight: 700; background: var(--primary); color: var(--on-primary); border: 0; width: 100%; }
.bar button.quiet { border: 0; background: transparent; color: var(--primary); }
.bar .hint { margin: 0; text-align: center; color: var(--muted); font-size: 15px; padding: 8px 0; }
#status { min-height: 1.5em; font-weight: 600; margin: 12px 0 0; }
a { color: var(--primary); }
`;
