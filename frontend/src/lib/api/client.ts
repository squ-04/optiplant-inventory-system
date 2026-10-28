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

export interface ProductAttributes {
  sku: string;
  name: string;
  description: string | null;
  baseUnitName: string;
  baseDivisible: boolean;
}
/** Minimum stock in base units as an exact decimal string; never converted to a JavaScript number. */
export interface ProductEnablement { branchExternalId: string; minimumQuantity: string }
export interface ProductBranch {
  branchExternalId: string; branchCode: string; branchName: string; branchActive: boolean;
  enabled: boolean; minimumQuantity: string | null;
}
/** A unit of one product; `factor` is the exact base equivalent of one unit as a decimal string (base unit: 1). */
export interface ProductUnit { name: string; factor: string; divisible: boolean; base: boolean; active: boolean }
export interface ProductUnitInput { name: string; factor: string; divisible: boolean }
export interface ProductUnitChange { name: string; factor: string; active: boolean }
export interface Product extends ProductAttributes {
  externalId: string;
  active: boolean;
  inUse: boolean;
  /** Local enablement, returned to administrators only. */
  branches: ProductBranch[] | null;
  /** Units of the product, returned by the detail query only. */
  units: ProductUnit[] | null;
}
export interface UnitConversion { unitName: string; quantity: string; factor: string; baseUnitName: string; baseQuantity: string }
/** Products enabled in the session's own branch, with exact base-unit quantities. */
export interface LocalCatalogItem {
  productExternalId: string; sku: string; name: string; description: string | null; baseUnitName: string;
  baseDivisible: boolean; minimumQuantity: string; physicalQuantity: string; availableQuantity: string;
  inTransitQuantity: string;
  /** RF-0020 operational indicator: an active restock alert exists for this product in the branch. */
  restockAlert: boolean; units: ProductUnit[];
}
export interface LocalCatalog {
  branchExternalId: string; branchCode: string; branchName: string; branchActive: boolean; items: LocalCatalogItem[];
}

export interface InventoryItem { productExternalId: string; sku: string; name: string; physicalQuantity: string }
/** RF-0014: stock of one branch in exact base-unit quantities; transit is never physical or available stock. */
export interface StockItem {
  productExternalId: string; sku: string; name: string; baseUnitName: string;
  physicalQuantity: string; availableQuantity: string; inTransitQuantity: string;
}
export interface BranchInventory { branch: Branch; readAt: string; items: StockItem[] }
export interface OperationalDocument { externalId: string; code: string; status: string; occurredAt: string }
export interface Movement { externalId: string; occurredAt: string; kind: string; direction: string;
  sku: string; productName: string; quantity: string; unit: string; actorCode: string }
export interface Visibility {
  branch: Branch; inventory: InventoryItem[]; purchases: OperationalDocument[]; sales: OperationalDocument[];
  transfers: OperationalDocument[]; movements: Movement[];
}

export type ReorderAlertStatus = 'ACTIVE' | 'RESOLVED';
/**
 * RF-0020: one restock alert episode of a product in a branch. The minimum and stock captured when it opened are kept
 * next to the current values; `currentMinimum` is null once the product is no longer enabled there.
 */
export interface ReorderAlert {
  externalId: string; status: ReorderAlertStatus; branch: Branch;
  product: { externalId: string; sku: string; name: string; baseUnitName: string };
  openedAt: string; resolvedAt: string | null; minimumAtOpening: string; quantityAtOpening: string;
  currentMinimum: string | null; currentQuantity: string;
}
/** `branch` is null when the administrator reads the whole network; `activeCount` ignores the row limit. */
export interface ReorderAlerts { readAt: string; branch: Branch | null; activeCount: number; items: ReorderAlert[] }

export type MovementKind = 'MANUAL_ENTRY' | 'SALE_RETURN' | 'MANUAL_EXIT' | 'COUNT_ADJUSTMENT' | 'ENTRY_CORRECTION';
export type MovementDirection = 'IN' | 'OUT';
/**
 * RF-0016/RF-0017: a manual movement of the session's own branch. Quantities and costs are exact decimal strings.
 * A referenced return or a correction sends `reference` and omits product and unit.
 */
