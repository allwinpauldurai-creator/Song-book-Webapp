import { useState, type FormEvent } from 'react';
import { Link as RouterLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router';
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Container,
  Divider,
  IconButton,
  InputBase,
  ListItemIcon,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import SearchIcon from '@mui/icons-material/Search';
import MusicNoteIcon from '@mui/icons-material/MusicNote';
import LogoutIcon from '@mui/icons-material/Logout';
import DashboardIcon from '@mui/icons-material/Dashboard';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { useAuth } from '../contexts/AuthContext';
import { loginPath } from '../utils/redirect';

function HeaderSearch() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const location = useLocation();
  const [value, setValue] = useState(location.pathname === '/books' ? (params.get('q') ?? '') : '');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const q = value.trim();
    navigate(q ? `/books?q=${encodeURIComponent(q)}` : '/books');
  };

  return (
    <Box
      component="form"
      role="search"
      onSubmit={submit}
      sx={(t) => ({
        display: 'flex',
        alignItems: 'center',
        flex: 1,
        maxWidth: 360,
        minWidth: 0,
        px: 1.25,
        borderRadius: 2,
        bgcolor: alpha(t.palette.common.white, 0.15),
        '&:focus-within': { bgcolor: alpha(t.palette.common.white, 0.25) },
      })}
    >
      <SearchIcon fontSize="small" sx={{ mr: 1, opacity: 0.8 }} />
      <InputBase
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Search song books…"
        inputProps={{ 'aria-label': 'Search song books' }}
        sx={{ color: 'inherit', flex: 1, minWidth: 0, py: 0.5 }}
      />
    </Box>
  );
}

function UserMenu() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  if (!user) return null;
  const initial = user.fullName.trim().charAt(0).toUpperCase() || '?';

  const go = (path: string) => {
    setAnchor(null);
    navigate(path);
  };

  return (
    <>
      <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Account menu" sx={{ p: 0.5 }}>
        <Avatar sx={{ width: 34, height: 34, bgcolor: 'secondary.main', fontSize: 16 }}>{initial}</Avatar>
      </IconButton>
      <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
        <Box sx={{ px: 2, py: 1, maxWidth: 260 }}>
          <Typography sx={{ fontWeight: 600 }} noWrap>
            {user.fullName}
          </Typography>
          <Typography variant="body2" color="text.secondary" noWrap>
            {user.email}
          </Typography>
        </Box>
        <Divider />
        <MenuItem onClick={() => go('/me')}>
          <ListItemIcon>
            <DashboardIcon fontSize="small" />
          </ListItemIcon>
          My dashboard
        </MenuItem>
        {isAdmin && (
          <MenuItem onClick={() => go('/admin')}>
            <ListItemIcon>
              <AdminPanelSettingsIcon fontSize="small" />
            </ListItemIcon>
            Admin
          </MenuItem>
        )}
        <MenuItem
          onClick={async () => {
            setAnchor(null);
            await logout().catch(() => undefined);
            navigate('/');
          }}
        >
          <ListItemIcon>
            <LogoutIcon fontSize="small" />
          </ListItemIcon>
          Log out
        </MenuItem>
      </Menu>
    </>
  );
}

export default function Layout() {
  const { status } = useAuth();
  const location = useLocation();
  const onAuthPage = /^\/(login|register|forgot-password|reset-password|verify-email)/.test(location.pathname);

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <AppBar position="sticky" elevation={0} color="primary" enableColorOnDark>
        <Container maxWidth="lg" disableGutters sx={{ px: 2 }}>
          <Toolbar disableGutters sx={{ gap: { xs: 1, sm: 2 } }}>
            <Box
              component={RouterLink}
              to="/"
              sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'inherit', textDecoration: 'none' }}
            >
              <MusicNoteIcon />
              <Typography variant="h6" sx={{ fontWeight: 700, display: { xs: 'none', sm: 'block' } }}>
                Song Book
              </Typography>
            </Box>
            <Button color="inherit" component={RouterLink} to="/books" sx={{ display: { xs: 'none', md: 'inline-flex' } }}>
              Catalogue
            </Button>
            <Box sx={{ flex: 1, display: 'flex', justifyContent: 'flex-end', minWidth: 0 }}>
              <HeaderSearch key={location.pathname} />
            </Box>
            {status === 'authenticated' ? (
              <UserMenu />
            ) : (
              status === 'anonymous' &&
              !onAuthPage && (
                <Button
                  color="inherit"
                  variant="outlined"
                  component={RouterLink}
                  to={loginPath(location.pathname + location.search)}
                  sx={{ borderColor: 'rgba(255,255,255,0.5)', flexShrink: 0 }}
                >
                  Log in
                </Button>
              )
            )}
          </Toolbar>
        </Container>
      </AppBar>
      <Box component="main" sx={{ flex: 1 }}>
        <Outlet />
      </Box>
      <Box component="footer" sx={{ py: 3, textAlign: 'center', color: 'text.secondary' }}>
        <Typography variant="body2">© {new Date().getFullYear()} Song Book</Typography>
      </Box>
    </Box>
  );
}
