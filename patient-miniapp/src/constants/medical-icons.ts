import bookOpenTextIcon from '../static/icons/book-open-text.svg';
import calendarDaysIcon from '../static/icons/calendar-days.svg';
import clipboardListIcon from '../static/icons/clipboard-list.svg';
import flaskConicalIcon from '../static/icons/flask-conical.svg';
import homeIcon from '../static/icons/home.svg';
import hospitalIcon from '../static/icons/hospital.svg';
import microscopeIcon from '../static/icons/microscope.svg';
import pillBottleIcon from '../static/icons/pill-bottle.svg';
import stethoscopeIcon from '../static/icons/stethoscope.svg';
import syringeIcon from '../static/icons/syringe.svg';
import userRoundPlusIcon from '../static/icons/user-round-plus.svg';
import walletCardsIcon from '../static/icons/wallet-cards.svg';
import bookOpenTextWhiteIcon from '../static/icons/white/book-open-text.svg';
import calendarDaysWhiteIcon from '../static/icons/white/calendar-days.svg';
import clipboardListWhiteIcon from '../static/icons/white/clipboard-list.svg';
import flaskConicalWhiteIcon from '../static/icons/white/flask-conical.svg';
import homeWhiteIcon from '../static/icons/white/home.svg';
import hospitalWhiteIcon from '../static/icons/white/hospital.svg';
import microscopeWhiteIcon from '../static/icons/white/microscope.svg';
import pillBottleWhiteIcon from '../static/icons/white/pill-bottle.svg';
import stethoscopeWhiteIcon from '../static/icons/white/stethoscope.svg';
import syringeWhiteIcon from '../static/icons/white/syringe.svg';
import userRoundPlusWhiteIcon from '../static/icons/white/user-round-plus.svg';
import walletCardsWhiteIcon from '../static/icons/white/wallet-cards.svg';
import bookOpenTextThemeIcon from '../static/icons/theme/book-open-text.svg';
import calendarDaysThemeIcon from '../static/icons/theme/calendar-days.svg';
import clipboardListThemeIcon from '../static/icons/theme/clipboard-list.svg';
import flaskConicalThemeIcon from '../static/icons/theme/flask-conical.svg';
import homeThemeIcon from '../static/icons/theme/home.svg';
import hospitalThemeIcon from '../static/icons/theme/hospital.svg';
import microscopeThemeIcon from '../static/icons/theme/microscope.svg';
import pillBottleThemeIcon from '../static/icons/theme/pill-bottle.svg';
import stethoscopeThemeIcon from '../static/icons/theme/stethoscope.svg';
import syringeThemeIcon from '../static/icons/theme/syringe.svg';
import userRoundPlusThemeIcon from '../static/icons/theme/user-round-plus.svg';
import walletCardsThemeIcon from '../static/icons/theme/wallet-cards.svg';

export const medicalIcons = {
  dark: {
    'book-open-text': bookOpenTextIcon,
    'calendar-days': calendarDaysIcon,
    'clipboard-list': clipboardListIcon,
    'flask-conical': flaskConicalIcon,
    home: homeIcon,
    hospital: hospitalIcon,
    microscope: microscopeIcon,
    'pill-bottle': pillBottleIcon,
    stethoscope: stethoscopeIcon,
    syringe: syringeIcon,
    'user-round-plus': userRoundPlusIcon,
    'wallet-cards': walletCardsIcon
  },
  white: {
    'book-open-text': bookOpenTextWhiteIcon,
    'calendar-days': calendarDaysWhiteIcon,
    'clipboard-list': clipboardListWhiteIcon,
    'flask-conical': flaskConicalWhiteIcon,
    home: homeWhiteIcon,
    hospital: hospitalWhiteIcon,
    microscope: microscopeWhiteIcon,
    'pill-bottle': pillBottleWhiteIcon,
    stethoscope: stethoscopeWhiteIcon,
    syringe: syringeWhiteIcon,
    'user-round-plus': userRoundPlusWhiteIcon,
    'wallet-cards': walletCardsWhiteIcon
  },
  theme: {
    'book-open-text': bookOpenTextThemeIcon,
    'calendar-days': calendarDaysThemeIcon,
    'clipboard-list': clipboardListThemeIcon,
    'flask-conical': flaskConicalThemeIcon,
    home: homeThemeIcon,
    hospital: hospitalThemeIcon,
    microscope: microscopeThemeIcon,
    'pill-bottle': pillBottleThemeIcon,
    stethoscope: stethoscopeThemeIcon,
    syringe: syringeThemeIcon,
    'user-round-plus': userRoundPlusThemeIcon,
    'wallet-cards': walletCardsThemeIcon
  }
} as const;

export type MedicalIconVariant = keyof typeof medicalIcons;
export type MedicalIconName = keyof typeof medicalIcons.dark;

export function getMedicalIcon(name: MedicalIconName, variant: MedicalIconVariant = 'dark') {
  return medicalIcons[variant][name];
}

export function getAllMedicalIcons() {
  return [...Object.values(medicalIcons.dark), ...Object.values(medicalIcons.white), ...Object.values(medicalIcons.theme)];
}
