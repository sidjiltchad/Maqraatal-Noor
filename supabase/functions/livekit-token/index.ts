// دالة حافة Supabase تُصدر توكن LiveKit للمستخدمة المسجّلة فقط.
// الأسرار تبقى هنا (خادم)، ولا تُضمَّن في تطبيق الأندرويد إطلاقا.
//
// النشر:
//   supabase functions deploy livekit-token --no-verify-jwt=false
//   supabase secrets set LIVEKIT_URL=wss://xxx.livekit.cloud \
//     LIVEKIT_API_KEY=... LIVEKIT_API_SECRET=... LIVEKIT_ROOM=maqraa-main
//
// الاستدعاء من التطبيق:
//   POST /functions/v1/livekit-token  (Authorization: Bearer <access_token>)
//   → { token, url, room }

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { AccessToken } from "npm:livekit-server-sdk@2.6.1";

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

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

  // العميل يعمل بجلسة المستخدمة نفسها، فكل استعلام يمر بسياسات RLS.
  const supabase = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });

  const { data: userData, error: userError } = await supabase.auth.getUser();
  if (userError || !userData.user) {
    return json({ error: "unauthorized" }, 401);
  }
  const user = userData.user;

  // العضوية شرط دخول الغرفة (بدعوة فقط).
  const { data: member } = await supabase
    .from("halqa_members")
    .select("role,status")
    .eq("user_id", user.id)
    .maybeSingle();

  if (!member || member.status === "left") {
    return json({ error: "not_a_member" }, 403);
  }

  const { data: profile } = await supabase
    .from("profiles")
    .select("full_name,role")
    .eq("id", user.id)
    .single();

  const apiKey = Deno.env.get("LIVEKIT_API_KEY");
  const apiSecret = Deno.env.get("LIVEKIT_API_SECRET");
  const url = Deno.env.get("LIVEKIT_URL");
  const room = Deno.env.get("LIVEKIT_ROOM") ?? "maqraa-main";

  if (!apiKey || !apiSecret || !url) {
    return json({ error: "server_not_configured" }, 500);
  }

  const isStaff = profile?.role === "teacher" || profile?.role === "supervisor";

  const token = new AccessToken(apiKey, apiSecret, {
    identity: user.id,
    name: profile?.full_name ?? "عضوة",
    ttl: "2h",
  });

  token.addGrant({
    room,
    roomJoin: true,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
    // المعلمة وحدها تُدير الغرفة (كتم الآخرين مثلا).
    roomAdmin: isStaff,
  });

  return json({
    token: await token.toJwt(),
    url,
    room,
    role: profile?.role ?? "student",
  });
});
