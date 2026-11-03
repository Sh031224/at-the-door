/**
 * 차량 앱 ↔ 웹 서버 계약. 차량 쪽 구현은 car-app/platform/.../share/HttpSharePort.kt 이며 두 파일을 함께 바꾼다.
 * 이름·건강 정보·실시간 위치는 이 계약에 넣지 않는다.
 */

/** POST /api/trips 요청 본문 */
export interface CreateTripRequest {
  venueName: string;
  planName: string;
  entranceName: string;
  /** data/venues/photos 아래 상대 경로 */
  entrancePhotos: string[];
  steps: string[];
  /** 예: "계단 없는 동선". 질병·장애 정보가 아니라 안내에 필요한 조건 이름만 */
  conditionLabels: string[];
  parkingNote: string | null;
  /** 예: "데모 현장 조사 · 2026-11-01" */
  evidenceLabel: string;
}

export interface CreateTripResponse {
  token: string;
  url: string;
  /** epoch ms */
  expiresAt: number;
}

export const PHONE_EVENT_TYPES = ["arrived", "entrance_blocked", "returning", "parking_note"] as const;
export type PhoneEventType = (typeof PHONE_EVENT_TYPES)[number];

/** POST /api/trips/:token/events 요청 본문 */
export interface PostEventRequest {
  type: PhoneEventType;
  note?: string;
}

export interface PhoneEvent {
  id: string;
  type: PhoneEventType;
  note?: string;
  createdAt: number;
}

/** GET /api/trips/:token 응답 */
export interface TripStatusResponse {
  events: PhoneEvent[];
}

export function isCreateTripRequest(v: unknown): v is CreateTripRequest {
  if (typeof v !== "object" || v === null) return false;
  const o = v as Record<string, unknown>;
  const str = (k: string) => typeof o[k] === "string" && (o[k] as string).length <= 200;
  const strArr = (k: string) => Array.isArray(o[k]) && (o[k] as unknown[]).length <= 20 && (o[k] as unknown[]).every((x) => typeof x === "string" && x.length <= 200);
  return (
    str("venueName") && str("planName") && str("entranceName") && str("evidenceLabel") &&
    strArr("entrancePhotos") && strArr("steps") && strArr("conditionLabels") &&
    (o.parkingNote === null || str("parkingNote"))
  );
}
