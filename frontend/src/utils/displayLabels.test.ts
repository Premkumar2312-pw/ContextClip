import { describe, it, expect } from 'vitest';
import { formatLabel } from './displayLabels';

describe('formatLabel', () => {
  it('formats raw enums into human-readable labels', () => {
    expect(formatLabel('TERMINAL_COMMAND')).toBe('Terminal Command');
    expect(formatLabel('PLAIN_TEXT')).toBe('Plain Text');
    expect(formatLabel('TEXT')).toBe('Plain Text');
    expect(formatLabel('UNKNOWN')).toBe('Unknown');
    expect(formatLabel('SPRING_BOOT')).toBe('Spring Boot');
    expect(formatLabel('NODE_JS')).toBe('Node.js');
    expect(formatLabel('SQL')).toBe('SQL');
    expect(formatLabel('JSON')).toBe('JSON');
    expect(formatLabel('DEVOPS')).toBe('DevOps');
  });

  it('handles null, undefined, and empty string', () => {
    expect(formatLabel(null)).toBe('');
    expect(formatLabel(undefined)).toBe('');
    expect(formatLabel('')).toBe('');
    expect(formatLabel('   ')).toBe('');
  });

  it('formats unknown snake_case strings into title case fallback', () => {
    expect(formatLabel('SOME_CUSTOM_TYPE')).toBe('Some Custom Type');
  });
});
