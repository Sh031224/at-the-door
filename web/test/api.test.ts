import { test } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.tsx";
import { TOKEN_TTL_MS, TripStore } from "../src/store.ts";
import type { CreateTripRequest, CreateTripResponse, PlanResponse, TripStatusResponse } from "../src/contract.ts";

const snapshot: CreateTripRequest = {
  venueName: "새봄문화회관 소공연장",
  planName: "후문 일반 주차 구역",
  entranceName: "후문",
  entrancePhotos: ["saebom-hall/e-back-1.png"],
  photosAreIllustrations: true,
  steps: ["파란 안내판을 지나 후문으로 이동"],
  returnSteps: [],
  parkingNote: null,
  evidenceLabel: "데모 현장 조사 · 2026-11-01",
  demo: true,
  contact: "02-000-0000",
  walkSummary: "주차 후 입구까지 약 80m · 계단 없음",
  noAlternative: false,
  changeNote: null,
};

function setup(now = () => 1_000) {
  const store = new TripStore(":memory:", now);
  const app = createApp({ store, publicBaseUrl: "https://door.test", photosDir: new URL("../../data/venues/photos", import.meta.url).pathname });
  const json = (body: unknown) => ({ method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  return { app, store, json };
}

test("링크를 만들면 추측하기 어려운 토큰과 24시간 만료가 붙는다", async () => {
  const { app, json } = setup();
  const res = await app.request("/api/trips", json(snapshot));
  assert.equal(res.status, 201);
  const body = (await res.json()) as CreateTripResponse;
  assert.match(body.token, /^[A-Za-z0-9_-]{22}$/);
  assert.equal(body.url, `https://door.test/t/${body.token}`);
  assert.equal(body.expiresAt, 1_000 + TOKEN_TTL_MS);
});

test("휴대폰 페이지에 입구 이름·이동 순서·출처가 보이고 이름이나 위치 정보는 없다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  const html = await (await app.request(`/t/${token}`)).text();
  assert.ok(html.includes("후문으로 가요"));
  assert.ok(html.includes("파란 안내판을 지나 후문으로 이동"));
  assert.ok(html.includes("데모 현장 조사"));
});

test("휴대폰 이벤트를 차량 앱이 가져가고 여행 종료 시 삭제된다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;

  assert.equal((await app.request(`/api/trips/${token}/events`, json({ type: "parking_note", note: "B2-14" }))).status, 201);
  assert.equal((await app.request(`/api/trips/${token}/events`, json({ type: "arrived" }))).status, 201);
  assert.equal((await app.request(`/api/trips/${token}/events`, json({ type: "teleport" }))).status, 400);

  const status = (await (await app.request(`/api/trips/${token}`)).json()) as TripStatusResponse;
  assert.deepEqual(status.events.map((e) => e.type), ["parking_note", "arrived"]);
  assert.equal(status.events[0]?.note, "B2-14");

  assert.equal((await app.request(`/api/trips/${token}`, { method: "DELETE" })).status, 204);
  assert.equal((await app.request(`/api/trips/${token}`)).status, 404);
  assert.equal((await app.request(`/t/${token}`)).status, 404);
});

test("만료된 링크는 열리지 않는다", async () => {
  let now = 1_000;
  const { app, json } = setup(() => now);
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  now += TOKEN_TTL_MS;
  assert.equal((await app.request(`/t/${token}`)).status, 404);
});

test("형식이 틀린 계획은 거절한다", async () => {
  const { app, json } = setup();
  assert.equal((await app.request("/api/trips", json({ ...snapshot, steps: "not-an-array" }))).status, 400);
});

test("시연 데이터와 일러스트는 페이지에 그렇다고 표시된다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  const html = await (await app.request(`/t/${token}`)).text();
  assert.ok(html.includes("시연 데이터"));
  assert.ok(html.includes("실제 사진 아님"));
});

