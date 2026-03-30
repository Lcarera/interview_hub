// scripts/get-calendar-token.ts
//
// Obtains a Google OAuth2 refresh token for the calendar.events scope.
//
// Prerequisites:
//   1. Go to Google Cloud Console → APIs & Services → OAuth consent screen
//   2. Click "Publish App" — tokens from "Testing" mode expire after 7 days
//   3. Ensure GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set in .env or environment
//
// Usage:
//   bun run scripts/get-calendar-token.ts
//
// Copy the printed GOOGLE_CALENDAR_REFRESH_TOKEN= line into your .env file.

const CLIENT_ID = process.env.GOOGLE_CLIENT_ID;
const CLIENT_SECRET = process.env.GOOGLE_CLIENT_SECRET;

if (!CLIENT_ID || !CLIENT_SECRET) {
  console.error("Error: GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must be set in the environment or .env file.");
  process.exit(1);
}

const REDIRECT_PORT = 8888;
const REDIRECT_URI = `http://localhost:${REDIRECT_PORT}/callback`;
const SCOPE = "https://www.googleapis.com/auth/calendar.events";

const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
authUrl.searchParams.set("client_id", CLIENT_ID);
authUrl.searchParams.set("redirect_uri", REDIRECT_URI);
authUrl.searchParams.set("response_type", "code");
authUrl.searchParams.set("scope", SCOPE);
authUrl.searchParams.set("access_type", "offline");
authUrl.searchParams.set("prompt", "consent");

console.log("Opening browser to Google OAuth consent page...\n");

const { exec } = await import("child_process");
const openCmd =
  process.platform === "darwin" ? "open"
  : process.platform === "win32" ? "start"
  : "xdg-open";
exec(`${openCmd} "${authUrl.toString()}"`);

const code = await new Promise<string>((resolve) => {
  const server = Bun.serve({
    port: REDIRECT_PORT,
    fetch(req) {
      const url = new URL(req.url);
      if (url.pathname === "/callback") {
        const code = url.searchParams.get("code");
        if (code) {
          resolve(code);
          setTimeout(() => server.stop(), 100);
          return new Response("Authorization successful! You can close this tab.");
        }
      }
      return new Response("Waiting for authorization...");
    },
  });
  console.log(`Waiting for OAuth callback on port ${REDIRECT_PORT}...`);
});

const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({
    code,
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    redirect_uri: REDIRECT_URI,
    grant_type: "authorization_code",
  }),
});

const tokens = (await tokenResponse.json()) as {
  refresh_token?: string;
  error?: string;
  error_description?: string;
};

if (tokens.error || !tokens.refresh_token) {
  console.error("Error exchanging code for tokens:", tokens.error_description ?? tokens.error ?? "No refresh_token in response");
  process.exit(1);
}

console.log("\nSuccess! Add this to your .env file:\n");
console.log(`GOOGLE_CALENDAR_REFRESH_TOKEN=${tokens.refresh_token}\n`);
