// =============================================================================
//  create-general-supervisor — authenticated, owner-only account creation.
//
//  This function creates the actual General Supervisor Auth account (no
//  invitation, no email invite).  It never returns credentials or access
//  tokens, never logs the request body or the password, and keeps
//  SUPABASE_SERVICE_ROLE_KEY inside the Edge Function runtime.
//
//  Deploy (never commit secrets):
//    supabase functions deploy create-general-supervisor
//
//  Runtime secrets are provided by the platform:
//    SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const NAME_MIN = 2;
const NAME_MAX = 80;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 72;

/** Trim, collapse whitespace and remove control characters from a display name. */
function sanitizeName(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const name = value
    .replace(/\p{Cc}/gu, " ")
    .replace(/\s+/gu, " ")
    .trim();
  if (name.length < NAME_MIN || name.length > NAME_MAX) return null;
  return name;
}

function normalizeEmail(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const email = value.trim().toLowerCase();
  if (email.length === 0 || email.length > 254) return null;
  if (!EMAIL_PATTERN.test(email)) return null;
  return email;
}

/** Passwords are never trimmed: leading/trailing spaces are significant. */
function acceptPassword(value: unknown): string | null {
  if (typeof value !== "string") return null;
  if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX) return null;
  if (value.trim().length === 0) return null;
  // bcrypt only uses the first 72 bytes; reject longer UTF-8 values instead of
  // silently accepting a truncated password.
  if (new TextEncoder().encode(value).length > 72) return null;
  return value;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    return json({ error: "unauthorized" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    return json({ error: "server_not_configured" }, 500);
  }

  // Parse and validate before touching Auth.  The body is never logged.
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid_payload" }, 400);
  }
  const body = (typeof raw === "object" && raw !== null)
    ? raw as Record<string, unknown>
    : {};

  const fullName = sanitizeName(body.fullName);
  const email = normalizeEmail(body.email);
  const password = acceptPassword(body.password);
  if (!fullName || !email || !password) {
    return json({ error: "invalid_payload" }, 400);
  }

  // The caller client carries the caller's own JWT.  It is used only for
  // authentication and the no-argument public.is_owner() check.
  const caller = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: userData, error: userError } = await caller.auth.getUser();
  if (userError || !userData.user) {
    return json({ error: "unauthorized" }, 401);
  }

  const { data: isOwner, error: ownerError } = await caller.rpc("is_owner");
  if (ownerError) {
    return json({ error: "authorization_check_failed" }, 500);
  }
  if (isOwner !== true) {
    return json({ error: "owner_only" }, 403);
  }

  // The service-role key exists only in this server runtime.
  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: createData, error: createError } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: fullName },
  });

  const createdUser = createData?.user;
  if (createError || !createdUser) {
    const code = createError?.code ?? "";
    const message = createError?.message?.toLowerCase() ?? "";
    if (code === "email_exists" || message.includes("already") || message.includes("registered")) {
      return json({ error: "email_exists" }, 409);
    }
    if (code === "weak_password" || message.includes("password")) {
      return json({ error: "weak_password" }, 400);
    }
    return json({ error: "auth_create_failed" }, 500);
  }

  // Only the profile UUID is passed to SQL.  This RPC writes the supervisor
  // role, the active main-halqa membership and the audit row in one transaction.
  const { error: provisionError } = await admin.rpc("provision_general_supervisor", {
    p_user: createdUser.id,
  });

  if (provisionError) {
    // The Auth user was created by this request, so a failed provisioning must
    // not leave an orphan.  Never delete an existing/pre-existing user.
    const { error: cleanupError } = await admin.auth.admin.deleteUser(createdUser.id);
    if (cleanupError) {
      return json({ error: "cleanup_failed" }, 500);
    }

    switch (provisionError.code) {
      case "MQR02":
        return json({ error: "active_supervisor_exists" }, 409);
      case "MQR01":
        return json({ error: "owner_target_forbidden" }, 409);
      case "MQR03":
        return json({ error: "main_halqa_missing" }, 500);
      case "22023":
        return json({ error: "profile_missing" }, 422);
      default:
        return json({ error: "provision_failed" }, 500);
    }
  }

  // No credentials, tokens or generated password are returned.
  return json({ status: "created" }, 201);
});
