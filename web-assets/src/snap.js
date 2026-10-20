// Screenshot helper loaded on demand by the bridge (exposes window.modernScreenshot).
import { domToPng } from 'modern-screenshot';

window.modernScreenshot = { domToPng };
