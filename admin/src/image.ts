// Offer image: one per offer, 16:9, WebP, <= catalog.offers.imageMaxBytes (DECISIONS #101).
import catalog from "../../shared/catalog.json";

const limits = (catalog as unknown as { offers: { imageMaxBytes: number } }).offers;
export const MAX_BYTES = limits.imageMaxBytes;
const W = 1280, H = 720;

// Centre-crops to 16:9, scales to 1280x720 and lowers the quality until it fits the byte limit.
export async function toOfferWebp(file: File): Promise<Blob> {
  const bmp = await createImageBitmap(file);
  const scale = Math.max(W / bmp.width, H / bmp.height);
  const cv = document.createElement("canvas");
  cv.width = W; cv.height = H;
  const ctx = cv.getContext("2d")!;
  ctx.drawImage(bmp, (W - bmp.width * scale) / 2, (H - bmp.height * scale) / 2, bmp.width * scale, bmp.height * scale);
  for (let q = 0.85; q >= 0.35; q -= 0.1) {
    const blob = await new Promise<Blob | null>((r) => cv.toBlob(r, "image/webp", q));
    if (blob && blob.size <= MAX_BYTES) return blob;
  }
  throw new Error("Image is too complex to fit 512 KB. Try a simpler one.");
}
