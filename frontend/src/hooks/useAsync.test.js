import { act, renderHook, waitFor } from "@testing-library/react";
import { useCallback } from "react";
import { ApiError } from "../api/client.js";
import { useAsync } from "./useAsync.js";

function deferred() {
  let resolve;
  const promise = new Promise((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

describe("useAsync", () => {
  it("starts loading, then exposes the data", async () => {
    const load = vi.fn().mockResolvedValue([{ id: 1 }]);

    const { result } = renderHook(() => useAsync(load));

    expect(result.current.loading).toBe(true);
    expect(result.current.data).toBeUndefined();
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual([{ id: 1 }]);
    expect(result.current.error).toBeNull();
  });

  it("exposes the error and loads again on reload", async () => {
    const failure = new ApiError({ status: 0, code: "NETWORK", detail: "x" });
    const load = vi
      .fn()
      .mockRejectedValueOnce(failure)
      .mockResolvedValueOnce(["ok"]);

    const { result } = renderHook(() => useAsync(load));

    await waitFor(() => expect(result.current.error).toBe(failure));
    expect(result.current.loading).toBe(false);

    act(() => result.current.reload());

    expect(result.current.loading).toBe(true);
    expect(result.current.error).toBeNull();
    await waitFor(() => expect(result.current.data).toEqual(["ok"]));
    expect(result.current.loading).toBe(false);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("reruns when a dependency changes and ignores the outdated result", async () => {
    const first = deferred();
    const second = deferred();
    const load = vi.fn((filter) =>
      filter === "A" ? first.promise : second.promise,
    );

    const { result, rerender } = renderHook(
      ({ filter }) => useAsync(useCallback(() => load(filter), [filter])),
      { initialProps: { filter: "A" } },
    );
    rerender({ filter: "B" });
    expect(result.current.loading).toBe(true);

    await act(async () => second.resolve("result B"));
    await act(async () => first.resolve("result A"));

    expect(load).toHaveBeenCalledWith("B");
    expect(result.current.data).toBe("result B");
    expect(result.current.loading).toBe(false);
  });

  it("clears the previous data when a reload fails", async () => {
    const failure = new ApiError({ status: 500, code: "INTERNAL_ERROR" });
    const load = vi
      .fn()
      .mockResolvedValueOnce(["old"])
      .mockRejectedValueOnce(failure);

    const { result } = renderHook(() => useAsync(load));
    await waitFor(() => expect(result.current.data).toEqual(["old"]));

    act(() => result.current.reload());
    expect(result.current.data).toEqual(["old"]);

    await waitFor(() => expect(result.current.error).toBe(failure));
    expect(result.current.data).toBeUndefined();
  });

  it("ignores a result that arrives after unmount", async () => {
    const pending = deferred();
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const { result, unmount } = renderHook(() =>
      useAsync(() => pending.promise),
    );
    const lastState = result.current;
    unmount();
    await act(async () => pending.resolve("late"));

    expect(result.current).toBe(lastState);
    expect(errorSpy).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });

  it("warns in development when the function changes on every render", async () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

    const { unmount } = renderHook(() => useAsync(() => Promise.resolve(1)));

    await waitFor(() => expect(warnSpy).toHaveBeenCalledTimes(1));
    expect(warnSpy.mock.calls[0][0]).toMatch(/changed on every render/);
    unmount();
    warnSpy.mockRestore();
  });

  it("does not warn for a stable function", async () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const load = vi.fn().mockResolvedValue(1);

    const { result, rerender } = renderHook(() => useAsync(load));
    await waitFor(() => expect(result.current.loading).toBe(false));
    rerender();
    rerender();

    expect(warnSpy).not.toHaveBeenCalled();
    warnSpy.mockRestore();
  });
});
