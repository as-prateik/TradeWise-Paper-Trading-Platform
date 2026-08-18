import { Pipe, PipeTransform } from '@angular/core';

const CURRENCY_FORMAT = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const PLAIN_FORMAT = new Intl.NumberFormat('en-IN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * The single money formatter for the whole app — templates never format
 * currency ad hoc.
 *
 * `signed` prefixes a explicit + for gains, so P&L reads correctly without
 * relying on colour alone.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, mode: 'currency' | 'plain' | 'signed' = 'currency'): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '—';
    }
    if (mode === 'plain') {
      return PLAIN_FORMAT.format(value);
    }
    if (mode === 'signed') {
      const formatted = CURRENCY_FORMAT.format(Math.abs(value));
      if (value > 0) return `+${formatted}`;
      if (value < 0) return `-${formatted}`;
      return formatted;
    }
    return CURRENCY_FORMAT.format(value);
  }
}

/** Percentages, with the same explicit-sign treatment. */
@Pipe({ name: 'percent2' })
export class Percent2Pipe implements PipeTransform {
  transform(value: number | null | undefined, signed = false): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '—';
    }
    const formatted = `${Math.abs(value).toFixed(2)}%`;
    if (!signed) return `${value.toFixed(2)}%`;
    if (value > 0) return `+${formatted}`;
    if (value < 0) return `-${formatted}`;
    return formatted;
  }
}
