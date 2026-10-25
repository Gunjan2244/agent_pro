import { supabase } from "./supabase";

const MESSAGES: Record<string, string> = {
  unauthorized: "Please sign in again.",
  forbidden: "Only owners can do this.",
  mfa_required: "Verify your authenticator code first.",
  invalid: "Check the details and try again.",
  not_found: "That admin no longer exists.",
  last_owner: "There must be at least one other owner first.",
  self: "You can't change your own access.",
  failed: "That didn't work. Try again.",
};
export const NOT_DEPLOYED = "The admin service isn't deployed yet.";

// Calls the `admin` Edge Function. Returns the parsed success body, or throws Error with a plain message.
export async function callAdmin<T = Record<string, unknown>>(action: string, args: Record<string, unknown> = {}): Promise<T> {
  const { data, error } = await supabase.functions.invoke("admin", { body: { action, ...args } });
  if (!error) return data as T;
  const ctx = (error as { context?: unknown }).context;
  if (ctx instanceof Response) {
    if (ctx.status === 404) throw new Error(NOT_DEPLOYED);
    try {
      const body = (await ctx.json()) as { error?: string };
      throw new Error(MESSAGES[body.error ?? ""] ?? MESSAGES.failed);
    } catch (e) {
      if (e instanceof Error && Object.values(MESSAGES).includes(e.message)) throw e;
      throw new Error(MESSAGES.failed);
    }
  }
  throw new Error(NOT_DEPLOYED); // network failure or relay error: the function isn't reachable
}
