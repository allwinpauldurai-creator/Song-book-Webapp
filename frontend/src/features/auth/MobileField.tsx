import { TextField, type TextFieldProps } from '@mui/material';
import { MOBILE_HELP, normalizeMobile } from '../../utils/mobile';

type Props = Omit<TextFieldProps, 'value' | 'onChange'> & {
  value: string;
  onChange: (value: string) => void;
  /** Show the "invalid number" message (e.g. after the form was submitted once). */
  showError?: boolean;
  /** Server-side message for this field, shown instead of the hint. */
  serverError?: string;
};

/** Mobile number input. Accepts loose spellings; the parent sends normalizeMobile(value). */
export default function MobileField({ value, onChange, showError, serverError, label = 'Mobile number', ...rest }: Props) {
  const invalid = value.trim().length > 0 && normalizeMobile(value) === null;
  const empty = value.trim().length === 0;
  const error = !!serverError || (!!showError && (invalid || (empty && !!rest.required)));
  const helper = serverError ?? (showError && empty && rest.required ? 'Enter your mobile number.' : invalid && showError ? 'Enter a valid mobile number.' : MOBILE_HELP);
  return (
    <TextField
      {...rest}
      label={label}
      type="tel"
      autoComplete="tel"
      placeholder="98765 43210"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      error={error}
      helperText={helper}
      slotProps={{ ...rest.slotProps, htmlInput: { inputMode: 'tel', maxLength: 25 } }}
    />
  );
}