export interface ManualMovementInput {
  kind: MovementKind; direction?: MovementDirection; product?: string; unit?: string; quantity: string;
  unitCost?: string; reference?: string; reason: string;
}
export interface ManualMovement {
  operationExternalId: string; movementExternalId: string; occurredAt: string; kind: string; direction: MovementDirection;
  productExternalId: string; sku: string; productName: string; unit: string; commercialQuantity: string;
  baseQuantity: string; baseUnitName: string; reason: string; responsibleCode: string; referenceExternalId: string | null;
  resultingPhysicalQuantity: string;
}
/** Signed value effects at display precision; returned to managers and administrators only (RF-0026). */
export interface MovementValuation {
  unitCost: string; baseAmount: string; differenceKind: string | null; differenceAmount: string | null;
  exactDifferenceAmount: string | null; totalAmount: string;
}
export interface MovementHistoryEntry {
  externalId: string; operationExternalId: string; occurredAt: string; kind: string; direction: MovementDirection;
  productExternalId: string; sku: string; productName: string; unit: string; commercialQuantity: string;
  baseQuantity: string; baseUnitName: string; reason: string; responsible: { code: string; fullName: string };
  referenceExternalId: string | null; pendingQuantity: string | null; valuation: MovementValuation | null;
}
/** RF-0018: auditable movements of one branch, newest first. */
export interface MovementHistory { branch: Branch; readAt: string; items: MovementHistoryEntry[] }

/** RF-0021: minimum supplier reference; no fiscal or contact data. */
export interface Supplier { externalId: string; code: string; name: string; active: boolean }
export type PurchaseStatus = 'DRAFT' | 'PENDING' | 'RECEIVED' | 'CANCELLED';
/** Commercial conditions of one line (RF-0023), as exact decimal strings in API format. */
export interface PurchaseLineInput { product: string; unit: string; quantity: string; unitPrice: string; discountPercent: string }
export interface PurchaseOrderInput { supplier: string; paymentTermDays: number; lines: PurchaseLineInput[] }
export interface PurchasePerson { code: string; fullName: string }
export interface PurchaseLine {
  lineNo: number; productExternalId: string; sku: string; productName: string; unit: string; factor: string;
  commercialQuantity: string; baseQuantity: string; baseUnitName: string; unitPrice: string; discountPercent: string;
  /** Line net at 2 decimals for display; `exactNetAmount` keeps every decimal. */
  netAmount: string; exactNetAmount: string;
  /** Net cost per base unit fixed by the receipt; null before it. */
  unitBaseCost: string | null;
}
export interface PurchaseEvent { fromStatus: PurchaseStatus | null; toStatus: PurchaseStatus; occurredAt: string; actor: PurchasePerson }
export interface PurchaseOrder {
  externalId: string; code: string; status: PurchaseStatus; branch: Branch;
  /** `name` is the historical supplier name kept by the order (RF-0021-CA-03). */
  supplier: { externalId: string; code: string; name: string };
  paymentTermDays: number; createdAt: string; createdBy: PurchasePerson;
  receivedAt: string | null; receivedBy: PurchasePerson | null; dueDate: string | null;
  receiptOperationExternalId: string | null; totalNet: string; exactTotalNet: string; lines: PurchaseLine[];
  /** State history, returned by the detail query only. */
  events: PurchaseEvent[] | null;
}
export interface PurchaseSummary { orders: number; drafts: number; pending: number; received: number; cancelled: number }
export interface PurchaseHistory { readAt: string; branch: Branch | null; summary: PurchaseSummary; items: PurchaseOrder[] }
export interface PurchaseHistoryFilter { branch?: string; supplier?: string; product?: string; status?: PurchaseStatus; limit?: number }

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

