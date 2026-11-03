import { DatabaseSync } from "node:sqlite";
import { randomBytes, randomUUID } from "node:crypto";
import type { CreateTripRequest, PhoneEvent, PhoneEventType } from "./contract.ts";

export const TOKEN_TTL_MS = 24 * 60 * 60 * 1000;

export interface StoredTrip {
  token: string;
  snapshot: CreateTripRequest;
  createdAt: number;
  expiresAt: number;
}

/**
 * 공유 링크 저장소. 토큰은 128비트 난수(base64url)이고 24시간 후 만료된다.
 * 만료된 여행은 조회 시점과 sweep()에서 지운다.
 */
export class TripStore {
  private db: DatabaseSync;

  constructor(path = ":memory:", private now: () => number = Date.now) {
    this.db = new DatabaseSync(path);
    this.db.exec(`
      PRAGMA foreign_keys = ON;
      CREATE TABLE IF NOT EXISTS trips (
        token TEXT PRIMARY KEY,
        snapshot TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        expires_at INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS events (
        id TEXT PRIMARY KEY,
        token TEXT NOT NULL REFERENCES trips(token) ON DELETE CASCADE,
        type TEXT NOT NULL,
        note TEXT,
        created_at INTEGER NOT NULL
      );
    `);
  }

  create(snapshot: CreateTripRequest): StoredTrip {
    const token = randomBytes(16).toString("base64url");
    const createdAt = this.now();
    const expiresAt = createdAt + TOKEN_TTL_MS;
    this.db.prepare("INSERT INTO trips (token, snapshot, created_at, expires_at) VALUES (?, ?, ?, ?)")
      .run(token, JSON.stringify(snapshot), createdAt, expiresAt);
    return { token, snapshot, createdAt, expiresAt };
  }

  get(token: string): StoredTrip | null {
    const row = this.db.prepare("SELECT token, snapshot, created_at, expires_at FROM trips WHERE token = ?").get(token) as
      | { token: string; snapshot: string; created_at: number; expires_at: number }
      | undefined;
    if (!row) return null;
    if (row.expires_at <= this.now()) {
      this.delete(token);
      return null;
    }
    return { token: row.token, snapshot: JSON.parse(row.snapshot), createdAt: row.created_at, expiresAt: row.expires_at };
  }

  addEvent(token: string, type: PhoneEventType, note?: string): PhoneEvent | null {
    if (!this.get(token)) return null;
    const event: PhoneEvent = { id: randomUUID(), type, createdAt: this.now(), ...(note ? { note } : {}) };
    this.db.prepare("INSERT INTO events (id, token, type, note, created_at) VALUES (?, ?, ?, ?, ?)")
      .run(event.id, token, type, note ?? null, event.createdAt);
    return event;
  }

  events(token: string): PhoneEvent[] {
    const rows = this.db.prepare("SELECT id, type, note, created_at FROM events WHERE token = ? ORDER BY created_at, rowid").all(token) as
      Array<{ id: string; type: PhoneEventType; note: string | null; created_at: number }>;
    return rows.map((r) => ({ id: r.id, type: r.type, createdAt: r.created_at, ...(r.note ? { note: r.note } : {}) }));
  }

  delete(token: string): void {
    this.db.prepare("DELETE FROM trips WHERE token = ?").run(token);
  }

  sweep(): number {
    return Number(this.db.prepare("DELETE FROM trips WHERE expires_at <= ?").run(this.now()).changes);
  }
}
