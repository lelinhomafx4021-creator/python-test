/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "#0f172a", // Legacy
        primary: "#3b82f6", 
        secondary: "#64748b",
        glass: "rgba(255, 255, 255, 0.05)",
        // Light Theme Tokens
        "light-bg": "#f9fafb",
        "card-white": "rgba(255, 255, 255, 0.8)",
        "accent": "#4f46e5",
      },
      backgroundImage: {
        'gradient-radial': 'radial-gradient(var(--tw-gradient-stops))',
      }
    },
  },
  plugins: [],
}
