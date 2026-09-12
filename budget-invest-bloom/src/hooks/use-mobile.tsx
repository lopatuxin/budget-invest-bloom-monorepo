import * as React from "react"

export const MOBILE_BREAKPOINT = 768

// The complement of Tailwind's `min-width: <breakpoint>px`, so a JS branch and a CSS branch on the
// same breakpoint agree. Both are media queries, which is the point: `window.innerWidth` counts the
// vertical scrollbar and a media query does not, leaving a ~15px band where the two disagree.
// The 0.02px step is the Bootstrap convention against browsers rounding fractional widths; it
// leaves [bp-0.02, bp) covered by neither range, reachable only at non-integer zoom levels.
const narrowerThanQuery = (breakpoint: number) => `(max-width: ${breakpoint - 0.02}px)`

// Generic version of useIsMobile below, parameterized on the breakpoint — a shared component
// (e.g. CategoryFormDialog) rendered from pages with different mobile/desktop cutoffs needs its
// own threshold without shifting MOBILE_BREAKPOINT, which every other consumer of useIsMobile
// still relies on.
export function useIsNarrowerThan(breakpoint: number) {
  // Read synchronously on mount instead of starting at `undefined` and waiting for an effect —
  // otherwise the first frame always renders the desktop branch (every consumer defaults falsy),
  // then flips right after, which is visible as a flash of the wrong layout on a phone.
  const [isNarrow, setIsNarrow] = React.useState(() => window.matchMedia(narrowerThanQuery(breakpoint)).matches)

  React.useEffect(() => {
    const mql = window.matchMedia(narrowerThanQuery(breakpoint))
    const onChange = () => setIsNarrow(mql.matches)
    // Re-read on subscribe, not just on later `change` events: the width can cross the breakpoint
    // between the render that seeded the state and this commit, and matchMedia only fires when the
    // boundary is crossed — so a value stale by then would stay stale until the next crossing.
    // This also resyncs when `breakpoint` itself changes, since the state was seeded from the old one.
    onChange()
    mql.addEventListener("change", onChange)
    return () => mql.removeEventListener("change", onChange)
  }, [breakpoint])

  return isNarrow
}

export function useIsMobile() {
  return useIsNarrowerThan(MOBILE_BREAKPOINT)
}
