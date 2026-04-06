import React from 'react';
import { motion } from 'motion/react';
import { Hammer, Shield, Eye, ArrowLeft, Sword } from 'lucide-react';
import { auth } from '../firebase';
import { apiUrl } from '../apiConfig';

interface ArmoryProps {
  onBack: () => void;
}

export default function Armory({ onBack }: ArmoryProps) {
  const purchase = async (itemId: string, costGold: number) => {
    const u = auth.currentUser;
    if (!u) return;
    try {
      const token = await u.getIdToken();
      const r = await fetch(apiUrl('/api/shop/purchase'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ itemId, costGold }),
      });
      const j = (await r.json()) as { ok?: boolean };
      if (j.ok) alert('تم الشراء (مزامنة مع قاعدة البيانات)');
      else alert('تعذر الشراء');
    } catch {
      alert('خطأ شبكة');
    }
  };

  const items = [
    { id: 'hammer', name: 'المطرقة', description: 'حذف إجابتين خاطئتين (50/50)', price: 500, icon: <Hammer />, accent: 'text-primary' },
    { id: 'shield', name: 'الدرع', description: 'يمنع هجوماً واحداً على قلعتك', price: 1200, icon: <Shield />, accent: 'text-copper' },
    { id: 'spyglass', name: 'المنظار', description: 'كشف فئة السؤال قبل الهجوم', price: 300, icon: <Eye />, accent: 'text-blue-800' },
    { id: 'sword', name: 'السيف الذهبي', description: 'زيادة ضرر الهجوم بنسبة 20%', price: 2500, icon: <Sword />, accent: 'text-gold' },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 20 }}
      className="bg-parchment flex h-full w-full flex-col space-y-6 overflow-y-auto p-6 text-ink"
    >
      <div className="wood-shelf shadow-heavy relative overflow-hidden rounded-t-lg px-6 py-5 text-background">
        <div className="absolute inset-0 bg-parchment opacity-10" />
        <div className="relative z-10 flex items-center justify-between gap-4">
          <button
            type="button"
            onClick={onBack}
            className="rounded-full border border-background/30 p-2 transition-colors hover:bg-background/10"
            aria-label="رجوع"
          >
            <ArrowLeft className="h-6 w-6" />
          </button>
          <div className="text-end">
            <div className="text-gold mb-1 flex items-center justify-end gap-2">
              <Sword className="h-6 w-6" />
            </div>
            <h2 className="font-amiri text-2xl font-bold tracking-tight md:text-3xl">مستودع الأسلحة</h2>
            <p className="font-mono text-[10px] text-background/75 uppercase tracking-widest">Armory v1</p>
          </div>
        </div>
      </div>

      <div className="flex gap-3 overflow-x-auto pb-2">
        <CategoryTab active label="المساعدات" />
        <CategoryTab label="الشخصيات" />
        <CategoryTab label="الخلفيات" />
        <CategoryTab label="العروض" />
      </div>

      <div className="grid grid-cols-1 gap-4">
        {items.map((item, idx) => (
          <motion.div
            key={item.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.08 }}
            className="vintage-border group shadow-heavy relative flex cursor-default items-center gap-6 overflow-hidden rounded-lg bg-background/90 p-6 transition-all hover:bg-background"
          >
            <div
              className={`flex h-20 w-20 shrink-0 items-center justify-center rounded-xl border-2 border-gold/50 bg-surface/5 transition-transform group-hover:scale-105`}
            >
              {React.cloneElement(item.icon as React.ReactElement, {
                className: `h-10 w-10 ${item.accent}`,
              })}
            </div>

            <div className="min-w-0 flex-1 space-y-1">
              <h3 className="font-amiri text-xl font-bold tracking-tight uppercase italic">{item.name}</h3>
              <p className="text-xs leading-tight font-bold text-ink/60">{item.description}</p>
              <div className="flex items-center gap-2 pt-2">
                <span className="text-primary font-mono text-lg font-bold">{item.price}</span>
                <span className="text-[10px] font-bold text-ink/40 uppercase">ذهب</span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => purchase(item.id, item.price)}
              className="btn-crimson vintage-border shrink-0 rounded-lg px-5 py-4 font-black uppercase italic tracking-tighter transition-transform active:scale-95"
            >
              شراء
            </button>
          </motion.div>
        ))}
      </div>

      <section className="vintage-border relative flex h-48 flex-col justify-center space-y-4 overflow-hidden rounded-lg border-primary/40 bg-primary/10 p-8">
        <div className="bg-primary text-background absolute top-4 right-4 rounded-full px-3 py-1 text-[10px] font-black uppercase tracking-widest">
          عرض محدود
        </div>
        <div className="space-y-1">
          <h3 className="font-amiri text-3xl leading-none font-bold tracking-tight uppercase italic">
            حزمة <span className="text-primary">الأسطورة</span>
          </h3>
          <p className="text-sm font-bold text-ink/75">5000 ذهب + 500 جوهرة + شخصية نادرة</p>
        </div>
        <button
          type="button"
          className="btn-wax btn-crimson w-fit rounded-full px-8 py-3 font-black uppercase italic tracking-tighter"
        >
          احصل عليه بـ $9.99
        </button>
      </section>
    </motion.div>
  );
}

function CategoryTab({ active, label }: { active?: boolean; label: string }) {
  return (
    <button
      type="button"
      className={`whitespace-nowrap rounded-full border px-6 py-3 text-xs font-bold tracking-widest uppercase transition-all ${
        active
          ? 'border-gold bg-primary text-background'
          : 'border-gold/30 bg-background/60 text-ink/50 hover:border-gold hover:text-ink'
      }`}
    >
      {label}
    </button>
  );
}
