import { useId, useRef } from 'react';
import { Box, Button, FormHelperText, Stack, Typography } from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { formatBytes } from './common';

export const PDF_MAX = 100 * 1024 * 1024; // songbook.storage.max-pdf-bytes
export const IMAGE_MAX = 5 * 1024 * 1024; // songbook.storage.max-image-bytes
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Quick client-side checks that mirror the backend limits, so admins get instant feedback.
 * The backend still validates magic bytes and parses every file (docs/SECURITY.md §6).
 */
export function checkPdf(f: File | null): string | null {
  if (!f) return null;
  if (f.type && f.type !== 'application/pdf' && !f.name.toLowerCase().endsWith('.pdf')) return 'Choose a PDF file.';
  if (f.size > PDF_MAX) return `PDF is ${formatBytes(f.size)}; the limit is 100 MB.`;
  if (f.size === 0) return 'The file is empty.';
  return null;
}

export function checkImage(f: File | null): string | null {
  if (!f) return null;
  if (!IMAGE_TYPES.includes(f.type)) return 'Cover must be a JPEG, PNG or WebP image.';
  if (f.size > IMAGE_MAX) return `Image is ${formatBytes(f.size)}; the limit is 5 MB.`;
  return null;
}

export default function FilePicker({
  label,
  accept,
  file,
  onChange,
  error,
  hint,
  required,
  disabled,
}: {
  label: string;
  accept: string;
  file: File | null;
  onChange: (f: File | null) => void;
  error?: string | null;
  hint?: string;
  required?: boolean;
  disabled?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const id = useId();
  return (
    <Box>
      <Typography component="label" htmlFor={id} variant="body2" sx={{ fontWeight: 600, display: 'block', mb: 0.5 }}>
        {label}
        {required && ' *'}
      </Typography>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <Button variant="outlined" startIcon={<UploadFileIcon />} onClick={() => input.current?.click()} disabled={disabled}>
          {file ? 'Change file' : 'Choose file'}
        </Button>
        <Typography variant="body2" color={file ? 'text.primary' : 'text.secondary'} sx={{ overflowWrap: 'anywhere', minWidth: 0 }}>
          {file ? `${file.name} (${formatBytes(file.size)})` : 'No file chosen'}
        </Typography>
        {file && !disabled && (
          <Button size="small" onClick={() => onChange(null)}>
            Remove
          </Button>
        )}
      </Stack>
      <input
        id={id}
        ref={input}
        type="file"
        accept={accept}
        hidden
        onChange={(e) => {
          onChange(e.target.files?.[0] ?? null);
          e.target.value = ''; // allow re-choosing the same file
        }}
      />
      <FormHelperText error={!!error}>{error ?? hint ?? ' '}</FormHelperText>
    </Box>
  );
}