/** Session loss or a pending password change concern the whole shell; other rejections stay in their own view. */
export const sessionProblem = (reason: unknown) => reason instanceof ApiError
  && (reason.status === 401 || reason.problem.code === 'PASSWORD_CHANGE_REQUIRED');

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
const decimal = (value: unknown) => typeof value === 'string' && /^\d+(\.\d+)?$/.test(value);
function productBranchDto(value: unknown): ProductBranch {
  if (!record(value) || !uuid(value.branchExternalId) || !text(value.branchCode) || !text(value.branchName) ||
      typeof value.branchActive !== 'boolean' || typeof value.enabled !== 'boolean' ||
      !(value.minimumQuantity === null || decimal(value.minimumQuantity))) throw invalid();
  return value as unknown as ProductBranch;
}
function productUnitDto(value: unknown): ProductUnit {
  if (!record(value) || !text(value.name) || !decimal(value.factor) || typeof value.divisible !== 'boolean' ||
      typeof value.base !== 'boolean' || typeof value.active !== 'boolean') throw invalid();
  return value as unknown as ProductUnit;
}
function productDto(value: unknown): Product {
  if (!record(value) || !uuid(value.externalId) || !text(value.sku) || !text(value.name) ||
      !(value.description === null || typeof value.description === 'string') || !text(value.baseUnitName) ||
      typeof value.baseDivisible !== 'boolean' || typeof value.active !== 'boolean' || typeof value.inUse !== 'boolean' ||
      !(value.branches === null || Array.isArray(value.branches)) ||
      !(value.units === null || value.units === undefined || Array.isArray(value.units))) throw invalid();
  if (value.branches !== null) listDto(value.branches, productBranchDto);
  if (Array.isArray(value.units)) listDto(value.units, productUnitDto);
  return { ...(value as unknown as Product), units: Array.isArray(value.units) ? value.units as ProductUnit[] : null };
}
function conversionDto(value: unknown): UnitConversion {
  if (!record(value) || !text(value.unitName) || !decimal(value.quantity) || !decimal(value.factor) ||
      !text(value.baseUnitName) || !decimal(value.baseQuantity)) throw invalid();
  return value as unknown as UnitConversion;
}
function localItemDto(value: unknown): LocalCatalogItem {
  if (!record(value) || !uuid(value.productExternalId) || !text(value.sku) || !text(value.name) ||
      !(value.description === null || typeof value.description === 'string') || !text(value.baseUnitName) ||
      typeof value.baseDivisible !== 'boolean' ||
      !['minimumQuantity', 'physicalQuantity', 'availableQuantity', 'inTransitQuantity'].every(k => decimal(value[k])) ||
      typeof value.restockAlert !== 'boolean') throw invalid();
  listDto(value.units, productUnitDto);
  return value as unknown as LocalCatalogItem;
}
function inventoryDto(value: unknown): InventoryItem {
  if (!record(value) || !uuid(value.productExternalId) || !text(value.sku) || !text(value.name) ||
      typeof value.physicalQuantity !== 'string' || !/^\d+(\.\d+)?$/.test(value.physicalQuantity)) throw invalid();
  return value as unknown as InventoryItem;
}
function stockDto(value: unknown): StockItem {
  if (!record(value) || !uuid(value.productExternalId) || !text(value.sku) || !text(value.name) || !text(value.baseUnitName) ||
      !['physicalQuantity', 'availableQuantity', 'inTransitQuantity'].every(k => decimal(value[k]))) throw invalid();
  return value as unknown as StockItem;
}
function documentDto(value: unknown): OperationalDocument {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !text(value.status) ||
      !text(value.occurredAt) || !Number.isFinite(Date.parse(value.occurredAt))) throw invalid();
  return value as unknown as OperationalDocument;
}
const signed = (value: unknown) => typeof value === 'string' && /^-?\d+(\.\d+)?$/.test(value);
const direction = (value: unknown) => value === 'IN' || value === 'OUT';
const instant = (value: unknown) => text(value) && Number.isFinite(Date.parse(value));
function manualMovementDto(value: unknown): ManualMovement {
  if (!record(value) || !uuid(value.operationExternalId) || !uuid(value.movementExternalId) || !instant(value.occurredAt) ||
      !direction(value.direction) || !uuid(value.productExternalId) ||
      !['kind', 'sku', 'productName', 'unit', 'baseUnitName', 'reason', 'responsibleCode'].every(k => text(value[k])) ||
      !['commercialQuantity', 'baseQuantity', 'resultingPhysicalQuantity'].every(k => decimal(value[k])) ||
      !(value.referenceExternalId === null || uuid(value.referenceExternalId))) throw invalid();
  return value as unknown as ManualMovement;
}
function valuationDto(value: unknown): MovementValuation | null {
  if (value === null) return null;
  if (!record(value) || !decimal(value.unitCost) || !signed(value.baseAmount) || !signed(value.totalAmount) ||
      !(value.differenceKind === null || text(value.differenceKind)) ||
      !(value.differenceAmount === null || signed(value.differenceAmount)) ||
      !(value.exactDifferenceAmount === null || signed(value.exactDifferenceAmount))) throw invalid();
  return value as unknown as MovementValuation;
}
function historyEntryDto(value: unknown): MovementHistoryEntry {
  if (!record(value) || !uuid(value.externalId) || !uuid(value.operationExternalId) || !instant(value.occurredAt) ||
      !direction(value.direction) || !uuid(value.productExternalId) ||
      !['kind', 'sku', 'productName', 'unit', 'baseUnitName', 'reason'].every(k => text(value[k])) ||
      !['commercialQuantity', 'baseQuantity'].every(k => decimal(value[k])) ||
      !record(value.responsible) || !text(value.responsible.code) || !text(value.responsible.fullName) ||
      !(value.referenceExternalId === null || uuid(value.referenceExternalId)) ||
      !(value.pendingQuantity === null || decimal(value.pendingQuantity))) throw invalid();
  return { ...(value as unknown as MovementHistoryEntry), valuation: valuationDto(value.valuation) };
}

