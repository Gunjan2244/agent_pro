// Labels for catalog keys the panel shows. The keys come from shared/catalog.json (membershipStatuses,
// membershipRejectReasons, insuranceTypes); the panel is English only (docs/OFFERS.md, Technology).
import catalog from "../../shared/catalog.json";
import en from "../../shared/i18n/en.json";

type Entry = { key: string; labelKey: string; tone?: string };
const labels = en as Record<string, string>;
const list = (name: string): Entry[] => ((catalog as unknown as Record<string, Entry[]>)[name] ?? []);

export const label = (k: string) => labels[k] ?? k;
export const membershipStatuses = list("membershipStatuses");
export const rejectReasons = list("membershipRejectReasons");
export const insuranceTypes = list("insuranceTypes");
export const statusEntry = (key: string) => membershipStatuses.find((s) => s.key === key);
export const typeLabel = (key: string) => label(insuranceTypes.find((t) => t.key === key)?.labelKey ?? key);
export const adminRoles = list("adminRoles");
