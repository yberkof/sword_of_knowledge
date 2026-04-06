export type TiebreakerGameId = 'minefield' | 'rhythm' | 'rps' | 'closest';

export const TIEBREAKER_GAMES_UI: {
  id: TiebreakerGameId;
  title: string;
  blurb: string;
  emoji: string;
}[] = [
  {
    id: 'minefield',
    title: 'حقل الألغام',
    blurb: 'ضع 3 ألغام لخصمك، ثم تناوبا — من يطأ 3 ألغام يخسر.',
    emoji: '💣',
  },
  {
    id: 'rhythm',
    title: 'إيقاع الذاكرة',
    blurb: 'كرّر التسلسل؛ يبدأ سهلاً ويطول حتى يخطئ أحدكما.',
    emoji: '🥁',
  },
  { id: 'rps', title: 'حجر ورقة مقص', blurb: 'أفضل من 3 جولات.', emoji: '✊' },
  {
    id: 'closest',
    title: 'أقرب تخمين',
    blurb: 'سؤال معرفي برقم (سنة، عدد…) — الأقرب للصحيح يفوز.',
    emoji: '🎯',
  },
];

export function tiebreakerGameLabel(id: string): string {
  return TIEBREAKER_GAMES_UI.find((g) => g.id === id)?.title ?? id;
}
