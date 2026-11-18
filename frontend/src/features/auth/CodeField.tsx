import { TextField, type TextFieldProps } from '@mui/material';

type Props = Omit<TextFieldProps, 'value' | 'onChange'> & {
  value: string;
  onChange: (value: string) => void;
};

/** Six-digit SMS code. Non-digits are dropped; phones can autofill it from the SMS (one-time-code). */
export default function CodeField({ value, onChange, label = 'Code from SMS', ...rest }: Props) {
  return (
    <TextField
      {...rest}
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 6))}
      autoComplete="one-time-code"
      placeholder="123456"
      slotProps={{
        ...rest.slotProps,
        htmlInput: { inputMode: 'numeric', pattern: '[0-9]*', maxLength: 6, style: { letterSpacing: '0.3em' } },
      }}
    />
  );
}

export const isCompleteCode = (code: string) => /^\d{6}$/.test(code);
