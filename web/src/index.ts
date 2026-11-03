import { serve } from "@hono/node-server";
import { fileURLToPath } from "node:url";
import { createApp } from "./app.tsx";
import { TripStore } from "./store.ts";

const port = Number(process.env.PORT ?? 8787);
const publicBaseUrl = process.env.PUBLIC_BASE_URL ?? `http://localhost:${port}`;
const dbPath = process.env.DB_PATH ?? "trips.sqlite";
const photosDir = process.env.PHOTOS_DIR ?? fileURLToPath(new URL("../../data/venues/photos", import.meta.url));

const store = new TripStore(dbPath);
setInterval(() => store.sweep(), 10 * 60 * 1000).unref();

serve({ fetch: createApp({ store, publicBaseUrl, photosDir }).fetch, port }, (info) => {
  console.log(`문앞 web: http://localhost:${info.port} (QR 주소: ${publicBaseUrl})`);
});
