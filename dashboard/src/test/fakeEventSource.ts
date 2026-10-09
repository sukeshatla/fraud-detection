/** Minimal EventSource stand-in (jsdom has none): tests push server events with emit(). */
export class FakeEventSource {
  static instances: FakeEventSource[] = [];
  private listeners = new Map<string, ((event: MessageEvent<string>) => void)[]>();
  closed = false;

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  emit(type: string, data?: unknown) {
    const event = new MessageEvent<string>(type, { data: data === undefined ? '' : JSON.stringify(data) });
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }

  close() {
    this.closed = true;
  }

  static latest(): FakeEventSource {
    const latest = FakeEventSource.instances.at(-1);
    if (!latest) throw new Error('no EventSource was opened');
    return latest;
  }
}
