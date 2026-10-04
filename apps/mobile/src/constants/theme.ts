/**
 * Hawks CC design tokens. Mirrors design-system/hawks-cricket-club/MASTER.md and
 * design/prototype/styles.css (brand values are estimates until the club confirms them).
 */
export const Brand = {
  navy: '#1E2A78',
  navyStrong: '#141C52',
  navyDeep: '#0B1030',
  gold: '#F5B700',
  goldText: '#8A6200',
} as const;

export const Colors = {
  light: {
    background: '#F6F7FB',
    card: '#FFFFFF',
    text: '#0F1533',
    textSecondary: '#4A5272',
    muted: '#EEF0F7',
    border: '#D9DCEA',
    primary: Brand.navy,
    onPrimary: '#FFFFFF',
    accent: Brand.gold,
    onAccent: Brand.navyStrong,
    link: Brand.navy,
    success: '#15803D',
    successBg: '#E7F6EC',
    warning: '#B45309',
    warningBg: '#FEF3E2',
    danger: '#B91C1C',
    dangerBg: '#FDECEC',
    tabBar: '#FFFFFF',
  },
  dark: {
    background: '#0B1030',
    card: '#141B45',
    text: '#EEF0FA',
    textSecondary: '#A9B0D0',
    muted: '#1C2457',
    border: '#2A3370',
    primary: '#9DACFF',
    onPrimary: '#0B1030',
    accent: Brand.gold,
    onAccent: Brand.navyStrong,
    link: '#9DACFF',
    success: '#4ADE80',
    successBg: 'rgba(74,222,128,0.12)',
    warning: '#FBBF24',
    warningBg: 'rgba(251,191,36,0.12)',
    danger: '#F87171',
    dangerBg: 'rgba(248,113,113,0.12)',
    tabBar: '#141B45',
  },
} as const;

export type ThemeColors = { [K in keyof typeof Colors.light]: string };

export const Spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
export const Radius = { sm: 6, md: 10, lg: 16, full: 999 } as const;
export const MaxContentWidth = 720;
/** Minimum touch target (WCAG / platform guidance). */
export const MinTouch = 44;
