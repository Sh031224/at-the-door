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
  return (
    <html lang="ko">
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="robots" content="noindex" />
        <title>{`${s.entranceName}까지 안내 · 문앞`}</title>
        <style>{css}</style>
      </head>
      <body>
        <main data-stage={stage}>
          {s.demo && <p class="demo">시연 데이터 · 실제 시설 검증 결과가 아니에요</p>}
          <p class="venue">{s.venueName}</p>

          {stage === "visit" ? (
            <>
              <h1>입구 도착을 확인했어요</h1>
              <p class="lead">좋은 시간 보내세요. 돌아갈 때 아래 버튼을 눌러 주세요.</p>
              <section class="note">
                <h2>주차 위치</h2>
                <p class="big">{s.parkingNote ?? "메모 없음"}</p>
              </section>
              <section class="actions">
                <button data-type="returning" class="primary">차로 돌아가기</button>
                <button data-type="arrival_undone">아직 도착하지 않았어요 (안내로 돌아가기)</button>
              </section>
            </>
          ) : (
            <>
              <h1>{stage === "return" ? "차로 돌아가는 길" : `${s.entranceName}까지 함께 가요`}</h1>
              <p class="plan">{s.planName}</p>

              {stage === "return" && (
                <section class="note">
                  <h2>주차 위치</h2>
                  <p class="big">{s.parkingNote ?? "메모 없음"}</p>
                </section>
              )}

              <h2>{stage === "return" ? (hasReturnSteps ? "돌아가는 순서" : "올 때 지나온 길 (거꾸로 따라가세요)") : "이동 순서"}</h2>
              <ol class="steps">{steps.map((step) => <li>{step}</li>)}</ol>

              {s.entrancePhotos.length > 0 && (
                <section class="photos">
                  <h2>{stage === "return" ? "나갈 문" : "도착할 입구"}: {s.entranceName}</h2>
                  {s.entrancePhotos.map((p) => (
                    <img src={`/photos/${p}`} alt={s.photosAreIllustrations ? `${s.entranceName} 시연용 일러스트(실제 사진 아님)` : `${s.entranceName} 사진`} loading="lazy" />
                  ))}
                </section>
              )}

              {stage === "guide" && (
                <section class="note">
                  <h2>주차 위치 메모</h2>
                  <p id="parking-note">{s.parkingNote ?? "아직 메모가 없어요"}</p>
                  <form data-type="parking_note">
                    <input name="note" maxlength={40} placeholder="예: 지하 2층 B-14" aria-label="주차 위치 메모" />
                    <button type="submit">저장</button>
                  </form>
                </section>
              )}

              <section class="actions">
                {stage === "guide" && <button data-type="arrived" class="primary">입구에 도착했어요</button>}
                <button data-type="entrance_blocked">{stage === "return" ? "길이 막혀 있어요 · 다른 길 찾기" : "입구가 막혀 있어요 · 다른 입구 찾기"}</button>
              </section>
            </>
          )}
          <p id="status" role="status" aria-live="polite"></p>

          <footer>
            <p>정보 출처: {s.evidenceLabel}</p>
            <p>실제 통행 가능 여부는 현장에서 확인해 주세요. 이 링크는 24시간 뒤 사라져요. 이름·건강 정보·이동 조건·GPS 위치는 담지 않고, 직접 남긴 주차 메모만 담아요.</p>
            {s.contact && <p>막혀 있고 대안이 없으면 시설에 문의해 주세요: <a href={`tel:${s.contact}`}>{s.contact}</a>{s.demo && " (시연용 번호)"}</p>}
          </footer>
        </main>
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
  for (let i = 0; i < 24; i++) {
    await new Promise((r) => setTimeout(r, 5000));
    try {
      const res = await fetch("/api/trips/${token}/plan", { cache: "no-store" });
      if (!res.ok) break;
      const body = await res.json();
      if (body.version !== version) { location.reload(); return; }
    } catch {}
  }
  status.textContent = "새 안내를 받지 못했어요. 차량 화면을 확인하거나 시설에 문의해 주세요.";
}
document.querySelectorAll("button[data-type]").forEach((b) => b.addEventListener("click", () => send(b.dataset.type)));
const form = document.querySelector("form[data-type]");
if (form) form.addEventListener("submit", (e) => {
  e.preventDefault();
  const note = e.target.note.value.trim();
  if (note) send("parking_note", note);
});
`;

const css = `
:root { color-scheme: light dark; --fg:#15181d; --bg:#fff; --muted:#5b6370; --accent:#1b4fa0; --line:#d6dae0; --warn-bg:#fcebd2; --warn-fg:#4a2b00; --on-accent:#fff; }
@media (prefers-color-scheme: dark) { :root { --fg:#f2f2f2; --bg:#111418; --muted:#aab2bd; --accent:#8db4f5; --on-accent:#0d2a57; --line:#333a44; --warn-bg:#4a2b00; --warn-fg:#fcebd2; } }
* { box-sizing: border-box; }
body { margin:0; font: 18px/1.6 system-ui, -apple-system, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif; color:var(--fg); background:var(--bg); }
main { max-width: 560px; margin: 0 auto; padding: 20px 16px 48px; word-break: keep-all; overflow-wrap: anywhere; }
img { max-width: 100%; }
h1 { font-size: 1.6rem; line-height: 1.3; margin: 4px 0 8px; } h2 { font-size: 1.1rem; margin: 20px 0 6px; }
.demo { background: var(--warn-bg); color: var(--warn-fg); border-radius: 10px; padding: 8px 12px; font-size: .9rem; margin: 0 0 8px; }
.venue, .plan, .lead, footer { color: var(--muted); } footer { font-size: .85rem; margin-top: 32px; }
.chips { display:flex; flex-wrap:wrap; gap:6px; padding:0; list-style:none; }
.chips li { border:1px solid var(--line); border-radius: 999px; padding: 2px 10px; font-size:.9rem; }
.photos img { width:100%; border-radius: 12px; display:block; margin: 8px 0; }
.steps { padding-left: 1.4em; } .steps li { margin: 10px 0; font-size: 1.15rem; }
.note { border-top:1px solid var(--line); padding-top: 4px; margin-top: 16px; }
.big { font-size: 1.5rem; font-weight: 700; margin: 4px 0; }
html, body { overflow-x: hidden; }
.note form { display:flex; flex-wrap: wrap; gap:8px; } .note form button { flex: 0 0 auto; } .note input { flex:1; min-width:0; font-size:1rem; padding:12px; border-radius: 10px; border:1px solid var(--line); background: transparent; color: var(--fg); }
.actions { display:grid; gap:10px; margin-top: 24px; }
button { font-size: 1.05rem; padding: 16px; border-radius: 12px; border:1px solid var(--line); background: transparent; color: var(--fg); min-height: 56px; }
button.primary { background: var(--accent); color: var(--on-accent); border: none; font-weight: 700; }
#status { min-height: 1.6em; font-weight: 600; }
a { color: var(--accent); }
`;
