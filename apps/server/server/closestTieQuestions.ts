import { randomInt } from "node:crypto";

/** Numeric tiebreaker prompts — answer is the authoritative number (year, count, etc.). */
export type ClosestTieQuestion = {
  id: string;
  /** Arabic stem only; players type a number as the answer. */
  text: string;
  answer: number;
};

export const CLOSEST_TIE_QUESTIONS: ClosestTieQuestion[] = [
  { id: "ww1-start", text: "في أي عام بدأت الحرب العالمية الأولى؟", answer: 1914 },
  { id: "ww2-start", text: "في أي عام اندلعت الحرب العالمية الثانية؟", answer: 1939 },
  { id: "ww2-end", text: "في أي عام انتهت الحرب العالمية الثانية؟", answer: 1945 },
  { id: "constantinople", text: "في أي عام فتحت القسطنطينية (إسطنبول) على يد العثمانيين؟", answer: 1453 },
  { id: "french-rev", text: "في أي عام اندلعت الثورة الفرنسية؟", answer: 1789 },
  { id: "columbus", text: "في أي عام وصل كولومبوس إلى الأمريكتين (رحلته الشهيرة)؟", answer: 1492 },
  { id: "moon", text: "في أي عام هبط البشر على سطح القمر لأول مرة؟", answer: 1969 },
  { id: "berlin-wall", text: "في أي عام هُدم جدار برلين؟", answer: 1989 },
  { id: "hijra", text: "في أي عام كانت هجرة النبي ﷺ من مكة إلى يثرب (المدينة)؟", answer: 622 },
  { id: "leap-days", text: "كم يوماً في السنة الكبيسة؟", answer: 366 },
  { id: "gcc-count", text: "كم عدد الدول الأعضاء في مجلس التعاون لدول الخليج العربي؟", answer: 6 },
  { id: "pillars-islam", text: "كم عدد أركان الإسلام؟", answer: 5 },
  { id: "daily-prayers", text: "كم عدد الصلوات المفروضة في اليوم والليلة؟", answer: 5 },
  { id: "cairo-uni", text: "في أي عام تأسست جامعة القاهرة (افتتاحها كجامعة حديثة)؟", answer: 1908 },
  { id: "balfour", text: "في أي عام صدر وعد بلفور؟", answer: 1917 },
  { id: "sputnik", text: "في أي عام أُطلق القمر الصناعي سبوتنيك 1 (أول قمر صناعي)؟", answer: 1957 },
  { id: "water-boil-c", text: "عند الضغط الجوي العادي، كم درجة مئوية تقريباً لنقطة غليان الماء النقي؟", answer: 100 },
  { id: "earth-moons", text: "كم قمراً طبيعياً معروفاً للأرض؟", answer: 1 },
  { id: "planets-solar", text: "كم عدد كواكب النظام الشمسي (حسب التعريف الحديث)؟", answer: 8 },
];

export function pickRandomClosestTieQuestion(): ClosestTieQuestion {
  const i = randomInt(0, CLOSEST_TIE_QUESTIONS.length);
  return CLOSEST_TIE_QUESTIONS[i]!;
}