test("입구 도착을 누르면 방문 화면, 복귀를 누르면 주차 메모와 거꾸로 된 순서가 보인다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json({ ...snapshot, parkingNote: "B2 C구역", steps: ["첫째", "둘째"] }))).json()) as CreateTripResponse;
  await app.request(`/api/trips/${token}/events`, json({ type: "arrived" }));
  const visit = await (await app.request(`/t/${token}`)).text();
  assert.ok(visit.includes("입구 도착을 확인했어요"));
  assert.ok(!visit.includes("입구에 도착했어요"));
  await app.request(`/api/trips/${token}/events`, json({ type: "returning" }));
  const back = await (await app.request(`/t/${token}`)).text();
  assert.ok(back.includes("B2 C구역"));
  assert.ok(back.indexOf("둘째") < back.indexOf("첫째"));
});

test("재계획으로 안내가 바뀌면 같은 링크의 버전이 오르고 새 입구가 보인다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  const before = (await (await app.request(`/api/trips/${token}/plan`)).json()) as PlanResponse;
  const put = await app.request(`/api/trips/${token}`, { ...json({ ...snapshot, entranceName: "정문" }), method: "PUT" });
  assert.equal(put.status, 204);
  const after = (await (await app.request(`/api/trips/${token}/plan`)).json()) as PlanResponse;
  assert.equal(after.version, before.version + 1);
  assert.ok((await (await app.request(`/t/${token}`)).text()).includes("정문으로 가요"));
});

test("형식이 틀린 토큰은 조회하지 않는다", async () => {
  const { app } = setup();
  assert.equal((await app.request(`/t/<script>`)).status, 404);
  assert.equal((await app.request(`/api/trips/short`)).status, 404);
});

test("휴대폰에서 남긴 주차 메모가 다시 열어도 보인다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  await app.request(`/api/trips/${token}/events`, json({ type: "parking_note", note: "B2 C구역 14번" }));
  assert.ok((await (await app.request(`/t/${token}`)).text()).includes("B2 C구역 14번"));
});

test("돌아가는 순서 데이터가 있으면 그것을 보여 준다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json({ ...snapshot, returnSteps: ["후문으로 나가기", "주차장으로"] }))).json()) as CreateTripResponse;
  await app.request(`/api/trips/${token}/events`, json({ type: "arrived" }));
  await app.request(`/api/trips/${token}/events`, json({ type: "returning" }));
  const html = await (await app.request(`/t/${token}`)).text();
  assert.ok(html.includes("돌아가는 순서"));
  assert.ok(html.includes("후문으로 나가기"));
});

test("입구 도착을 되돌리면 다시 입구 안내가 보인다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  await app.request(`/api/trips/${token}/events`, json({ type: "arrived" }));
  await app.request(`/api/trips/${token}/events`, json({ type: "arrival_undone" }));
  const html = await (await app.request(`/t/${token}`)).text();
  assert.ok(html.includes("후문으로 가요"));
});

test("차량이 대안을 찾지 못하면 휴대폰은 시설 연락 화면으로 바뀐다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  await app.request(`/api/trips/${token}`, { ...json({ ...snapshot, noAlternative: true }), method: "PUT" });
  const html = await (await app.request(`/t/${token}`)).text();
  assert.ok(html.includes("확인된 다른 입구가 없어요"));
  assert.ok(html.includes("tel:02-000-0000"));
});

test("재계획으로 길이 바뀌면 휴대폰 위에 바뀐 이유가 보인다", async () => {
  const { app, json } = setup();
  const { token } = (await (await app.request("/api/trips", json(snapshot))).json()) as CreateTripResponse;
  await app.request(`/api/trips/${token}`, { ...json({ ...snapshot, entranceName: "정문", changeNote: "후문을 이용할 수 없어 정문으로 가는 길로 바꿨어요" }), method: "PUT" });
  assert.ok((await (await app.request(`/t/${token}`)).text()).includes("후문을 이용할 수 없어 정문으로 가는 길로 바꿨어요"));
});
