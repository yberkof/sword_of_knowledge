import type express from "express";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { initializeApp, applicationDefault, cert } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function resolveAdminCredential() {
  const envPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (envPath && fs.existsSync(envPath)) {
    return cert(envPath);
  }
  const localJson = path.join(__dirname, "..", "..", "service-account.json");
  if (fs.existsSync(localJson)) {
    return cert(localJson);
  }
  return applicationDefault();
}

const firebaseConfigPath = path.join(__dirname, "..", "..", "firebase-applet-config.json");

let adminAuth: Auth;
if (fs.existsSync(firebaseConfigPath)) {
  const firebaseConfig = JSON.parse(fs.readFileSync(firebaseConfigPath, "utf8"));
  const app = initializeApp({
    credential: resolveAdminCredential(),
    projectId: firebaseConfig.projectId,
  });
  adminAuth = getAuth(app);
} else {
  const app = initializeApp({ credential: resolveAdminCredential() });
  adminAuth = getAuth(app);
}

export { adminAuth };

export async function requireFirebaseUser(
  req: express.Request,
  res: express.Response,
  next: express.NextFunction
) {
  const h = req.headers.authorization;
  if (!h?.startsWith("Bearer ")) {
    res.status(401).json({ error: "Unauthorized" });
    return;
  }
  try {
    const token = h.slice(7);
    const decoded = await adminAuth.verifyIdToken(token);
    (req as express.Request & { firebaseUid: string }).firebaseUid = decoded.uid;
    next();
  } catch {
    res.status(401).json({ error: "Invalid token" });
  }
}
