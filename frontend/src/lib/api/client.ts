export interface User {
  externalId: string;
  code: string;
  role: string;
  branchExternalId: string | null;
  mustChangePassword: boolean;
}

export type Role = 'ADMIN' | 'MANAGER' | 'OPERATOR';
export interface Branch {
  externalId: string;
  code: string;
  name: string;
  active: boolean;
}
export interface UserInput {
  code: string;
  fullName: string;
  role: Role;
  branchExternalId: string | null;
}
export interface ManagedUser extends UserInput {
  externalId: string;
  active: boolean;
  mustChangePassword: boolean;
}

export interface InventoryItem { productExternalId: string; sku: string; name: string; physicalQuantity: string }
export interface OperationalDocument { externalId: string; code: string; status: string; occurredAt: string }
export interface Movement { externalId: string; occurredAt: string; kind: string; direction: string;
  sku: string; productName: string; quantity: string; unit: string; actorCode: string }
export interface Visibility {
  branch: Branch; inventory: InventoryItem[]; purchases: OperationalDocument[]; sales: OperationalDocument[];
  transfers: OperationalDocument[]; movements: Movement[];
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

function branchDto(value: unknown): Branch {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !text(value.name) ||
      typeof value.active !== 'boolean') throw invalid();
  return value as unknown as Branch;
}
function managedUserDto(value: unknown): ManagedUser {
  userDto(value);
  if (!record(value) || !text(value.fullName) || typeof value.active !== 'boolean' ||
      !['ADMIN', 'MANAGER', 'OPERATOR'].includes(String(value.role))) throw invalid();
  return value as unknown as ManagedUser;
}
function listDto<T>(value: unknown, parse: (item: unknown) => T): T[] {
  if (!Array.isArray(value)) throw invalid();
  return value.map(parse);
}
function inventoryDto(value: unknown): InventoryItem {
  if (!record(value) || !uuid(value.productExternalId) || !text(value.sku) || !text(value.name) ||
      typeof value.physicalQuantity !== 'string' || !/^\d+(\.\d+)?$/.test(value.physicalQuantity)) throw invalid();
  return value as unknown as InventoryItem;
}
function documentDto(value: unknown): OperationalDocument {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !text(value.status) ||
      !text(value.occurredAt) || !Number.isFinite(Date.parse(value.occurredAt))) throw invalid();
  return value as unknown as OperationalDocument;
}
function movementDto(value: unknown): Movement {
  if (!record(value) || !uuid(value.externalId) || !text(value.occurredAt) ||
      !Number.isFinite(Date.parse(value.occurredAt)) ||
      !['kind', 'direction', 'sku', 'productName', 'unit', 'actorCode'].every(k => text(value[k])) ||
      typeof value.quantity !== 'string' || !/^\d+(\.\d+)?$/.test(value.quantity)) throw invalid();
  return value as unknown as Movement;
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

  async branches(): Promise<Branch[]> {
    return listDto(await this.request('/branches'), branchDto);
  }
  async branch(id: string): Promise<Branch> {
    return branchDto(await this.request(`/branches/${encodeURIComponent(id)}`));
  }
  async createBranch(data: { code: string; name: string }): Promise<Branch> {
    return branchDto(await this.request('/branches', 'POST', data));
  }
  async updateBranch(id: string, data: { name: string }): Promise<Branch> {
    return branchDto(await this.request(`/branches/${encodeURIComponent(id)}/update`, 'POST', data));
  }
  async deactivateBranch(id: string): Promise<Branch> {
    return branchDto(await this.request(`/branches/${encodeURIComponent(id)}/deactivate`, 'POST'));
  }
  async users(): Promise<ManagedUser[]> {
    return listDto(await this.request('/users'), managedUserDto);
  }
  async user(id: string): Promise<ManagedUser> {
    return managedUserDto(await this.request(`/users/${encodeURIComponent(id)}`));
  }
  async createUser(data: UserInput & { temporaryPassword: string }): Promise<ManagedUser> {
    return managedUserDto(await this.request('/users', 'POST', data));
  }
  async updateUser(id: string, data: UserInput): Promise<ManagedUser> {
    return managedUserDto(await this.request(`/users/${encodeURIComponent(id)}/update`, 'POST', data));
  }
  async deactivateUser(id: string): Promise<void> {
    await this.request(`/users/${encodeURIComponent(id)}/deactivate`, 'POST');
  }
  async visibleBranches(): Promise<Branch[]> {
    return listDto(await this.request('/visibility/branches'), branchDto);
  }
  async inventory(id: string): Promise<InventoryItem[]> {
    return listDto(await this.request(`/visibility/inventory/${encodeURIComponent(id)}`), inventoryDto);
  }
  async visibility(id: string): Promise<Visibility> {
    const value = await this.request(`/visibility/branches/${encodeURIComponent(id)}`);
    if (!record(value)) throw invalid();
    return { branch: branchDto(value.branch), inventory: listDto(value.inventory, inventoryDto),
      purchases: listDto(value.purchases, documentDto), sales: listDto(value.sales, documentDto),
      transfers: listDto(value.transfers, documentDto), movements: listDto(value.movements, movementDto) };
  }
}
