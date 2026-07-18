interface LogoProps {
  size?: number;
  onClick?: () => void;
}

/** Cloud Shop wordmark — ink disc with a crescent rule + serif name. */
export default function Logo({ size = 22, onClick }: LogoProps) {
  return (
    <button
      onClick={onClick}
      className="flex items-center gap-[9px] bg-transparent border-0 p-0 cursor-pointer"
    >
      <span
        className="relative inline-block rounded-full bg-ink"
        style={{ width: size, height: size }}
      >
        <span
          className="absolute rounded-t-full"
          style={{ inset: '28% 0 0 0', borderTop: '2px solid var(--paper)' }}
        />
      </span>
      <span
        className="font-serif leading-none"
        style={{ fontSize: size + 6, letterSpacing: '0.02em' }}
      >
        Cloud Shop
      </span>
    </button>
  );
}
