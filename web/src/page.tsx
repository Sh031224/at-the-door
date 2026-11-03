import type { FC } from "hono/jsx";
import type { StoredTrip } from "./store.ts";

/**
 * 휴대폰 안내 페이지. 읽기 중심이며 로그인·설치가 없다.
 * 실내 위치 추적이나 AR 방향은 띄우지 않는다. 사람의 도착은 사용자가 버튼으로 확인한다.
 */
export const GuidePage: FC<{ trip: StoredTrip }> = ({ trip }) => {
  const s = trip.snapshot;
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
        <main>
          <p class="venue">{s.venueName}</p>
          <h1>{s.entranceName}까지 함께 가요</h1>
          <p class="plan">{s.planName}</p>
          <ul class="chips">{s.conditionLabels.map((l) => <li>{l}</li>)}</ul>

          {s.entrancePhotos.length > 0 && (
            <section class="photos">
              {s.entrancePhotos.map((p) => <img src={`/photos/${p}`} alt={`${s.entranceName} 사진`} loading="lazy" />)}
            </section>
          )}

          <ol class="steps">{s.steps.map((step) => <li>{step}</li>)}</ol>

          <section class="note">
            <h2>주차 위치</h2>
            <p id="parking-note">{s.parkingNote ?? "아직 메모가 없어요"}</p>
            <form data-type="parking_note">
              <input name="note" maxlength={40} placeholder="예: 지하 2층 B-14" />
              <button type="submit">메모 저장</button>
            </form>
          </section>

          <section class="actions">
            <button data-type="arrived" class="primary">입구에 도착했어요</button>
            <button data-type="entrance_blocked">이 입구를 이용할 수 없어요</button>
            <button data-type="returning">차로 돌아가기</button>
          </section>
          <p id="status" role="status" aria-live="polite"></p>

          <footer>
            <p>정보 출처: {s.evidenceLabel}</p>
            <p>실제 통행 가능 여부는 현장에서 확인해 주세요. 이 링크는 24시간 뒤 사라져요.</p>
          </footer>
        </main>
        <script dangerouslySetInnerHTML={{ __html: script(trip.token) }} />
      </body>
    </html>
  );
};

export const ExpiredPage: FC = () => (
  <html lang="ko">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
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

const script = (token: string) => `
const status = document.getElementById("status");
const labels = { arrived: "도착을 알렸어요", entrance_blocked: "차량 화면에서 다른 입구를 찾고 있어요", returning: "복귀 안내를 시작해요", parking_note: "메모를 저장했어요" };
async function send(type, note) {
  try {
    const res = await fetch("/api/trips/${token}/events", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(note ? { type, note } : { type }) });
    status.textContent = res.ok ? labels[type] : "전송하지 못했어요. 다시 눌러 주세요.";
    if (res.ok && type === "parking_note") document.getElementById("parking-note").textContent = note;
  } catch { status.textContent = "연결이 끊겼어요. 화면의 사진과 순서를 참고해 주세요."; }
}
document.querySelectorAll("button[data-type]").forEach((b) => b.addEventListener("click", () => send(b.dataset.type)));
document.querySelector("form[data-type]").addEventListener("submit", (e) => {
  e.preventDefault();
  const note = e.target.note.value.trim();
  if (note) send("parking_note", note);
});
`;

const css = `
:root { color-scheme: light dark; --fg:#111; --bg:#fff; --muted:#666; --accent:#1859c9; --line:#ddd; }
@media (prefers-color-scheme: dark) { :root { --fg:#f2f2f2; --bg:#111; --muted:#aaa; --accent:#7aa7ff; --line:#333; } }
* { box-sizing: border-box; }
body { margin:0; font: 18px/1.6 system-ui, -apple-system, "Apple SD Gothic Neo", sans-serif; color:var(--fg); background:var(--bg); }
main { max-width: 560px; margin: 0 auto; padding: 20px 16px 48px; }
h1 { font-size: 1.6rem; margin: 4px 0; } h2 { font-size: 1.1rem; margin: 0 0 4px; }
.venue, .plan, footer { color: var(--muted); } footer { font-size: .85rem; margin-top: 32px; }
.chips { display:flex; flex-wrap:wrap; gap:6px; padding:0; list-style:none; }
.chips li { border:1px solid var(--line); border-radius: 999px; padding: 2px 10px; font-size:.9rem; }
.photos img { width:100%; border-radius: 12px; margin: 8px 0; }
.steps li { margin: 8px 0; font-size: 1.15rem; }
.note { border-top:1px solid var(--line); padding-top: 12px; margin-top: 16px; }
.note form { display:flex; gap:8px; } .note input { flex:1; font-size:1rem; padding:10px; }
.actions { display:grid; gap:10px; margin-top: 20px; }
button { font-size: 1.05rem; padding: 14px; border-radius: 12px; border:1px solid var(--line); background: transparent; color: var(--fg); }
button.primary { background: var(--accent); color: #fff; border: none; }
`;
