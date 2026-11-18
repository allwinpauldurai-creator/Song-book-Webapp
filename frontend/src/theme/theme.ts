import { createTheme } from '@mui/material/styles';

// Light and dark palettes; MUI follows the OS setting via colorSchemes + CssBaseline.
export const theme = createTheme({
  cssVariables: { colorSchemeSelector: 'media' },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#3949ab' },
        secondary: { main: '#00796b' }, // teal 700: 4.5:1+ with white both ways (WCAG AA); teal 600 was 4.31
        // MUI defaults fail WCAG AA with white chip text (warning 3.1:1, info 3.9:1); these pass (5.0, 4.8).
        warning: { main: '#b45309' },
        info: { main: '#0277bd' },
        background: { default: '#f6f7fb', paper: '#ffffff' },
      },
    },
    dark: {
      palette: {
        primary: { main: '#9fa8ff' },
        secondary: { main: '#4db6ac' },
        background: { default: '#12131a', paper: '#1c1d26' },
      },
    },
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h1: { fontSize: '2.25rem', fontWeight: 700, lineHeight: 1.2 },
    h2: { fontSize: '1.75rem', fontWeight: 700 },
    h3: { fontSize: '1.375rem', fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiTextField: { defaultProps: { fullWidth: true } },
    MuiCard: { defaultProps: { variant: 'outlined' } },
  },
});
