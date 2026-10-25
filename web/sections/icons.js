// Tiny inline stroke icons for the highlight icon set (schema highlights.items.icon).
const P = {
  shield: '<path d="M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z"/>',
  verified_user: '<path d="M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z"/><path d="M9 12l2 2 4-4"/>',
  support_agent: '<path d="M5 14v-2a7 7 0 0114 0v2"/><rect x="4" y="13" width="3" height="5" rx="1"/><rect x="17" y="13" width="3" height="5" rx="1"/><path d="M19 18c0 2-3 3-7 3"/>',
  schedule: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  handshake: '<path d="M3 11l4-4 4 2 4-2 6 6-6 6-3-2-2 1-3-3-4-4z"/><path d="M9 14l3 3"/>',
  savings: '<circle cx="12" cy="12" r="9"/><path d="M15 8H9m6 3H9m1 0c3 0 5 1 5 3H9m1-6c3 0 4 1 4 3"/>',
  favorite: '<path d="M12 20s-8-5-8-11a4.5 4.5 0 018-2.8A4.5 4.5 0 0120 9c0 6-8 11-8 11z"/>',
  description: '<path d="M7 3h7l4 4v14H7z"/><path d="M14 3v4h4M9.5 12h6M9.5 16h6"/>',
  event_upcoming: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M8 3v4M16 3v4M9 15h6"/>',
  payments: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/><path d="M6.5 9.5v.01M17.5 14.5v.01"/>',
  chat: '<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9.5h8M8 12.5h5"/>',
  thumb_up: '<path d="M7 11v9H4v-9zM7 11l4-8c2 0 3 1 3 3l-1 4h6l-1 9H7"/>',
};
export const icon = (k) =>
  `<svg class="ico" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[k] || P.shield}</svg>`;
