import { getAllMedicalIcons } from '../constants/medical-icons';

let preloaded = false;

export function preloadMedicalIcons() {
  if (preloaded) {
    return;
  }
  preloaded = true;

  for (const src of getAllMedicalIcons()) {
    uni.getImageInfo({
      src,
      fail: () => {
        // Ignore preload failures and let the image render normally.
      }
    });
  }
}