function reorderAlertDto(value: unknown): ReorderAlert {
  if (!record(value) || !uuid(value.externalId) || !['ACTIVE', 'RESOLVED'].includes(String(value.status)) ||
      !record(value.product) || !uuid(value.product.externalId) || !text(value.product.sku) || !text(value.product.name) ||
      !text(value.product.baseUnitName) || !instant(value.openedAt) ||
      (value.status === 'ACTIVE' ? value.resolvedAt !== null : !instant(value.resolvedAt)) ||
      !['minimumAtOpening', 'quantityAtOpening', 'currentQuantity'].every(k => decimal(value[k])) ||
      !(value.currentMinimum === null || decimal(value.currentMinimum))) throw invalid();
  branchDto(value.branch);
  return value as unknown as ReorderAlert;
}
function movementDto(value: unknown): Movement {
  if (!record(value) || !uuid(value.externalId) || !text(value.occurredAt) ||
      !Number.isFinite(Date.parse(value.occurredAt)) ||
      !['kind', 'direction', 'sku', 'productName', 'unit', 'actorCode'].every(k => text(value[k])) ||
      typeof value.quantity !== 'string' || !/^\d+(\.\d+)?$/.test(value.quantity)) throw invalid();
  return value as unknown as Movement;
}

const PURCHASE_STATUSES = ['DRAFT', 'PENDING', 'RECEIVED', 'CANCELLED'];
const count = (value: unknown) => typeof value === 'number' && Number.isInteger(value) && value >= 0;
const day = (value: unknown) => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
function supplierDto(value: unknown): Supplier {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !text(value.name) ||
      typeof value.active !== 'boolean') throw invalid();
  return value as unknown as Supplier;
}
function personDto(value: unknown): PurchasePerson {
  if (!record(value) || !text(value.code) || !text(value.fullName)) throw invalid();
  return value as unknown as PurchasePerson;
}
function purchaseLineDto(value: unknown): PurchaseLine {
  if (!record(value) || !count(value.lineNo) || !uuid(value.productExternalId) ||
      !['sku', 'productName', 'unit', 'baseUnitName'].every(k => text(value[k])) ||
      !['factor', 'commercialQuantity', 'baseQuantity', 'unitPrice', 'discountPercent', 'netAmount', 'exactNetAmount']
        .every(k => decimal(value[k])) ||
      !(value.unitBaseCost === null || decimal(value.unitBaseCost))) throw invalid();
  return value as unknown as PurchaseLine;
}
function purchaseEventDto(value: unknown): PurchaseEvent {
  if (!record(value) || !(value.fromStatus === null || PURCHASE_STATUSES.includes(String(value.fromStatus))) ||
      !PURCHASE_STATUSES.includes(String(value.toStatus)) || !instant(value.occurredAt)) throw invalid();
  personDto(value.actor);
  return value as unknown as PurchaseEvent;
}
function purchaseDto(value: unknown): PurchaseOrder {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !PURCHASE_STATUSES.includes(String(value.status)) ||
      !record(value.supplier) || !uuid(value.supplier.externalId) || !text(value.supplier.code) || !text(value.supplier.name) ||
      !count(value.paymentTermDays) || !instant(value.createdAt) || !decimal(value.totalNet) || !decimal(value.exactTotalNet)) {
    throw invalid();
  }
  // The receipt fields exist together, only for a received order (purchases_order CHECK).
  const received = value.status === 'RECEIVED';
  if (received ? !instant(value.receivedAt) || !day(value.dueDate) || !uuid(value.receiptOperationExternalId)
    : value.receivedAt !== null || value.dueDate !== null || value.receivedBy !== null) throw invalid();
  branchDto(value.branch);
  personDto(value.createdBy);
  if (received) personDto(value.receivedBy);
  return { ...(value as unknown as PurchaseOrder), lines: listDto(value.lines, purchaseLineDto),
    events: value.events === null ? null : listDto(value.events, purchaseEventDto) };
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

  private async request(path: string, method = 'GET', body?: unknown, signal?: AbortSignal,
                        extra: Record<string, string> = {}): Promise<unknown> {
    if (method !== 'GET' && !this.csrf) await this.refreshCsrf();
    const headers = new Headers({ ...extra, Accept: 'application/json' });
    if (body !== undefined) headers.set('Content-Type', 'application/json');
    if (method !== 'GET' && this.csrf) headers.set(this.csrf.headerName, this.csrf.token);
    const transport = this.transport;
    const response = await transport(`${this.baseUrl}/api/v1${path}`, {
      method, credentials: 'include', headers, signal,
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
  async products(): Promise<Product[]> {
    return listDto(await this.request('/products'), productDto);
  }
  async product(id: string): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}`));
  }
  async createProduct(data: ProductAttributes & { branches: ProductEnablement[] }): Promise<Product> {
    return productDto(await this.request('/products', 'POST', data));
  }
  async updateProduct(id: string, data: ProductAttributes): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}/update`, 'POST', data));
  }
  async enableProduct(id: string, data: ProductEnablement): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}/branches`, 'POST', data));
  }
  /** Logical deactivation only: the API exposes no physical deletion. */
  async deactivateProduct(id: string): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}/deactivate`, 'POST'));
  }
  async reactivateProduct(id: string): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}/reactivate`, 'POST'));
  }
  /** RF-0001: changes only the explicit minimum of an enabled product-branch combination. */
  async updateMinimum(id: string, branchId: string, minimumQuantity: string): Promise<Product> {
    return productDto(await this.request(
      `/products/${encodeURIComponent(id)}/branches/${encodeURIComponent(branchId)}/minimum`, 'POST', { minimumQuantity }));
  }
  async createUnit(id: string, data: ProductUnitInput): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}/units`, 'POST', data));
  }
  async updateUnit(id: string, data: ProductUnitChange): Promise<Product> {
    return productDto(await this.request(`/products/${encodeURIComponent(id)}/units/update`, 'POST', data));
  }
  async convertUnit(id: string, unit: string, quantity: string): Promise<UnitConversion> {
    const query = new URLSearchParams({ unit, quantity });
    return conversionDto(await this.request(`/products/${encodeURIComponent(id)}/units/convert?${query}`));
  }
  /** RF-0012: the branch always comes from the session. */
  async localCatalog(): Promise<LocalCatalog> {
    const value = await this.request('/products/local');
    if (!record(value) || !uuid(value.branchExternalId) || !text(value.branchCode) || !text(value.branchName) ||
        typeof value.branchActive !== 'boolean') throw invalid();
    return { ...(value as unknown as LocalCatalog), items: listDto(value.items, localItemDto) };
  }
  async visibleBranches(): Promise<Branch[]> {
    return listDto(await this.request('/visibility/branches'), branchDto);
  }
  async inventory(id: string): Promise<InventoryItem[]> {
    return listDto(await this.request(`/visibility/inventory/${encodeURIComponent(id)}`), inventoryDto);
  }
  /** RF-0014/RF-0015: read-only stock of any branch; polled by the view, a GET never counts as session activity. */
  async branchInventory(id: string, signal?: AbortSignal): Promise<BranchInventory> {
    const value = await this.request(`/visibility/branches/${encodeURIComponent(id)}/inventory`, 'GET', undefined, signal);
    if (!record(value) || !text(value.readAt) || !Number.isFinite(Date.parse(value.readAt))) throw invalid();
    return { branch: branchDto(value.branch), readAt: value.readAt, items: listDto(value.items, stockDto) };
  }
  async visibility(id: string): Promise<Visibility> {
    const value = await this.request(`/visibility/branches/${encodeURIComponent(id)}`);
    if (!record(value)) throw invalid();
    return { branch: branchDto(value.branch), inventory: listDto(value.inventory, inventoryDto),
      purchases: listDto(value.purchases, documentDto), sales: listDto(value.sales, documentDto),
      transfers: listDto(value.transfers, documentDto), movements: listDto(value.movements, movementDto) };
  }
  /**
   * RF-0016/RF-0017: registers a manual movement in the session's own branch. Reuse the same key to retry a request
   * whose outcome is unknown (connection loss): the server returns the original result without new effects.
   */
  async registerMovement(input: ManualMovementInput, idempotencyKey: string): Promise<ManualMovement> {
    return manualMovementDto(await this.request('/inventory/movements', 'POST', input, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0018: movement history of a branch, newest first. */
  async movementHistory(branch: string, options: { product?: string; limit?: number } = {}): Promise<MovementHistory> {
    const query = new URLSearchParams();
    if (options.product) query.set('product', options.product);
    if (options.limit !== undefined) query.set('limit', String(options.limit));
    const suffix = query.size > 0 ? `?${query}` : '';
    const value = await this.request(`/inventory/branches/${encodeURIComponent(branch)}/movements${suffix}`);
    if (!record(value) || !instant(value.readAt)) throw invalid();
    return { branch: branchDto(value.branch), readAt: value.readAt as string, items: listDto(value.items, historyEntryDto) };
  }
  /**
   * RF-0020: active restock alerts (default) or their whole history. Managers read their own branch; administrators a
   * branch or, without one, the whole network. Operators are rejected and use the local catalog indicator instead.
   */
  async reorderAlerts(options: { branch?: string; history?: boolean; limit?: number } = {}, signal?: AbortSignal): Promise<ReorderAlerts> {
    const query = new URLSearchParams();
    if (options.branch) query.set('branch', options.branch);
    if (options.history) query.set('status', 'ALL');
    if (options.limit !== undefined) query.set('limit', String(options.limit));
    const suffix = query.size > 0 ? `?${query}` : '';
    const value = await this.request(`/inventory/reorder-alerts${suffix}`, 'GET', undefined, signal);
    if (!record(value) || !instant(value.readAt) || typeof value.activeCount !== 'number' ||
        !Number.isInteger(value.activeCount) || value.activeCount < 0) throw invalid();
    return { readAt: value.readAt as string, branch: value.branch === null ? null : branchDto(value.branch),
      activeCount: value.activeCount, items: listDto(value.items, reorderAlertDto) };
  }
  /** RF-0021: suppliers, readable by every profile to use them in purchases. */
  async suppliers(): Promise<Supplier[]> {
    return listDto(await this.request('/purchases/suppliers'), supplierDto);
  }
  async createSupplier(data: { code: string; name: string }): Promise<Supplier> {
    return supplierDto(await this.request('/purchases/suppliers', 'POST', data));
  }
  async updateSupplier(id: string, data: { code: string; name: string }): Promise<Supplier> {
    return supplierDto(await this.request(`/purchases/suppliers/${encodeURIComponent(id)}/update`, 'POST', data));
  }
  /**
   * RF-0021/RF-0023: creates a draft order in the operator's branch. Reuse the same key to retry a request whose outcome
   * is unknown; the server returns the original order without creating another.
   */
  async createPurchase(input: PurchaseOrderInput, idempotencyKey: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request('/purchases/orders', 'POST', input, undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0021-CA-03: moves a draft to pending receipt. */
  async submitPurchase(id: string, idempotencyKey: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request(`/purchases/orders/${encodeURIComponent(id)}/submit`, 'POST', {}, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0024: confirms the complete receipt; stock increases once through the inventory ledger. */
  async receivePurchase(id: string, idempotencyKey: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request(`/purchases/orders/${encodeURIComponent(id)}/receive`, 'POST', {}, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  async purchase(id: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request(`/purchases/orders/${encodeURIComponent(id)}`));
  }
  /** RF-0025: purchase history by branch, supplier, product and status, newest first. */
  async purchaseHistory(filter: PurchaseHistoryFilter = {}): Promise<PurchaseHistory> {
    const query = new URLSearchParams();
    for (const key of ['branch', 'supplier', 'product', 'status'] as const) if (filter[key]) query.set(key, filter[key]);
    if (filter.limit !== undefined) query.set('limit', String(filter.limit));
    const suffix = query.size > 0 ? `?${query}` : '';
    const value = await this.request(`/purchases/orders${suffix}`);
    if (!record(value) || !instant(value.readAt) || !record(value.summary) ||
        !['orders', 'drafts', 'pending', 'received', 'cancelled'].every(k => count((value.summary as Record<string, unknown>)[k]))) {
      throw invalid();
    }
    return { readAt: value.readAt as string, branch: value.branch === null ? null : branchDto(value.branch),
      summary: value.summary as unknown as PurchaseSummary, items: listDto(value.items, purchaseDto) };
  }
}
