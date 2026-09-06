import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

export default {
	darkMode: ["class"],
	content: [
		"./pages/**/*.{ts,tsx}",
		"./components/**/*.{ts,tsx}",
		"./app/**/*.{ts,tsx}",
		"./src/**/*.{ts,tsx}",
	],
	prefix: "",
	theme: {
		container: {
			center: true,
			padding: '2rem',
			screens: {
				'2xl': '1400px'
			}
		},
		extend: {
			fontFamily: {
				sans: ['"IBM Plex Sans"', 'sans-serif'],
				mono: ['"IBM Plex Mono"', 'monospace'],
				display: ['"Instrument Serif"', 'serif'],
			},
			colors: {
				border: 'hsl(var(--border))',
				input: 'hsl(var(--input))',
				ring: 'hsl(var(--ring))',
				background: 'hsl(var(--background))',
				foreground: 'hsl(var(--foreground))',
				primary: {
					DEFAULT: 'hsl(var(--primary))',
					foreground: 'hsl(var(--primary-foreground))',
					glow: 'hsl(var(--primary-glow))'
				},
				secondary: {
					DEFAULT: 'hsl(var(--secondary))',
					foreground: 'hsl(var(--secondary-foreground))'
				},
				destructive: {
					DEFAULT: 'hsl(var(--destructive))',
					foreground: 'hsl(var(--destructive-foreground))'
				},
				warning: {
					DEFAULT: 'hsl(var(--warning))',
					foreground: 'hsl(var(--warning-foreground))'
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
				card: {
					DEFAULT: 'hsl(var(--card))',
					foreground: 'hsl(var(--card-foreground))'
				},
				dashboard: {
					surface: '#061424',
					'surface-alt': '#0B1929',
					green: '#10B981',
					'green-light': '#4edea3',
					blue: '#3B82F6',
					amber: '#F59E0B',
					purple: '#8B5CF6',
					red: '#EF4444',
					// Piped through CSS vars so the same classes (text-dashboard-text, etc.)
					// render the "Гроссбух" light palette under .app-shell and the old dark
					// palette under .dashboard-bg — see index.css.
					text: 'rgb(var(--app-text) / <alpha-value>)',
					'text-muted': 'rgb(var(--app-text-muted) / <alpha-value>)',
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
				},
				sidebar: {
					DEFAULT: 'hsl(var(--sidebar-background))',
					foreground: 'hsl(var(--sidebar-foreground))',
					primary: 'hsl(var(--sidebar-primary))',
					'primary-foreground': 'hsl(var(--sidebar-primary-foreground))',
					accent: 'hsl(var(--sidebar-accent))',
					'accent-foreground': 'hsl(var(--sidebar-accent-foreground))',
					border: 'hsl(var(--sidebar-border))',
					ring: 'hsl(var(--sidebar-ring))'
				}
			},
			borderRadius: {
				lg: 'var(--radius)',
				md: 'calc(var(--radius) - 2px)',
				sm: 'calc(var(--radius) - 4px)'
			},
			backgroundImage: {
				'gradient-primary': 'var(--gradient-primary)',
				'gradient-secondary': 'var(--gradient-secondary)',
				'gradient-success': 'var(--gradient-success)',
				'gradient-background': 'var(--gradient-background)'
			},
			boxShadow: {
				'card': 'var(--shadow-card)',
				'success': 'var(--shadow-success)',
				'primary': 'var(--shadow-primary)'
			},
			transitionTimingFunction: {
				'smooth': 'var(--transition-smooth)',
				'bounce': 'var(--transition-bounce)'
			},
			keyframes: {
				'accordion-down': {
					from: {
						height: '0'
					},
					to: {
						height: 'var(--radix-accordion-content-height)'
					}
				},
				'accordion-up': {
					from: {
						height: 'var(--radix-accordion-content-height)'
					},
					to: {
						height: '0'
					}
				}
			},
			animation: {
				'accordion-down': 'accordion-down 0.2s ease-out',
				'accordion-up': 'accordion-up 0.2s ease-out'
			}
		}
	},
	plugins: [tailwindcssAnimate],
} satisfies Config;
