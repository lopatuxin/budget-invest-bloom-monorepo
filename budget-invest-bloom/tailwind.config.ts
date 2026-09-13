import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

export default {
	content: [
		"./src/**/*.{ts,tsx}",
	],
	prefix: "",
	theme: {
		extend: {
			fontFamily: {
				sans: ['"IBM Plex Sans"', 'sans-serif'],
				mono: ['"IBM Plex Mono"', 'monospace'],
				display: ['"Instrument Serif"', 'serif'],
			},
			colors: {
				border: 'hsl(var(--border))',
				ring: 'hsl(var(--ring))',
				background: 'hsl(var(--background))',
				foreground: 'hsl(var(--foreground))',
				primary: {
					DEFAULT: 'hsl(var(--primary))',
					foreground: 'hsl(var(--primary-foreground))'
				},
				secondary: {
					DEFAULT: 'hsl(var(--secondary))'
				},
				destructive: {
					DEFAULT: 'hsl(var(--destructive))',
					foreground: 'hsl(var(--destructive-foreground))'
				},
				muted: {
					DEFAULT: 'hsl(var(--muted))',
					foreground: 'hsl(var(--muted-foreground))'
				},
				accent: {
					DEFAULT: 'hsl(var(--accent))',
					foreground: 'hsl(var(--accent-foreground))'
				},
				popover: {
					DEFAULT: 'hsl(var(--popover))',
					foreground: 'hsl(var(--popover-foreground))'
				},
				// "Гроссбух" light theme tokens — see the token table in docs/plans/budget-page-redesign.md
				app: {
					bg: 'rgb(var(--app-bg) / <alpha-value>)',
					surface: 'rgb(var(--app-surface) / <alpha-value>)',
					'surface-2': 'rgb(var(--app-surface-2) / <alpha-value>)',
					border: 'rgb(var(--app-border) / <alpha-value>)',
					'border-strong': 'rgb(var(--app-border-strong) / <alpha-value>)',
					text: 'rgb(var(--app-text) / <alpha-value>)',
					'text-muted': 'rgb(var(--app-text-muted) / <alpha-value>)',
					'text-dim': 'rgb(var(--app-text-dim) / <alpha-value>)',
					accent: 'rgb(var(--app-accent) / <alpha-value>)',
					'accent-ink': 'rgb(var(--app-accent-ink) / <alpha-value>)',
					'accent-soft': 'rgb(var(--app-accent-soft) / <alpha-value>)',
					good: 'rgb(var(--app-good) / <alpha-value>)',
					'good-soft': 'rgb(var(--app-good-soft) / <alpha-value>)',
					warn: 'rgb(var(--app-warn) / <alpha-value>)',
					'warn-soft': 'rgb(var(--app-warn-soft) / <alpha-value>)',
					bad: 'rgb(var(--app-bad) / <alpha-value>)',
					'bad-soft': 'rgb(var(--app-bad-soft) / <alpha-value>)',
					neutral: 'rgb(var(--app-neutral) / <alpha-value>)',
					'neutral-soft': 'rgb(var(--app-neutral-soft) / <alpha-value>)',
					track: 'rgb(var(--app-track) / <alpha-value>)',
				}
			},
			borderRadius: {
				lg: 'var(--radius)',
				md: 'calc(var(--radius) - 2px)',
				sm: 'calc(var(--radius) - 4px)'
			}
		}
	},
	plugins: [tailwindcssAnimate],
} satisfies Config;
