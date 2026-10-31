// Placeholders in the shape of what's loading, instead of "Loading…" or an early "Nothing here." (docs/OFFERS.md,
// Admin panel). Grey blocks that pulse unless reduced motion is on; screen readers hear "Loading…" once.

const times = (n: number) => Array.from({ length: n }, (_, i) => i);

/** Rows for a table body while its first load runs. */
export function SkeletonRows({ cols, rows = 5 }: { cols: number; rows?: number }) {
  return <>{times(rows).map((r) => (
    <tr key={r} aria-hidden="true">{times(cols).map((c) => <td key={c}><span className={`sk ${c === 0 ? "sk-w60" : "sk-w40"}`} /></td>)}</tr>
  ))}</>;
}

/** Cards (the membership queue) while the first load runs. */
export function SkeletonCards({ count = 3 }: { count?: number }) {
  return <div aria-busy="true"><span className="sr-only">Loading…</span>{times(count).map((i) => (
    <div className="card" key={i} aria-hidden="true"><span className="sk sk-w40" /><span className="sk sk-w60" /><span className="sk sk-w40" /></div>
  ))}</div>;
}

/** The signed-in layout (nav and a page) while the session and access are checked. */
export function ShellSkeleton() {
  return (
    <div className="shell" aria-busy="true">
      <span className="sr-only">Loading…</span>
      <nav className="nav" aria-hidden="true"><span className="sk sk-w60" />{times(5).map((i) => <span key={i} className="sk sk-nav" />)}</nav>
      <main className="main" aria-hidden="true"><span className="sk sk-h1" /><table><tbody><SkeletonRows cols={4} /></tbody></table></main>
    </div>
  );
}
