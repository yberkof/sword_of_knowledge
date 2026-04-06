import { Question } from './types';

const QUESTIONS: Question[] = [
  {
    id: '1',
    text: 'في أي عام سقطت الإمبراطورية الرومانية الغربية؟',
    options: ['٤٧٦ م', '٣٩٥ م', '١٤٥٣ م', '٤١٠ م'],
    correctIndex: 0,
    category: 'تاريخ',
    difficulty: 'صعب'
  },
  {
    id: '2',
    text: 'من هو القائد المسلم الذي فتح الأندلس؟',
    options: ['خالد بن الوليد', 'طارق بن زياد', 'عمرو بن العاص', 'صلاح الدين الأيوبي'],
    correctIndex: 1,
    category: 'تاريخ',
    difficulty: 'سهل'
  },
  {
    id: '3',
    text: 'ما هي عاصمة الدولة العباسية في أوج ازدهارها؟',
    options: ['دمشق', 'القاهرة', 'بغداد', 'القيروان'],
    correctIndex: 2,
    category: 'تاريخ',
    difficulty: 'متوسط'
  },
  {
    id: '4',
    text: 'في أي معركة انتصر صلاح الدين الأيوبي على الصليبيين واستعاد القدس؟',
    options: ['معركة اليرموك', 'معركة القادسية', 'معركة حطين', 'معركة عين جالوت'],
    correctIndex: 2,
    category: 'تاريخ',
    difficulty: 'متوسط'
  },
  {
    id: '5',
    text: 'من هو الرحالة العربي الشهير الذي لقب بـ "أمير الرحالين"؟',
    options: ['ابن بطوطة', 'ابن خلدون', 'الإدريسي', 'ابن ماجد'],
    correctIndex: 0,
    category: 'تاريخ',
    difficulty: 'سهل'
  },
  {
    id: '6',
    text: 'ما هو الاسم القديم لمدينة إسطنبول؟',
    options: ['روما', 'القسطنطينية', 'أثينا', 'الإسكندرية'],
    correctIndex: 1,
    category: 'تاريخ',
    difficulty: 'سهل'
  }
];

export function getRandomQuestion(): Question {
  return QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
}

export function getNextQuestion(currentId: string): Question {
  const currentIndex = QUESTIONS.findIndex(q => q.id === currentId);
  const nextIndex = (currentIndex + 1) % QUESTIONS.length;
  return QUESTIONS[nextIndex];
}
