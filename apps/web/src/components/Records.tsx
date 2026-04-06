import React from 'react';
import { motion } from 'motion/react';
import { Trophy, ArrowLeft, Users, Globe, Zap } from 'lucide-react';

interface RecordsProps {
  onBack: () => void;
}

export default function Records({ onBack }: RecordsProps) {
  const leaderboard = [
    { rank: 1, name: 'سيف المعرفة', title: 'أسطورة المعرفة', points: 12450, avatar: 'https://picsum.photos/seed/1/100/100' },
    { rank: 2, name: 'فارس الحق', title: 'خبير تاريخ', points: 11200, avatar: 'https://picsum.photos/seed/2/100/100' },
    { rank: 3, name: 'نور العلم', title: 'عالم جغرافيا', points: 10800, avatar: 'https://picsum.photos/seed/3/100/100' },
    { rank: 4, name: 'بطل العرب', title: 'مقاتل برونزي', points: 9500, avatar: 'https://picsum.photos/seed/4/100/100' },
    { rank: 5, name: 'ذكاء خارق', title: 'مبتدئ طموح', points: 8200, avatar: 'https://picsum.photos/seed/5/100/100' },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="bg-parchment flex h-full w-full flex-col space-y-8 overflow-y-auto p-6 text-ink"
    >
      <div className="flex items-center justify-between gap-4">
        <button
          type="button"
          onClick={onBack}
          className="border-gold/40 rounded-full border bg-background/80 p-2 transition-colors hover:border-gold"
          aria-label="رجوع"
        >
          <ArrowLeft className="h-6 w-6" />
        </button>
        <div className="text-end">
          <h2 className="font-amiri text-3xl leading-none font-bold tracking-tight uppercase italic">
            قاعة <span className="text-primary">السجلات</span>
          </h2>
          <p className="font-mono text-[10px] text-ink/50 uppercase tracking-widest">Leaderboard v1</p>
        </div>
      </div>

      <div className="flex items-end justify-center gap-4 py-6">
        <PodiumItem
          rank={2}
          name={leaderboard[1].name}
          points={leaderboard[1].points}
          avatar={leaderboard[1].avatar}
          height="h-32"
        />
        <PodiumItem
          rank={1}
          name={leaderboard[0].name}
          points={leaderboard[0].points}
          avatar={leaderboard[0].avatar}
          height="h-40"
          primary
        />
        <PodiumItem
          rank={3}
          name={leaderboard[2].name}
          points={leaderboard[2].points}
          avatar={leaderboard[2].avatar}
          height="h-24"
        />
      </div>

      <div className="vintage-border shadow-heavy rounded-lg bg-background/80 p-2 backdrop-blur-sm">
        <div className="border-gold/30 flex items-center justify-between border-b px-4 py-2 text-[10px] font-bold tracking-widest text-ink/45 uppercase">
          <span>الترتيب</span>
          <span>اللاعب</span>
          <span>النقاط</span>
        </div>
        {leaderboard.map((entry) => (
          <motion.div
            key={entry.rank}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: entry.rank * 0.05 }}
            className="border-gold/20 hover:bg-background group m-1 flex cursor-pointer items-center justify-between rounded-lg border bg-background/50 p-4 transition-colors"
          >
            <div className="flex items-center gap-4">
              <span
                className={`font-black italic ${entry.rank <= 3 ? 'text-primary' : 'text-ink/35'} text-lg`}
              >
                {entry.rank.toString().padStart(2, '0')}
              </span>
              <div className="border-gold/40 h-10 w-10 overflow-hidden rounded-full border">
                <img src={entry.avatar} alt="" className="h-full w-full object-cover" referrerPolicy="no-referrer" />
              </div>
              <div className="text-end">
                <p className="text-sm font-bold">{entry.name}</p>
                <p className="text-primary font-mono text-[8px] tracking-widest uppercase">{entry.title}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm font-bold">{entry.points.toLocaleString()}</span>
              <Trophy className={`h-4 w-4 ${entry.rank === 1 ? 'text-gold' : 'text-ink/25'}`} />
            </div>
          </motion.div>
        ))}
      </div>

      <div className="border-gold/30 grid grid-cols-3 gap-4 border-t pt-8">
        <StatCard icon={<Users />} label="اللاعبين" value="12.4K" />
        <StatCard icon={<Globe />} label="الدول" value="22" />
        <StatCard icon={<Zap />} label="المباريات" value="1.2M" />
      </div>
    </motion.div>
  );
}

function PodiumItem({
  rank,
  name,
  points,
  avatar,
  height,
  primary = false,
}: {
  rank: number;
  name: string;
  points: number;
  avatar: string;
  height: string;
  primary?: boolean;
}) {
  return (
    <div className="flex flex-col items-center gap-3">
      <div
        className={`relative h-16 w-16 overflow-hidden rounded-full border-2 ${primary ? 'border-gold scale-110' : 'border-gold/30'}`}
      >
        <img src={avatar} alt="" className="h-full w-full object-cover" referrerPolicy="no-referrer" />
        <div className="absolute inset-0 flex items-end justify-center bg-gradient-to-t from-black/70 to-transparent pb-1">
          <span className="text-[10px] font-black italic text-background">#{rank}</span>
        </div>
      </div>
      <div
        className={`flex w-16 flex-col items-center justify-center space-y-1 rounded-t-2xl p-2 text-center ${height} ${
          primary ? 'bg-primary text-background' : 'border-gold/30 border bg-background/70 text-ink'
        }`}
      >
        <p className="w-full truncate text-[8px] font-black tracking-tighter uppercase">{name}</p>
        <p className="font-mono text-[10px] font-bold">{points}</p>
      </div>
    </div>
  );
}

function StatCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="vintage-border flex flex-col items-center gap-1 rounded-lg bg-background/70 p-4">
      {React.cloneElement(icon as React.ReactElement, { className: 'h-4 w-4 text-primary' })}
      <p className="text-lg font-black italic leading-none">{value}</p>
      <p className="text-[8px] font-bold tracking-widest text-ink/45 uppercase">{label}</p>
    </div>
  );
}
