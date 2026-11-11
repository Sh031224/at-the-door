import { Hono } from "hono";
import { serveStatic } from "@hono/node-server/serve-static";
import { PHONE_EVENT_TYPES, isCreateTripRequest, type CreateTripResponse, type PhoneEventType, type PlanResponse, type TripStatusResponse } from "./contract.ts";
import { ExpiredPage, GuidePage } from "./page.tsx";
import type { TripStore } from "./store.ts";

export interface AppOptions {
  store: TripStore;
  /** QR에 들어갈 공개 주소. 예: https://door.example.com */
  publicBaseUrl: string;
  /** 저장소 루트의 data/venues/photos */
  photosDir: string;
}

export function createApp({ store, publicBaseUrl, photosDir }: AppOptions) {
  const app = new Hono();
  const TOKEN = /^[A-Za-z0-9_-]{22}$/;

  // 토큰 형식이 틀리면 저장소를 조회하지 않는다(페이지 스크립트에 토큰을 그대로 넣으므로 형식을 보장한다).
  app.use("/t/:token", async (c, next) => (TOKEN.test(c.req.param("token")) ? next() : c.html(<ExpiredPage />, 404)));
  app.use("/api/trips/:token/*", async (c, next) => (TOKEN.test(c.req.param("token")) ? next() : c.json({ error: "not_found" }, 404)));
  app.use("/api/trips/:token", async (c, next) => (TOKEN.test(c.req.param("token")) ? next() : c.json({ error: "not_found" }, 404)));

  app.get("/healthz", (c) => c.text("ok"));

  app.use("/photos/*", serveStatic({ root: photosDir, rewriteRequestPath: (p) => p.replace(/^\/photos/, "") }));

  // 차량 앱: 계획 공유 링크 발급
  app.post("/api/trips", async (c) => {
    const body = await c.req.json().catch(() => null);
    if (!isCreateTripRequest(body)) return c.json({ error: "invalid_body" }, 400);
    const trip = store.create(body);
    const res: CreateTripResponse = { token: trip.token, url: `${publicBaseUrl.replace(/\/$/, "")}/t/${trip.token}`, expiresAt: trip.expiresAt };
    return c.json(res, 201);
  });

  // 차량 앱: 휴대폰에서 기록한 이벤트 동기화
  app.get("/api/trips/:token", (c) => {
    const token = c.req.param("token");
    if (!store.get(token)) return c.json({ error: "not_found" }, 404);
    const res: TripStatusResponse = { events: store.events(token) };
    return c.json(res);
  });

  // 차량 앱: 재계획으로 바뀐 안내 내용 반영(같은 링크 유지)
  app.put("/api/trips/:token", async (c) => {
    const body = await c.req.json().catch(() => null);
    if (!isCreateTripRequest(body)) return c.json({ error: "invalid_body" }, 400);
    const trip = store.update(c.req.param("token"), body);
    if (!trip) return c.json({ error: "not_found" }, 404);
    return c.body(null, 204);
  });

  // 휴대폰: 현재 안내 내용과 버전. 버전이 바뀌면 페이지를 새로 그린다
  app.get("/api/trips/:token/plan", (c) => {
    c.header("Cache-Control", "no-store");
    const trip = store.get(c.req.param("token"));
    if (!trip) return c.json({ error: "not_found" }, 404);
    const res: PlanResponse = { version: trip.version, snapshot: trip.snapshot };
    return c.json(res);
  });

  // 차량 앱: 여행 종료 시 즉시 삭제
  app.delete("/api/trips/:token", (c) => {
    store.delete(c.req.param("token"));
    return c.body(null, 204);
  });

  // 휴대폰: 도착·이용 불가·복귀·주차 메모
  app.post("/api/trips/:token/events", async (c) => {
    const body = (await c.req.json().catch(() => null)) as { type?: unknown; note?: unknown } | null;
    const type = body?.type;
    if (typeof type !== "string" || !PHONE_EVENT_TYPES.includes(type as PhoneEventType)) return c.json({ error: "invalid_type" }, 400);
    const note = typeof body?.note === "string" ? body.note.slice(0, 40) : undefined;
    if (type === "parking_note" && !note) return c.json({ error: "note_required" }, 400);
    const event = store.addEvent(c.req.param("token"), type as PhoneEventType, note);
    if (!event) return c.json({ error: "not_found" }, 404);
    return c.json(event, 201);
  });

  // 휴대폰: 안내 페이지
  app.get("/t/:token", (c) => {
    c.header("Cache-Control", "no-store");
    c.header("Referrer-Policy", "no-referrer");
    const trip = store.get(c.req.param("token"));
    if (!trip) return c.html(<ExpiredPage />, 404);
    return c.html(<GuidePage trip={trip} events={store.events(trip.token)} />);
  });

  return app;
}
