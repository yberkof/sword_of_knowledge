import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  auth,
  onAuthStateChanged,
  signInWithPopup,
  signInAnonymously,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  linkWithPopup,
  googleProvider,
  User,
} from './firebase';
import { Screen, UserProfile } from './types';
import WarRoom from './components/WarRoom';
import GameSession from './components/GameSession';
import Records from './components/Records';
import Armory from './components/Armory';
import { Sword, Shield, Trophy, User as UserIcon, LogIn, Loader2 } from 'lucide-react';
import { apiUrl } from './apiConfig';

function SidebarNav({
  activeScreen,
  onNavigate,
}: {
  activeScreen: Screen;
  onNavigate: (s: Screen) => void;
}) {
  const Item = ({
    screen,
    icon,
    label,
  }: {
    screen: Screen;
    icon: React.ReactNode;
    label: string;
  }) => {
    const active = activeScreen === screen;
    return (
      <button
        type="button"
        onClick={() => onNavigate(screen)}
        className={`group flex w-full items-center gap-4 rounded border px-4 py-3 text-start transition-colors ${
          active
            ? 'border-gold/30 bg-background/10 text-background'
            : 'border-transparent text-vellum hover:border-gold/30 hover:bg-background/5 hover:text-background'
        }`}
      >
        <span className={active ? 'text-gold' : 'text-vellum group-hover:text-gold'}>{icon}</span>
        <span className="font-amiri text-sm tracking-wider">{label}</span>
      </button>
    );
  };

  return (
    <nav className="flex flex-1 flex-col gap-2 overflow-y-auto px-4 py-6">
      <Item screen="WAR_ROOM" label="غرفة الحرب" icon={<Sword className="h-5 w-5" />} />
      <Item screen="ARMORY" label="مستودع الأسلحة" icon={<Shield className="h-5 w-5" />} />
      <Item screen="RECORDS" label="قاعة السجلات" icon={<Trophy className="h-5 w-5" />} />
      <button
        type="button"
        disabled
        className="flex w-full items-center gap-4 rounded border border-transparent px-4 py-3 text-start text-vellum/50"
      >
        <UserIcon className="h-5 w-5" />
        <span className="font-amiri text-sm tracking-wider">الملف الشخصي</span>
      </button>
    </nav>
  );
}

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [activeScreen, setActiveScreen] = useState<Screen>('WAR_ROOM');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (u) => {
      setUser(u);
      if (u) {
        try {
          const token = await u.getIdToken();
          const headers: HeadersInit = { Authorization: `Bearer ${token}` };
          let r = await fetch(apiUrl('/api/profile'), { headers });
          if (r.status === 404) {
            const cr = await fetch(apiUrl('/api/profile'), {
              method: 'POST',
              headers: { ...headers, 'Content-Type': 'application/json' },
              body: JSON.stringify({
                name: u.displayName || 'Warrior',
                username: u.displayName || 'warrior',
                avatar: u.photoURL || '',
              }),
            });
            if (cr.ok) {
              setProfile((await cr.json()) as UserProfile);
            } else {
              setProfile(null);
            }
          } else if (r.ok) {
            setProfile((await r.json()) as UserProfile);
          } else {
            setProfile(null);
          }
        } catch (e) {
          console.error(e);
          setProfile(null);
        }
      } else {
        setProfile(null);
      }
      setLoading(false);
    });
    return () => unsubscribe();
  }, []);

  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [emailAuthRegister, setEmailAuthRegister] = useState(false);

  const handleLogin = async () => {
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  const handleGuest = async () => {
    try {
      await signInAnonymously(auth);
    } catch (e) {
      console.error(e);
    }
  };

  const handleEmailAuth = async () => {
    if (!loginEmail.trim() || loginPassword.length < 6) return;
    try {
      if (emailAuthRegister) {
        await createUserWithEmailAndPassword(auth, loginEmail.trim(), loginPassword);
      } else {
        await signInWithEmailAndPassword(auth, loginEmail.trim(), loginPassword);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleLinkGoogle = async () => {
    const u = auth.currentUser;
    if (!u?.isAnonymous) return;
    try {
      await linkWithPopup(u, googleProvider);
    } catch (e) {
      console.error(e);
    }
  };

  if (loading) {
    return (
      <div className="bg-parchment flex h-screen w-screen items-center justify-center text-ink">
        <Loader2 className="text-primary h-12 w-12 animate-spin" />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="bg-parchment flex min-h-screen w-screen flex-col items-center justify-center p-6 text-ink">
        <motion.div
          initial={{ opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          className="vintage-border bg-surface shadow-heavy w-full max-w-md space-y-8 rounded-lg p-8"
        >
          <div className="relative flex justify-center">
            <Sword className="text-gold h-24 w-24 rotate-45" />
            <Shield className="text-background/20 absolute top-1/2 left-1/2 h-16 w-16 -translate-x-1/2 -translate-y-1/2" />
          </div>
          <div className="space-y-2 text-center">
            <h1 className="font-amiri text-4xl font-bold tracking-tight text-background md:text-5xl">
              سيف <span className="text-gold">المعرفة</span>
            </h1>
            <p className="text-vellum font-mono text-xs tracking-widest uppercase">
              Sword of Knowledge — الاستراتيجي التاريخي
            </p>
          </div>
          <div className="flex flex-col gap-3">
            <button
              type="button"
              onClick={handleLogin}
              className="btn-wax btn-crimson flex items-center justify-center gap-3 rounded-lg px-8 py-4 font-bold"
            >
              <LogIn className="h-6 w-6" />
              Google
            </button>
            <button
              type="button"
              onClick={handleGuest}
              className="border-gold/40 text-background hover:bg-background/10 rounded-lg border px-8 py-3 text-sm font-bold transition-colors"
            >
              متابعة كضيف
            </button>
            <div className="border-gold/30 space-y-3 rounded-xl border bg-black/20 p-4 text-right">
              <p className="text-vellum font-mono text-[10px] uppercase">بريد وكلمة مرور</p>
              <input
                type="email"
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                placeholder="email@example.com"
                className="border-gold/20 focus:border-gold w-full rounded-lg border bg-background/10 px-3 py-2 text-sm text-background placeholder:text-vellum/50"
              />
              <input
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="••••••••"
                className="border-gold/20 focus:border-gold w-full rounded-lg border bg-background/10 px-3 py-2 text-sm text-background placeholder:text-vellum/50"
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setEmailAuthRegister(!emailAuthRegister)}
                  className="text-gold text-[10px] underline"
                >
                  {emailAuthRegister ? 'لديك حساب؟ تسجيل الدخول' : 'إنشاء حساب'}
                </button>
                <button
                  type="button"
                  onClick={handleEmailAuth}
                  className="bg-gold/20 hover:bg-gold/30 flex-1 rounded-lg py-2 text-sm font-bold text-background"
                >
                  {emailAuthRegister ? 'تسجيل' : 'دخول'}
                </button>
              </div>
            </div>
            <p className="text-vellum/70 text-center font-mono text-[9px]">
              Facebook / Apple: فعّل المزوّدين في Firebase ثم أضف أزراراً هنا.
            </p>
          </div>
        </motion.div>
      </div>
    );
  }

  const profileBlock = (
    <div className="border-gold/30 flex items-center gap-4 border-b p-6">
      <div className="border-gold relative h-20 w-20 shrink-0 overflow-hidden rounded border-2 bg-background shadow-inner">
        <img src={profile?.avatar || ''} alt="" className="h-full w-full object-cover" />
        <div className="border-gold/50 absolute end-0 bottom-0 start-0 border-t bg-black/60 py-0.5 text-center font-amiri text-[10px] text-gold">
          مستوى {profile?.level ?? '—'}
        </div>
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-amiri text-background truncate text-lg leading-tight">
          {profile?.countryFlag} {profile?.username || profile?.name || 'محارب'}
        </p>
        <p className="font-amiri text-gold text-sm italic">{profile?.title}</p>
        <div className="mt-1 flex flex-wrap items-center gap-3 text-sm">
          <span className="text-background/90 font-mono">G {profile?.gold ?? 0}</span>
          <span className="text-copper font-mono">♦ {profile?.gems ?? 0}</span>
        </div>
        {user?.isAnonymous ? (
          <button type="button" onClick={handleLinkGoogle} className="text-vellum mt-1 text-[9px] underline">
            ربط الحساب بجوجل
          </button>
        ) : null}
      </div>
    </div>
  );

  return (
    <div className="flex h-screen w-screen flex-col bg-surface lg:flex-row">
      {/* Desktop sidebar (RTL: first in row = inline-start = right side) */}
      <aside className="shadow-heavy hidden w-[300px] shrink-0 flex-col border-e-2 border-gold text-vellum lg:flex">
        {profileBlock}
        <SidebarNav activeScreen={activeScreen} onNavigate={setActiveScreen} />
        <div className="border-gold/30 mt-auto border-t p-4 text-vellum/60">
          <p className="font-mono text-[10px]">سيف المعرفة</p>
        </div>
      </aside>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        {/* Mobile top strip */}
        <header className="border-gold/30 flex flex-none items-center justify-between gap-2 border-b bg-surface/95 px-3 py-2 lg:hidden">
          <div className="flex min-w-0 items-center gap-2">
            <div className="border-gold h-10 w-10 shrink-0 overflow-hidden rounded border">
              <img src={profile?.avatar || ''} alt="" className="h-full w-full object-cover" />
            </div>
            <div className="min-w-0">
              <p className="text-background truncate text-xs font-bold">{profile?.username || profile?.name}</p>
              <p className="text-gold font-mono text-[10px]">LV {profile?.level}</p>
            </div>
          </div>
          <div className="text-background flex shrink-0 gap-2 font-mono text-[10px]">
            <span>G{profile?.gold}</span>
            <span className="text-copper">♦{profile?.gems}</span>
          </div>
        </header>

        <main className="bg-parchment flex min-h-0 flex-1 flex-col overflow-y-auto">
          <AnimatePresence mode="wait">
            {activeScreen === 'WAR_ROOM' && (
              <WarRoom onStart={() => setActiveScreen('MATCH_SETUP')} onNavigate={setActiveScreen} />
            )}
            {activeScreen === 'MATCH_SETUP' && (
              <GameSession onBack={() => setActiveScreen('WAR_ROOM')} />
            )}
            {activeScreen === 'RECORDS' && (
              <Records onBack={() => setActiveScreen('WAR_ROOM')} />
            )}
            {activeScreen === 'ARMORY' && (
              <Armory onBack={() => setActiveScreen('WAR_ROOM')} />
            )}
          </AnimatePresence>
        </main>

        <nav className="border-gold/40 flex flex-none items-center justify-around border-t bg-surface px-2 py-3 lg:hidden">
          <NavButton
            active={activeScreen === 'WAR_ROOM'}
            onClick={() => setActiveScreen('WAR_ROOM')}
            icon={<Sword />}
            label="الغرفة"
          />
          <NavButton
            active={activeScreen === 'ARMORY'}
            onClick={() => setActiveScreen('ARMORY')}
            icon={<Shield />}
            label="المخزن"
          />
          <NavButton
            active={activeScreen === 'RECORDS'}
            onClick={() => setActiveScreen('RECORDS')}
            icon={<Trophy />}
            label="السجلات"
          />
          <NavButton active={false} onClick={() => {}} icon={<UserIcon />} label="الملف" />
        </nav>
      </div>
    </div>
  );
}

function NavButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button type="button" onClick={onClick} className="flex flex-col items-center gap-1 transition-colors">
      <div
        className={`rounded-xl p-2 ${active ? 'bg-gold/15 text-gold border-gold/40 border' : 'text-vellum border border-transparent'}`}
      >
        {React.cloneElement(icon as React.ReactElement, { className: 'w-6 h-6' })}
      </div>
      <span
        className={`font-mono text-[9px] font-bold tracking-widest uppercase ${active ? 'text-gold' : 'text-vellum'}`}
      >
        {label}
      </span>
    </button>
  );
}
