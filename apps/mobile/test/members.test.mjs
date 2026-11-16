// Run: npm test
import test from 'node:test';
import assert from 'node:assert/strict';
import { canManageMembers, looksLikeEmail, passwordProblem, roleLabel, statusLabel } from '../src/lib/members.ts';

test('only admins and superusers manage members', () => {
  assert.equal(canManageMembers('player'), false);
  assert.equal(canManageMembers('admin'), true);
  assert.equal(canManageMembers('superuser'), true);
  assert.equal(canManageMembers(undefined), false);
});

test('password checks match the server rules', () => {
  assert.equal(passwordProblem('short', 'short'), 'Use at least 8 characters.');
  assert.equal(passwordProblem('x'.repeat(65), 'x'.repeat(65)), 'Use at most 64 characters.');
  assert.equal(passwordProblem('long enough', 'long enougH'), "The two passwords don't match.");
  assert.equal(passwordProblem('long enough', 'long enough'), null);
});

test('labels', () => {
  assert.equal(roleLabel('superuser'), 'Support');
  assert.equal(statusLabel('deactivated'), 'Deactivated');
});

test('email shape', () => {
  assert.equal(looksLikeEmail(' ann@example.org '), true);
  assert.equal(looksLikeEmail('ann@example'), false);
  assert.equal(looksLikeEmail('ann example.org'), false);
});
