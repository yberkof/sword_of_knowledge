import React from 'react';
import { motion } from 'motion/react';
import { Sword, Shield, Trophy, Users, Play, Star } from 'lucide-react';
import { Screen } from '../types';

interface WarRoomProps {
  onStart: () => void;
  onNavigate: (screen: Screen) => void;
}

export default function WarRoom({ onStart, onNavigate }: WarRoomProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="flex h-full w-full flex-col space-y-8 overflow-y-auto p-6 text-ink"
    >
      <section className="vintage-border shadow-heavy group relative h-64 overflow-hidden rounded-lg bg-surface">
        <div className="absolute inset-0 opacity-30 bg-[radial-gradient(ellipse_at_center,_#F4EED8_0%,_transparent_70%)]" />
        <div className="bg-parchment absolute inset-0 opacity-10" />
        <div className="absolute inset-0 flex flex-col justify-end bg-gradient-to-t from-black/80 via-black/40 to-transparent p-8">
          <motion.div
            initial={{ x: -20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            transition={{ delay: 0.2 }}
            className="space-y-2"
          >
            <div className="text-gold flex items-center gap-2">
              <Star className="h-4 w-4 fill-current" />
              <span className="font-mono text-[10px] font-bold tracking-[0.2em] uppercase">الموسم الأول: عودة السيف</span>
            </div>
            <h2 className="font-amiri text-4xl leading-none font-bold tracking-tight text-background md:text-5xl">
              استعد <span className="text-gold">للمعركة</span>
            </h2>
            <p className="max-w-xs text-sm text-background/80">
              أثبت ذكاءك وسيطر على الخريطة في لعبة استراتيجية ثقافية.
            </p>
          </motion.div>
        </div>
      </section>

      <div className="grid grid-cols-2 gap-4">
        <ActionButton
          onClick={onStart}
          icon={<Play className="h-8 w-8" />}
          label="ابدأ اللعب"
          sub="2–4 لاعبين"
          primary
        />
        <ActionButton
          onClick={() => onNavigate('ARMORY')}
          icon={<Shield className="h-8 w-8" />}
          label="المخزن"
          sub="Power-ups"
        />
        <ActionButton
          onClick={() => onNavigate('RECORDS')}
          icon={<Trophy className="h-8 w-8" />}
          label="السجلات"
          sub="Leaderboard"
        />
        <ActionButton
          onClick={() => onNavigate('RECORDS')}
          icon={<Users className="h-8 w-8" />}
          label="التحالفات"
          sub="قريباً — السجلات"
        />
      </div>

      <section className="vintage-border rounded-lg bg-background/60 p-4 text-center shadow-sm backdrop-blur-sm">
        <p className="text-ink/80 font-mono text-[10px] font-bold tracking-widest uppercase">المجتمع</p>
        <p className="text-ink/60 mt-1 text-xs">
          البطولات الأسبوعية والتحالفات قيد الإعداد؛ تصنيف الأسبوع الحالي في قاعة السجلات.
        </p>
      </section>

      <section className="vintage-border space-y-4 rounded-lg bg-background/80 p-6 shadow-sm backdrop-blur-sm">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold tracking-widest text-ink/70 uppercase">المهمة اليومية</h3>
          <span className="text-primary font-mono text-[10px]">ينتهي خلال 12 ساعة</span>
        </div>
        <div className="flex items-center gap-4">
          <div className="border-gold/40 flex h-12 w-12 items-center justify-center rounded-2xl border bg-primary/10">
            <Sword className="text-primary h-6 w-6" />
          </div>
          <div className="flex-1 space-y-2">
            <p className="text-sm font-bold">انتصر في 3 مباريات كلاسيكية</p>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-ink/10">
              <div className="bg-primary h-full w-1/3" />
            </div>
          </div>
          <div className="text-end">
            <p className="text-primary text-xs font-bold">1/3</p>
            <p className="text-[10px] text-ink/40 uppercase">مكتمل</p>
          </div>
        </div>
      </section>
    </motion.div>
  );
}

function ActionButton({
  onClick,
  icon,
  label,
  sub,
  primary = false,
}: {
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  sub: string;
  primary?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`group relative flex h-40 transform flex-col justify-between overflow-hidden rounded-xl border-2 p-6 text-right transition-all active:scale-[0.98] ${
        primary
          ? 'btn-crimson vintage-border border-gold text-background'
          : 'border-gold/40 bg-background/60 text-ink hover:border-gold hover:bg-background'
      }`}
    >
      <div
        className={`flex h-12 w-12 items-center justify-center rounded-xl border ${
          primary ? 'border-background/30 bg-black/20' : 'border-gold/30 bg-surface/5'
        }`}
      >
        {primary ? (
          React.cloneElement(icon as React.ReactElement, { className: 'h-8 w-8 text-gold' })
        ) : (
          React.cloneElement(icon as React.ReactElement, { className: 'h-8 w-8 text-primary' })
        )}
      </div>
      <div className="space-y-1">
        <p className="font-amiri text-xl leading-none font-bold tracking-tight uppercase italic">{label}</p>
        <p
          className={`font-mono text-[10px] tracking-widest uppercase ${primary ? 'text-background/70' : 'text-ink/50'}`}
        >
          {sub}
        </p>
      </div>
      <div
        className={`absolute -bottom-4 -left-4 h-24 w-24 rounded-full blur-3xl transition-opacity group-hover:opacity-40 ${
          primary ? 'bg-gold/30 opacity-30' : 'bg-primary/20 opacity-20'
        }`}
      />
    </button>
  );
}
