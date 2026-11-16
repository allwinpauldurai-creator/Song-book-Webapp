import { NavLink, Outlet, useLocation } from 'react-router';
import { Box, Container, List, ListItemButton, ListItemIcon, ListItemText, Paper, Tab, Tabs } from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PeopleIcon from '@mui/icons-material/People';
import CardMembershipIcon from '@mui/icons-material/CardMembership';
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks';
import CategoryIcon from '@mui/icons-material/Category';
import RateReviewIcon from '@mui/icons-material/RateReview';
import HistoryIcon from '@mui/icons-material/History';

const NAV = [
  { to: '/admin', label: 'Dashboard', icon: <DashboardIcon />, end: true },
  { to: '/admin/users', label: 'Users', icon: <PeopleIcon /> },
  { to: '/admin/memberships', label: 'Memberships', icon: <CardMembershipIcon /> },
  { to: '/admin/books', label: 'Books', icon: <LibraryBooksIcon /> },
  { to: '/admin/categories', label: 'Categories', icon: <CategoryIcon /> },
  { to: '/admin/requests', label: 'Requests', icon: <RateReviewIcon /> },
  { to: '/admin/audit', label: 'Audit log', icon: <HistoryIcon /> },
];

function activeIndex(pathname: string): number {
  let best = 0;
  NAV.forEach((n, i) => {
    if (n.end ? pathname === n.to : pathname === n.to || pathname.startsWith(n.to + '/')) best = i;
  });
  return best;
}

/** Admin shell: side navigation on desktop, scrollable tabs on small screens. */
export default function AdminLayout() {
  const { pathname } = useLocation();
  const current = activeIndex(pathname);

  return (
    <Container maxWidth="xl" sx={{ py: { xs: 2, md: 4 }, px: 2 }}>
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: '220px minmax(0, 1fr)' } }}>
        <Box component="nav" aria-label="Admin">
          <Paper variant="outlined" sx={{ display: { xs: 'none', md: 'block' }, position: 'sticky', top: 88 }}>
            <List dense>
              {NAV.map((n, i) => (
                <ListItemButton key={n.to} component={NavLink} to={n.to} end={n.end} selected={i === current}>
                  <ListItemIcon sx={{ minWidth: 36 }}>{n.icon}</ListItemIcon>
                  <ListItemText primary={n.label} />
                </ListItemButton>
              ))}
            </List>
          </Paper>
          <Tabs
            value={current}
            variant="scrollable"
            scrollButtons="auto"
            allowScrollButtonsMobile
            sx={{ display: { xs: 'flex', md: 'none' }, borderBottom: 1, borderColor: 'divider' }}
          >
            {NAV.map((n) => (
              <Tab key={n.to} label={n.label} component={NavLink} to={n.to} end={n.end} />
            ))}
          </Tabs>
        </Box>
        <Box component="section" sx={{ minWidth: 0 }}>
          <Outlet />
        </Box>
      </Box>
    </Container>
  );
}
