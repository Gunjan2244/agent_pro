// Company logo: square, WebP, <= catalog.offers.logoMaxBytes and <= catalog.offers.logoMaxPx (OFFERS.md, DECISIONS #47).
import catalog from "../../shared/catalog.json";

const limits = (catalog as unknown as { offers: { logoMaxBytes: number; logoMaxPx: number } }).offers;
export const LOGO_MAX_BYTES = limits.logoMaxBytes;
export const LOGO_MAX_PX = limits.logoMaxPx;

// Centre-crops to a square, scales down to at most LOGO_MAX_PX and lowers the quality until it fits the byte limit.
export async function toLogoWebp(file: File): Promise<Blob> {
  if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) throw new Error("Use a PNG, JPEG or WebP image.");
  const bmp = await createImageBitmap(file);
  const side = Math.min(bmp.width, bmp.height);
  const out = Math.min(side, LOGO_MAX_PX);
  const cv = document.createElement("canvas");
  cv.width = out; cv.height = out;
  const ctx = cv.getContext("2d");
  if (!ctx) throw new Error("This browser can't process images.");
  ctx.drawImage(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side, 0, 0, out, out);
  bmp.close();
  for (let q = 0.9; q >= 0.3; q -= 0.1) {
    const blob = await new Promise<Blob | null>((r) => cv.toBlob(r, "image/webp", q));
    if (blob && blob.type === "image/webp" && blob.size <= LOGO_MAX_BYTES) return blob;
  }
  throw new Error(`The logo is too complex to fit ${Math.round(LOGO_MAX_BYTES / 1024)} KB. Try a simpler or smaller image.`);
}
