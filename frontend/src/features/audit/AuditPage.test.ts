import { describe, expect, it } from 'vitest';
import { auditQuery } from './AuditPage';

describe('auditQuery', () => {
  it('skips blank filters', () => {
    const q = new URLSearchParams(auditQuery({ entity: ' ', entityId: '', action: '', by: '', from: '', to: '' }, 0));
    expect([...q.keys()]).toEqual(['page', 'size']);
  });

  it('makes the "to" date inclusive by sending the following midnight', () => {
    const q = new URLSearchParams(auditQuery({ entity: 'USER', entityId: '', action: '', by: '', from: '2026-10-01', to: '2026-10-08' }, 2));
    expect(q.get('entity')).toBe('USER');
    expect(q.get('page')).toBe('2');
    expect(new Date(q.get('from')!).getTime()).toBe(new Date('2026-10-01T00:00:00').getTime());
    expect(new Date(q.get('to')!).getTime()).toBe(new Date('2026-10-09T00:00:00').getTime());
  });
});
