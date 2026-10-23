export interface User {
  externalId: string;
  code: string;
  role: string;
  branchExternalId: string | null;
  mustChangePassword: boolean;
}

export interface BootstrapDto {
  externalId: string;
  occurredAt: string;
  amount: string;
  [key: string]: unknown;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  code?: string;
  status?: number;
  [key: string]: unknown;
}

export class ApiError extends Error {
  status: number;
  problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Error HTTP ${status}.`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0;
const uuid = (value: unknown): value is string => typeof value === 'string' &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
const invalid = () => new Error('Respuesta JSON inválida del servidor.');

function userDto(value: unknown): User {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !text(value.role) ||
      !(value.branchExternalId === null || uuid(value.branchExternalId)) ||
      typeof value.mustChangePassword !== 'boolean') throw invalid();
  return value as unknown as User;
}

export class ApiClient {
  private baseUrl: string;
  private transport: typeof fetch;
  private onSessionExpired: () => void;
  private csrf: { token: string; headerName: string } | null = null;

  constructor(baseUrl: string, transport: typeof fetch = fetch, onSessionExpired = () => {}) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.transport = transport;
    this.onSessionExpired = onSessionExpired;
  }

  private async request(path: string, method = 'GET', body?: unknown): Promise<unknown> {
    if (method !== 'GET' && !this.csrf) await this.refreshCsrf();
    const headers = new Headers({ Accept: 'application/json' });
    if (body !== undefined) headers.set('Content-Type', 'application/json');
    if (method !== 'GET' && this.csrf) headers.set(this.csrf.headerName, this.csrf.token);
    const transport = this.transport;
    const response = await transport(`${this.baseUrl}/api/v1${path}`, {
      method, credentials: 'include', headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.status === 401) {
      this.csrf = null;
      this.onSessionExpired();
    }
    if (!response.ok) {
      let problem: ProblemDetail = {};
      try {
        const value: unknown = await response.json();
        if (record(value)) {
          problem = { ...value,
            title: typeof value.title === 'string' ? value.title : undefined,
            detail: typeof value.detail === 'string' ? value.detail : undefined,
          };
        }
      } catch { /* Keep HTTP status even when a gateway returns non-JSON. */ }
      throw new ApiError(response.status, problem);
    }
    if (response.status === 204) return undefined;
    try { return await response.json(); } catch { throw invalid(); }
  }

  private async refreshCsrf(): Promise<void> {
    this.csrf = null;
    const value = await this.request('/auth/csrf');
    if (!record(value) || !text(value.token) || !text(value.headerName) ||
        !/^[!#$%&'*+.^_`|~0-9a-z-]+$/i.test(value.headerName) || /[\r\n]/.test(value.token)) throw invalid();
    this.csrf = { token: value.token, headerName: value.headerName };
  }

  async login(code: string, password: string): Promise<User> {
    const user = userDto(await this.request('/auth/login', 'POST', { code, password }));
    await this.refreshCsrf();
    return user;
  }

  async me(): Promise<User> {
    return userDto(await this.request('/auth/me'));
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await this.request('/auth/password', 'POST', { currentPassword, newPassword });
    this.onSessionExpired();
    await this.refreshCsrf();
  }

  async logout(): Promise<void> {
    await this.request('/auth/logout', 'POST');
    this.onSessionExpired();
    await this.refreshCsrf();
  }

  async bootstrap(): Promise<BootstrapDto> {
    const value = await this.request('/bootstrap');
    if (!record(value) || !uuid(value.externalId) || !text(value.occurredAt) ||
        !/^\d{4}-\d{2}-\d{2}T.*Z$/.test(value.occurredAt) || !Number.isFinite(Date.parse(value.occurredAt)) ||
        typeof value.amount !== 'string' || !/^-?\d+(\.\d+)?$/.test(value.amount)) throw invalid();
    return value as BootstrapDto;
  }
}
