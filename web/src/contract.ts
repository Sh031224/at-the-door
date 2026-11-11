/**
 * 차량 앱 ↔ 웹 서버 계약. 차량 쪽 구현은 car-app/platform/.../share/HttpSharePort.kt 이며 두 파일을 함께 바꾼다.
 * 이름·건강 정보·실시간 위치·이동 조건(보행 제약, 보조기구)은 이 계약에 넣지 않는다.
 */

/** POST /api/trips 요청 본문 */
export interface CreateTripRequest {
  venueName: string;
  planName: string;
  entranceName: string;
  /** data/venues/photos 아래 상대 경로 */
  entrancePhotos: string[];
  /** 입구 이미지가 시연용 일러스트이면 true. 페이지에 ‘실제 사진 아님’을 표시한다 */
  photosAreIllustrations: boolean;
  steps: string[];
  /** 차로 돌아가는 순서. 비어 있으면 올 때 순서를 거꾸로 보여 준다 */
  returnSteps: string[];
  parkingNote: string | null;
  /** 예: "데모 현장 조사 · 2026-11-01" */
  evidenceLabel: string;
  /** 시연 데이터 여부. 운영 데이터와 섞지 않는다 */
  demo: boolean;
  /** 대안이 없을 때 보여 줄 시설 연락처 */
  contact: string | null;
}

/** GET /api/trips/:token/plan 응답. 휴대폰 페이지가 재계획으로 바뀐 안내를 다시 읽는 데 쓴다 */
export interface PlanResponse {
  version: number;
  snapshot: CreateTripRequest;
}

export interface CreateTripResponse {
  token: string;
  url: string;
  /** epoch ms */
  expiresAt: number;
}

export const PHONE_EVENT_TYPES = ["arrived", "arrival_undone", "entrance_blocked", "returning", "parking_note"] as const;
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
    strArr("entrancePhotos") && strArr("steps") && strArr("returnSteps") &&
    typeof o.photosAreIllustrations === "boolean" && typeof o.demo === "boolean" &&
    (o.parkingNote === null || str("parkingNote")) &&
    (o.contact === null || str("contact"))
  );
}
