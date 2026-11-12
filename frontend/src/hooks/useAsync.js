import { useCallback, useEffect, useRef, useState } from "react";

const UNSTABLE_FN_RENDER_LIMIT = 5;

/**
 * Runs `asyncFn` on mount, whenever `asyncFn` changes and on `reload()`.
 * Pass a stable function: preferably an api module function (`useAsync(listTrucks)`),
 * otherwise wrap it in `useCallback` with the values it reads as dependencies
 * (e.g. filters), so a changed filter starts a new request. An inline arrow function
 * would start a new request on every render (a dev-only warning reports it).
 *
 * Returns {data, error, loading, reload}. Data from the previous run is kept while
 * a new run is loading and cleared when it fails; results of an outdated run
 * (function changed, reloaded, unmounted) are ignored.
 */
export function useAsync(asyncFn) {
  const [runId, setRunId] = useState(0);
  const [settled, setSettled] = useState({
    asyncFn: null,
    runId: -1,
    data: undefined,
    error: null,
  });
  useUnstableFunctionWarning(asyncFn);

  useEffect(() => {
    let current = true;
    const finish = (outcome) => {
      if (current) {
        setSettled({ asyncFn, runId, ...outcome });
      }
    };
    Promise.resolve()
      .then(() => asyncFn())
      .then(
        (data) => finish({ data, error: null }),
        (error) => finish({ data: undefined, error }),
      );
    return () => {
      current = false;
    };
  }, [asyncFn, runId]);

  const reload = useCallback(() => setRunId((id) => id + 1), []);

  const loading = settled.asyncFn !== asyncFn || settled.runId !== runId;
  return {
    data: settled.data,
    error: loading ? null : settled.error,
    loading,
    reload,
  };
}

/** Dev only: warns once when `asyncFn` is a new function on many renders in a row. */
function useUnstableFunctionWarning(asyncFn) {
  const tracker = useRef({ fn: asyncFn, changes: 0, warned: false });
  useEffect(() => {
    if (!import.meta.env.DEV) {
      return;
    }
    const state = tracker.current;
    state.changes = state.fn === asyncFn ? 0 : state.changes + 1;
    state.fn = asyncFn;
    if (state.changes >= UNSTABLE_FN_RENDER_LIMIT && !state.warned) {
      state.warned = true;
      console.warn(
        "useAsync: the function changed on every render, so it is fetched again and again. " +
          "Pass an api module function or wrap it in useCallback.",
      );
    }
  });
}
