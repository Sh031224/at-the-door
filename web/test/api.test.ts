import { test } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.tsx";
import { TOKEN_TTL_MS, TripStore } from "../src/store.ts";
import type { CreateTripRequest, CreateTripResponse, TripStatusResponse } from "../src/contract.ts";

const snapshot: CreateTripRequest = {
  venueName: "새봄문화회관 소공연장",
  planName: "후문 일반 주차 구역",
  entranceName: "후문",
  entrancePhotos: ["saebom-hall/e-back-1.jpg"],
  steps: ["파란 안내판을 지나 후문으로 이동"],
  conditionLabels: ["계단 없는 동선", "운전자와 함께 이동"],
  parkingNote: null,
  evidenceLabel: "데모 현장 조사 · 2026-11-01",
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
  assert.ok(html.includes("후문까지 함께 가요"));
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
