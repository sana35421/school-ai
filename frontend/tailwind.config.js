/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        paper: '#FAF7F2',
        ink: '#1A1F36',
        'ink-soft': '#3A4257',
        'ink-mute': '#6B7388',
        terracotta: {
          DEFAULT: '#B85C38',
          50: '#FBF1EC',
          100: '#F4DDD0',
          200: '#E9BBA1',
          300: '#DD9871',
          400: '#D27648',
          500: '#B85C38',
          600: '#964828',
          700: '#73361E',
          800: '#502414',
          900: '#2D130A',
        },
        sage: {
          DEFAULT: '#6B7F6B',
          50: '#F0F2EE',
          100: '#DDE3DC',
          200: '#BBC6BA',
          300: '#98AA97',
          400: '#7A8E79',
          500: '#6B7F6B',
          600: '#536552',
          700: '#3C4B3B',
          800: '#263024',
          900: '#10160F',
        },
        cream: '#FFFFFF',
        border: '#E8E2D8',
        'border-strong': '#D6CDB9',
        warning: '#9F2D2D',
      },
      fontFamily: {
        display: ['"Fraunces"', 'Georgia', 'serif'],
        sans: ['"IBM Plex Sans"', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Courier New"', 'monospace'],
      },
      fontSize: {
        '2xs': ['0.6875rem', { lineHeight: '1rem' }],
      },
      boxShadow: {
        'soft': '0 1px 2px rgba(26, 31, 54, 0.04), 0 1px 3px rgba(26, 31, 54, 0.06)',
        'card': '0 2px 8px rgba(26, 31, 54, 0.06), 0 1px 2px rgba(26, 31, 54, 0.04)',
        'lift': '0 8px 24px rgba(26, 31, 54, 0.08), 0 2px 6px rgba(26, 31, 54, 0.06)',
      },
      borderRadius: {
        'sm': '4px',
        DEFAULT: '6px',
        'md': '8px',
        'lg': '12px',
      },
      animation: {
        'fade-in': 'fadeIn 250ms ease-out',
        'fade-up': 'fadeUp 300ms cubic-bezier(0.16, 1, 0.3, 1)',
        'blink': 'blink 1.4s infinite both',
        'pulse-dot': 'pulseDot 1.4s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        fadeUp: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        blink: {
          '0%, 80%, 100%': { opacity: '0.2' },
          '40%': { opacity: '1' },
        },
        pulseDot: {
          '0%, 80%, 100%': { transform: 'scale(0.6)', opacity: '0.4' },
          '40%': { transform: 'scale(1)', opacity: '1' },
        },
      },
    },
  },
  plugins: [],
}