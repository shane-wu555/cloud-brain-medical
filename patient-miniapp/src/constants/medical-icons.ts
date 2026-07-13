export const medicalIcons = {
  dark: {
    'book-open-text': '/static/icons/book-open-text.svg',
    'calendar-days': '/static/icons/calendar-days.svg',
    'clipboard-list': '/static/icons/clipboard-list.svg',
    'flask-conical': '/static/icons/flask-conical.svg',
    home: '/static/icons/home.svg',
    hospital: '/static/icons/hospital.svg',
    microscope: '/static/icons/microscope.svg',
    'pill-bottle': '/static/icons/pill-bottle.svg',
    stethoscope: '/static/icons/stethoscope.svg',
    syringe: '/static/icons/syringe.svg',
    'user-round-plus': '/static/icons/user-round-plus.svg',
    'wallet-cards': '/static/icons/wallet-cards.svg'
  },
  white: {
    'book-open-text': '/static/icons/white/book-open-text.svg',
    'calendar-days': '/static/icons/white/calendar-days.svg',
    'clipboard-list': '/static/icons/white/clipboard-list.svg',
    'flask-conical': '/static/icons/white/flask-conical.svg',
    home: '/static/icons/white/home.svg',
    hospital: '/static/icons/white/hospital.svg',
    microscope: '/static/icons/white/microscope.svg',
    'pill-bottle': '/static/icons/white/pill-bottle.svg',
    stethoscope: '/static/icons/white/stethoscope.svg',
    syringe: '/static/icons/white/syringe.svg',
    'user-round-plus': '/static/icons/white/user-round-plus.svg',
    'wallet-cards': '/static/icons/white/wallet-cards.svg'
  },
  theme: {
    'book-open-text': '/static/icons/theme/book-open-text.svg',
    'calendar-days': '/static/icons/theme/calendar-days.svg',
    'clipboard-list': '/static/icons/theme/clipboard-list.svg',
    'flask-conical': '/static/icons/theme/flask-conical.svg',
    home: '/static/icons/theme/home.svg',
    hospital: '/static/icons/theme/hospital.svg',
    microscope: '/static/icons/theme/microscope.svg',
    'pill-bottle': '/static/icons/theme/pill-bottle.svg',
    stethoscope: '/static/icons/theme/stethoscope.svg',
    syringe: '/static/icons/theme/syringe.svg',
    'user-round-plus': '/static/icons/theme/user-round-plus.svg',
    'wallet-cards': '/static/icons/theme/wallet-cards.svg'
  }
} as const;

export type MedicalIconVariant = keyof typeof medicalIcons;
export type MedicalIconName = keyof typeof medicalIcons.dark;

export function getMedicalIcon(name: MedicalIconName, variant: MedicalIconVariant = 'dark') {
  return medicalIcons[variant][name];
}
