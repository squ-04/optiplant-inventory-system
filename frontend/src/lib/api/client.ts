import { isTransferStatus } from '../transfers/statuses.ts';
import { isRoutePriority } from '../logistics/routes.ts';
import type { RoutePriority } from '../logistics/routes.ts';
import type { TransferStatus } from '../transfers/statuses.ts';

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
/** RF-0030: a gross price in COP per commercial unit, as an exact decimal string. */
export interface ListPrice {
  productExternalId: string; sku: string; productName: string; productActive: boolean;
  unitName: string; unitActive: boolean; price: string;
}
/** RF-0030: a global price list; `prices` is returned by the detail query only. */
export interface PriceList { externalId: string; code: string; name: string; active: boolean; prices: ListPrice[] | null }
export interface PriceListInput { code: string; name: string }
export type PurchaseStatus = 'DRAFT' | 'PENDING' | 'RECEIVED' | 'CANCELLED';
/** Commercial conditions of one line (RF-0023), as exact decimal strings in API format. */
export interface PurchaseLineInput { product: string; unit: string; quantity: string; unitPrice: string; discountPercent: string }
export interface PurchaseOrderInput { supplier: string; paymentTermDays: number; lines: PurchaseLineInput[] }
/** A new order may replace a cancelled order of the branch (RF-0022-CA-02); a draft edit never changes it. */
export interface NewPurchaseOrderInput extends PurchaseOrderInput { replacesOrder?: string }
export interface PurchaseOrderReference { externalId: string; code: string }
export interface PurchasePerson { code: string; fullName: string }
export interface PurchaseLine {
  lineNo: number; productExternalId: string; sku: string; productName: string; unit: string; factor: string;
  commercialQuantity: string; baseQuantity: string; baseUnitName: string; unitPrice: string; discountPercent: string;
  /** Line net at 2 decimals for display; `exactNetAmount` keeps every decimal. */
  netAmount: string; exactNetAmount: string;
  /** Net cost per base unit fixed by the receipt (inventory valuation); null before it and always for operators. */
  unitBaseCost: string | null;
}
/** `reason` is the optional reason of a cancellation. */
export interface PurchaseEvent {
  fromStatus: PurchaseStatus | null; toStatus: PurchaseStatus; occurredAt: string; actor: PurchasePerson; reason: string | null;
}
export interface PurchaseOrder {
  externalId: string; code: string; status: PurchaseStatus; branch: Branch;
  /** `name` is the historical supplier name kept by the order (RF-0021-CA-03). */
  supplier: { externalId: string; code: string; name: string };
  paymentTermDays: number; createdAt: string; createdBy: PurchasePerson;
  receivedAt: string | null; receivedBy: PurchasePerson | null; dueDate: string | null;
  receiptOperationExternalId: string | null;
  /** RF-0022-CA-02: the cancelled order this one replaces, and the order that replaced this one. */
  replacesOrder: PurchaseOrderReference | null; replacedBy: PurchaseOrderReference | null;
  totalNet: string; exactTotalNet: string; lines: PurchaseLine[];
  /** State history, returned by the detail query only. */
  events: PurchaseEvent[] | null;
}
export interface PurchaseSummary { orders: number; drafts: number; pending: number; received: number; cancelled: number }
export interface PurchaseHistory { readAt: string; branch: Branch | null; summary: PurchaseSummary; items: PurchaseOrder[] }
/** RF-0026: recorded valuation of one product in a branch; `value` at 2 decimals, `exactValue` unrounded. */
export interface ValuationItem {
  productExternalId: string; sku: string; productName: string; baseUnitName: string; physicalQuantity: string;
  averageCost: string; value: string; exactValue: string;
}
export interface InventoryValuation { branch: Branch; readAt: string; totalValue: string; exactTotalValue: string; items: ValuationItem[] }
function routeDto(value: unknown): Route {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !isRoutePriority(value.priority) ||
      typeof value.active !== 'boolean') throw invalid();
  branchDto(value.origin);
  branchDto(value.destination);
  return value as unknown as Route;
}
export interface PurchaseHistoryFilter { branch?: string; supplier?: string; product?: string; status?: PurchaseStatus; limit?: number }

export type { TransferStatus };
export interface TransferLineInput { product: string; unit: string; quantity: string }
/** `sourceDiscrepancy` makes the request the resend of a shortage (RF-0039-CA-02). */
export interface TransferInput { origin: string; lines: TransferLineInput[]; sourceDiscrepancy?: string }
/**
 * RF-0033-CA-02: requested and prepared quantities stay apart; the prepared ones are null until the origin prepares.
 * `originAvailableQuantity` (base units) is sent only to the origin branch and administrators.
 */
export interface TransferLine {
  lineNo: number; productExternalId: string; sku: string; productName: string; unit: string; factor: string;
  baseUnitName: string; requestedQuantity: string; requestedBaseQuantity: string; preparedQuantity: string | null;
  preparedBaseQuantity: string | null;
  /** RF-0035-CA-01: the quantity sent, null until dispatched; 0 for a line prepared at zero. */
  dispatchedQuantity: string | null; dispatchedBaseQuantity: string | null; originAvailableQuantity: string | null;
  /** Whether the requested unit and the base unit admit fractions, so preparation applies the server's rules. */
  unitDivisible: boolean; baseDivisible: boolean;
  /** RF-0036/RF-0037: received and missing (dispatched − received) quantities, null until the arrival. */
  receivedQuantity: string | null; receivedBaseQuantity: string | null; missingQuantity: string | null;
  missingBaseQuantity: string | null;
  /** Base quantity still pending in transit for a dispatched line; null before the dispatch. */
  inTransitBaseQuantity: string | null;
}
export type ShortageTreatment = 'RESEND' | 'ADJUSTMENT' | 'CLAIM';
export const SHORTAGE_TREATMENTS: ShortageTreatment[] = ['RESEND', 'ADJUSTMENT', 'CLAIM'];
/** RF-0038/RF-0039: the shortage alert, its treatment and the transit loss registered apart. */
export interface TransferDiscrepancy {
  externalId: string; status: 'OPEN' | 'RESOLVED'; openedAt: string; treatment: ShortageTreatment | null;
  resolvedAt: string | null; resolvedBy: PurchasePerson | null;
  transitLoss: { occurredAt: string; authorizedBy: PurchasePerson; reason: string; evidence: string } | null;
  /** New transfers that reference this shortage; null in listings. */
  resends: { externalId: string; code: string; status: TransferStatus }[] | null;
}
export interface TransferPrepared { lineNo: number; quantity: string; baseQuantity: string }
export interface TransferEvent {
  fromStatus: TransferStatus | null; toStatus: TransferStatus; occurredAt: string; actor: PurchasePerson; reason: string | null;
  preparation: TransferPrepared[] | null;
}
export interface Transfer {
  externalId: string; code: string; status: TransferStatus; origin: Branch; destination: Branch; requestedAt: string;
  requestedBy: PurchasePerson; approval: { occurredAt: string; actor: PurchasePerson } | null;
  /** RF-0035-CA-01: carrier, actual departure, estimated arrival and the route's priority at dispatch; null until then. */
  dispatch: TransferDispatch | null;
  /** RF-0036/RF-0037: the single confirmed arrival; null until received. */
  arrival: { occurredAt: string; actor: PurchasePerson } | null;
  /** RF-0040: estimated versus real delivery time; null until dispatched. */
  timing: DeliveryTiming | null;
  discrepancy: TransferDiscrepancy | null;
  /** The shortage a resend refers to. */
  sourceDiscrepancy: { externalId: string; transferExternalId: string; transferCode: string } | null;
  lines: TransferLine[];
  /** State history; null in listings. */
  events: TransferEvent[] | null;
}
export interface TransferDispatch {
  carrier: string; departedAt: string; estimatedArrivalAt: string; actor: PurchasePerson; routeCode: string;
  routePriority: RoutePriority;
}
/**
 * RF-0040 (H v0.15): exact seconds computed by the server. The real time runs from the actual departure to the single
 * confirmed arrival and is null without one; `deviationSeconds` is the arrival minus the estimate (positive when late).
 * `onTime` (RF-0043) is true only for a complete reception not later than the estimate; `overdue` marks a transfer
 * still without arrival whose estimate has passed. `consistent` is false when the recorded arrival precedes the departure
 * (data corrected outside the application): its negative duration is shown as an anomaly, never rejected, so one such
 * record cannot hide the whole listing.
 */
export interface DeliveryTiming {
  estimatedSeconds: number; actualSeconds: number | null; deviationSeconds: number | null;
  delivery: 'COMPLETE' | 'DISCREPANCY' | null; onTime: boolean | null; overdue: boolean; consistent: boolean;
}
/**
 * RF-0043: `compliancePercent` is complete punctual arrivals / confirmed arrivals with two decimals; null means
 * «No calculable» (no arrivals in the period).
 */
export interface ComplianceMetrics {
  arrivals: number; onTimeComplete: number; lateComplete: number; discrepancy: number;
  /** Complete receptions whose recorded arrival precedes the departure: in the denominator, never compliant. */
  inconsistentTiming: number; compliancePercent: string | null;
}
export interface ComplianceReport {
  timeZone: string; from: string; to: string; generatedAt: string; branch: Branch | null; summary: ComplianceMetrics;
  branches: { branch: Branch; asOrigin: ComplianceMetrics; asDestination: ComplianceMetrics }[];
  routes: { externalId: string; code: string; origin: Branch; destination: Branch; priority: RoutePriority; active: boolean;
    metrics: ComplianceMetrics }[];
  /** Still in transit with the estimate of the period already passed; never part of the KPI. */
  overdueInTransit: { externalId: string; code: string; origin: Branch; destination: Branch; routeCode: string | null;
    carrier: string; departedAt: string; estimatedArrivalAt: string }[];
}
export interface ComplianceFilter { from: string; to: string; branch?: string }
/** RF-0044: quantities of one product in its base unit; products are never added together. */
export interface ProductSales {
  productExternalId: string; sku: string; name: string; baseUnitName: string; soldQuantity: string; returnedQuantity: string;
}
/**
 * RF-0044: one month of sales of the branch. Without `dataAvailable` (before the branch was registered) the counts are
 * null, which is not a zero. `partialHistory` marks the month the branch was registered; `inProgress` the current one.
 */
export interface SalesMonth {
  month: string; from: string; to: string; inProgress: boolean; dataAvailable: boolean; partialHistory: boolean;
  salesCount: number | null; netAmount: string | null; returnsCount: number | null; products: ProductSales[];
}
export interface SalesComparison { timeZone: string; generatedAt: string; branch: Branch; recordedFrom: string; months: SalesMonth[] }
export type RotationStatus = 'CALCULATED' | 'INSUFFICIENT_HISTORY' | 'ZERO_AVERAGE' | 'INCONSISTENT_HISTORY';
/** RF-0045: `rotation` is null («No calculable») unless CALCULATED; `averageQuantity` is null without enough or consistent history. */
export interface RotationItem {
  productExternalId: string; sku: string; name: string; baseUnitName: string; historyStartedAt: string;
  soldQuantity: string; averageQuantity: string | null; rotation: string | null; status: RotationStatus;
}
export interface RotationReport {
  timeZone: string; from: string; to: string; measuredUntil: string; generatedAt: string; branch: Branch; items: RotationItem[];
}
export interface RotationFilter { from: string; to: string; branch?: string }
export interface DemandItem { productExternalId: string; sku: string; name: string; soldQuantity: string }
/** RF-0046: one ranking per base unit, because pieces and kilos are not comparable. */
export interface DemandRanking { baseUnitName: string; productsWithSales: number; high: DemandItem[]; low: DemandItem[] }
/** RF-0046: without `dataAvailable` the month ended before the branch was registered: empty lists, not «no sales». */
export interface DemandReport {
  timeZone: string; month: string; from: string; to: string; inProgress: boolean; dataAvailable: boolean; partialHistory: boolean;
  generatedAt: string; branch: Branch; rankings: DemandRanking[];
  withoutSales: { productExternalId: string; sku: string; name: string; baseUnitName: string }[];
}
export interface DemandFilter { month?: string; branch?: string }
/** RF-0047: each quantity is null in the states where it does not apply, so none of them adds to another. */
export interface TransferImpactLine {
  productExternalId: string; sku: string; name: string; baseUnitName: string; forecastQuantity: string | null;
  inTransitQuantity: string | null; receivedQuantity: string | null; pendingShortageQuantity: string | null; lostQuantity: string | null;
}
export interface TransferImpactItem {
  externalId: string; code: string; status: TransferStatus; role: 'ORIGIN' | 'DESTINATION'; counterpart: Branch; requestedAt: string;
  estimatedArrivalAt: string | null; alertStatus: 'OPEN' | 'RESOLVED' | null; treatment: ShortageTreatment | null; lines: TransferImpactLine[];
}
export interface TransferImpact { generatedAt: string; branch: Branch; items: TransferImpactItem[] }
/** RF-0048: the operable combinations whose available stock is lower than or equal to their current minimum. */
export interface RestockItem {
  productExternalId: string; sku: string; name: string; baseUnitName: string; availableQuantity: string; minimumQuantity: string;
}
export interface RestockIndicators { generatedAt: string; branch: Branch; items: RestockItem[] }
/** RF-0049: without `dataAvailable` every figure is null, which is not a zero; a percent is null («No calculable») without arrivals. */
export interface BranchPerformance {
  branch: Branch; dataAvailable: boolean; partialHistory: boolean; salesCount: number | null; netAmount: string | null;
  asOrigin: ComplianceMetrics | null; asDestination: ComplianceMetrics | null;
}
export interface BranchComparison {
  timeZone: string; month: string; from: string; to: string; inProgress: boolean; generatedAt: string; branches: BranchPerformance[];
}
export interface TransferDispatchInput { carrier: string; departedAt: string; estimatedArrivalAt: string }
export type { RoutePriority };
/** RF-0041: a directed route with its manual priority. */
export interface Route { externalId: string; code: string; origin: Branch; destination: Branch; priority: RoutePriority; active: boolean }
export interface RouteInput { code: string; origin: string; destination: string; priority: RoutePriority }
export interface RouteUpdate { code: string; priority: RoutePriority; active: boolean }
/** `total` counts every transfer matching the filters; `items` holds the newest ones up to the limit. */
export interface TransferHistory { readAt: string; branch: Branch | null; total: number; items: Transfer[] }
export interface TransferHistoryFilter {
  branch?: string; direction?: 'INCOMING' | 'OUTGOING'; status?: TransferStatus;
  /** RF-0038: only transfers with a shortage alert in that state, newest alert first. */
  discrepancy?: 'OPEN' | 'RESOLVED' | 'ALL'; limit?: number;
}
export interface TransferReceptionLine { lineNo: number; quantity: string }
export interface TransitLossInput { reason: string; evidence: string }

/** RF-0027/RF-0029: a sale line; the gross price comes from the chosen list, only the line discount is captured. */
export interface SaleLineInput { product: string; unit: string; quantity: string; discountPercent: string }
export interface SaleInput { priceList: string; lines: SaleLineInput[] }
/**
 * RF-0031: a line of a confirmed sale record with the snapshots kept at confirmation. `unitPrice` is the gross list
 * price per commercial unit and `netUnitPrice` the exact price after the line discount; `movementExternalId` is the
 * inventory exit a return references.
 */
export interface SaleLine {
  lineNo: number; productExternalId: string; sku: string; productName: string; unit: string; factor: string;
  commercialQuantity: string; baseQuantity: string; baseUnitName: string; priceListExternalId: string; priceListName: string;
  unitPrice: string; discountPercent: string; netUnitPrice: string; netAmount: string; exactNetAmount: string;
  movementExternalId: string;
}
/** RF-0031: the immutable record of a confirmed sale. Amounts at 2 decimals; `exactTotalNet` keeps every decimal. */
export interface Sale {
  externalId: string; code: string; branch: Branch; confirmedAt: string; confirmedBy: PurchasePerson;
  operationExternalId: string; grossTotal: string; discountTotal: string; totalNet: string; exactTotalNet: string;
  lines: SaleLine[];
}
export interface SaleHistory { readAt: string; branch: Branch | null; total: number; items: Sale[] }

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
    // A server failure without detail only carries a generic English title; explain what to do instead.
    super(problem.detail ?? (status >= 500
      ? `El servidor no pudo completar la solicitud (HTTP ${status}). Vuelva a intentarlo; si persiste, avise al administrador.`
      : problem.title ?? `Error HTTP ${status}.`));
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
function listPriceDto(value: unknown): ListPrice {
  if (!record(value) || !uuid(value.productExternalId) || !['sku', 'productName', 'unitName'].every(k => text(value[k])) ||
      typeof value.productActive !== 'boolean' || typeof value.unitActive !== 'boolean' || !decimal(value.price)) throw invalid();
  return value as unknown as ListPrice;
}
function priceListDto(value: unknown): PriceList {
  supplierDto(value);
  if (!record(value) || !(value.prices === null || Array.isArray(value.prices))) throw invalid();
  return { ...(value as unknown as PriceList), prices: value.prices === null ? null : listDto(value.prices, listPriceDto) };
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
      !PURCHASE_STATUSES.includes(String(value.toStatus)) || !instant(value.occurredAt) ||
      !(value.reason === null || text(value.reason))) throw invalid();
  personDto(value.actor);
  return value as unknown as PurchaseEvent;
}
const orderReference = (value: unknown) => value === null || (record(value) && uuid(value.externalId) && text(value.code));
function valuationItemDto(value: unknown): ValuationItem {
  if (!record(value) || !uuid(value.productExternalId) || !['sku', 'productName', 'baseUnitName'].every(k => text(value[k])) ||
      !['physicalQuantity', 'averageCost', 'value', 'exactValue'].every(k => decimal(value[k]))) throw invalid();
  return value as unknown as ValuationItem;
}
const optionalDecimal = (value: unknown) => value === null || decimal(value);
function transferLineDto(value: unknown): TransferLine {
  if (!record(value) || !count(value.lineNo) || !uuid(value.productExternalId) ||
      !['sku', 'productName', 'unit', 'baseUnitName'].every(k => text(value[k])) ||
      !['factor', 'requestedQuantity', 'requestedBaseQuantity'].every(k => decimal(value[k])) ||
      !['preparedQuantity', 'preparedBaseQuantity', 'dispatchedQuantity', 'dispatchedBaseQuantity', 'originAvailableQuantity',
        'receivedQuantity', 'receivedBaseQuantity', 'missingQuantity', 'missingBaseQuantity', 'inTransitBaseQuantity']
        .every(k => optionalDecimal(value[k])) ||
      (value.receivedQuantity === null) !== (value.missingQuantity === null) ||
      typeof value.unitDivisible !== 'boolean' || typeof value.baseDivisible !== 'boolean' ||
      (value.preparedQuantity === null) !== (value.preparedBaseQuantity === null) ||
      (value.dispatchedQuantity === null) !== (value.dispatchedBaseQuantity === null)) throw invalid();
  return value as unknown as TransferLine;
}
function transferEventDto(value: unknown): TransferEvent {
  if (!record(value) || !(value.fromStatus === null || isTransferStatus(value.fromStatus)) ||
      !isTransferStatus(value.toStatus) || !instant(value.occurredAt) ||
      !(value.reason === null || text(value.reason))) throw invalid();
  personDto(value.actor);
  const preparation = value.preparation === null ? null : listDto(value.preparation, item => {
    if (!record(item) || !count(item.lineNo) || !decimal(item.quantity) || !decimal(item.baseQuantity)) throw invalid();
    return item as unknown as TransferPrepared;
  });
  return { ...(value as unknown as TransferEvent), preparation };
}
function discrepancyDto(value: unknown): TransferDiscrepancy {
  if (!record(value) || !uuid(value.externalId) || (value.status !== 'OPEN' && value.status !== 'RESOLVED')
      || !instant(value.openedAt)) throw invalid();
  // The alert is open with no resolution, or resolved with its treatment, author and date (V3 CHECK).
  const resolved = value.status === 'RESOLVED';
  if (resolved !== SHORTAGE_TREATMENTS.includes(value.treatment as ShortageTreatment) || (!resolved && value.treatment !== null)
      || (resolved ? !instant(value.resolvedAt) : value.resolvedAt !== null) || (!resolved && value.resolvedBy !== null)) throw invalid();
  if (resolved) personDto(value.resolvedBy);
  const loss = value.transitLoss;
  if (loss !== null && (!record(loss) || !instant(loss.occurredAt) || !text(loss.reason) || !text(loss.evidence))) throw invalid();
  if (loss !== null) personDto((loss as Record<string, unknown>).authorizedBy);
  const resends = value.resends === null ? null : listDto(value.resends, item => {
    if (!record(item) || !uuid(item.externalId) || !text(item.code) || !isTransferStatus(item.status)) throw invalid();
    return item as unknown as { externalId: string; code: string; status: TransferStatus };
  });
  return { ...(value as unknown as TransferDiscrepancy), resends };
}
const seconds = (value: unknown) => typeof value === 'number' && Number.isInteger(value);
/** The real time, the delivery kind and the punctuality exist together, only once something has arrived. */
function timingDto(value: unknown): boolean {
  if (!record(value) || !count(value.estimatedSeconds) || typeof value.overdue !== 'boolean'
      || typeof value.consistent !== 'boolean') return false;
  if (value.actualSeconds === null) {
    return value.deviationSeconds === null && value.delivery === null && value.onTime === null && value.consistent;
  }
  // A negative real duration is accepted and flagged: it is exactly the inconsistent case.
  return seconds(value.actualSeconds) && seconds(value.deviationSeconds) && typeof value.onTime === 'boolean'
    && (value.delivery === 'COMPLETE' || value.delivery === 'DISCREPANCY') && !value.overdue
    && value.consistent === ((value.actualSeconds as number) >= 0)
    && (!value.onTime || (value.delivery === 'COMPLETE' && value.consistent));
}
const percent = (value: unknown) => value === null || (typeof value === 'string' && /^\d{1,3}\.\d{2}$/.test(value));
function complianceMetricsDto(value: unknown): ComplianceMetrics {
  if (!record(value) || !['arrivals', 'onTimeComplete', 'lateComplete', 'discrepancy', 'inconsistentTiming'].every(k => count(value[k]))
      || !percent(value.compliancePercent)) throw invalid();
  const metrics = value as unknown as ComplianceMetrics;
  // «No calculable» exactly when nothing arrived; the four outcomes add up to the arrivals.
  if ((metrics.arrivals === 0) !== (metrics.compliancePercent === null) || metrics.onTimeComplete + metrics.lateComplete
      + metrics.discrepancy + metrics.inconsistentTiming !== metrics.arrivals) throw invalid();
  return metrics;
}
function complianceDto(value: unknown): ComplianceReport {
  if (!record(value) || !text(value.timeZone) || !day(value.from) || !day(value.to) || !instant(value.generatedAt)) throw invalid();
  if (value.branch !== null) branchDto(value.branch);
  complianceMetricsDto(value.summary);
  const branches = listDto(value.branches, item => {
    if (!record(item)) throw invalid();
    branchDto(item.branch); complianceMetricsDto(item.asOrigin); complianceMetricsDto(item.asDestination);
    return item as unknown as ComplianceReport['branches'][number];
  });
  const routes = listDto(value.routes, item => {
    if (!record(item) || !uuid(item.externalId) || !text(item.code) || !isRoutePriority(item.priority)
        || typeof item.active !== 'boolean') throw invalid();
    branchDto(item.origin); branchDto(item.destination); complianceMetricsDto(item.metrics);
    return item as unknown as ComplianceReport['routes'][number];
  });
  const overdueInTransit = listDto(value.overdueInTransit, item => {
    if (!record(item) || !uuid(item.externalId) || !text(item.code) || !text(item.carrier) || !instant(item.departedAt)
        || !instant(item.estimatedArrivalAt) || !(item.routeCode === null || text(item.routeCode))) throw invalid();
    branchDto(item.origin); branchDto(item.destination);
    return item as unknown as ComplianceReport['overdueInTransit'][number];
  });
  return { ...(value as unknown as ComplianceReport), branches, routes, overdueInTransit };
}
function salesComparisonDto(value: unknown): SalesComparison {
  if (!record(value) || !text(value.timeZone) || !instant(value.generatedAt) || !day(value.recordedFrom)) throw invalid();
  branchDto(value.branch);
  const months = listDto(value.months, item => {
    if (!record(item) || !/^\d{4}-\d{2}$/.test(String(item.month)) || !day(item.from) || !day(item.to)
        || typeof item.inProgress !== 'boolean' || typeof item.dataAvailable !== 'boolean'
        || typeof item.partialHistory !== 'boolean') throw invalid();
    const known = item.dataAvailable;
    if (known ? !count(item.salesCount) || !decimal(item.netAmount) || !count(item.returnsCount)
      : item.salesCount !== null || item.netAmount !== null || item.returnsCount !== null) throw invalid();
    const products = listDto(item.products, product => {
      if (!record(product) || !uuid(product.productExternalId) || !text(product.sku) || !text(product.name)
          || !text(product.baseUnitName) || !decimal(product.soldQuantity) || !decimal(product.returnedQuantity)) throw invalid();
      return product as unknown as ProductSales;
    });
    return { ...(item as unknown as SalesMonth), products };
  });
  return { ...(value as unknown as SalesComparison), months };
}
const ROTATION_STATUSES: readonly string[] = ['CALCULATED', 'INSUFFICIENT_HISTORY', 'ZERO_AVERAGE', 'INCONSISTENT_HISTORY'];
function rotationDto(value: unknown): RotationReport {
  if (!record(value) || !text(value.timeZone) || !day(value.from) || !day(value.to) || !instant(value.measuredUntil)
      || !instant(value.generatedAt)) throw invalid();
  branchDto(value.branch);
  const items = listDto(value.items, item => {
    if (!record(item) || !uuid(item.productExternalId) || !text(item.sku) || !text(item.name) || !text(item.baseUnitName)
        || !instant(item.historyStartedAt) || !decimal(item.soldQuantity) || !ROTATION_STATUSES.includes(String(item.status))
        || !(item.averageQuantity === null || decimal(item.averageQuantity))
        || (item.status === 'CALCULATED' ? !decimal(item.rotation) : item.rotation !== null)) throw invalid();
    return item as unknown as RotationItem;
  });
  return { ...(value as unknown as RotationReport), items };
}
const month = (value: unknown) => typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
function demandDto(value: unknown): DemandReport {
  if (!record(value) || !text(value.timeZone) || !month(value.month) || !day(value.from) || !day(value.to)
      || typeof value.inProgress !== 'boolean' || typeof value.dataAvailable !== 'boolean'
      || typeof value.partialHistory !== 'boolean' || !instant(value.generatedAt)) throw invalid();
  branchDto(value.branch);
  const items = (list: unknown) => listDto(list, item => {
    if (!record(item) || !uuid(item.productExternalId) || !text(item.sku) || !text(item.name) || !decimal(item.soldQuantity)) throw invalid();
    return item as unknown as DemandItem;
  });
  const rankings = listDto(value.rankings, item => {
    if (!record(item) || !text(item.baseUnitName) || !count(item.productsWithSales)) throw invalid();
    return { ...(item as unknown as DemandRanking), high: items(item.high), low: items(item.low) };
  });
  const withoutSales = listDto(value.withoutSales, item => {
    if (!record(item) || !uuid(item.productExternalId) || !text(item.sku) || !text(item.name) || !text(item.baseUnitName)) throw invalid();
    return item as unknown as DemandReport['withoutSales'][number];
  });
  return { ...(value as unknown as DemandReport), rankings, withoutSales };
}
function transferImpactDto(value: unknown): TransferImpact {
  if (!record(value) || !instant(value.generatedAt)) throw invalid();
  branchDto(value.branch);
  const items = listDto(value.items, item => {
    if (!record(item) || !uuid(item.externalId) || !text(item.code) || !isTransferStatus(item.status)
        || (item.role !== 'ORIGIN' && item.role !== 'DESTINATION') || !instant(item.requestedAt)
        || !(item.estimatedArrivalAt === null || instant(item.estimatedArrivalAt))
        || !(item.alertStatus === null || item.alertStatus === 'OPEN' || item.alertStatus === 'RESOLVED')
        || !(item.treatment === null || ['RESEND', 'ADJUSTMENT', 'CLAIM'].includes(String(item.treatment)))) throw invalid();
    branchDto(item.counterpart);
    const lines = listDto(item.lines, line => {
      if (!record(line) || !uuid(line.productExternalId) || !text(line.sku) || !text(line.name) || !text(line.baseUnitName)
          || !optionalDecimal(line.forecastQuantity) || !optionalDecimal(line.inTransitQuantity) || !optionalDecimal(line.receivedQuantity)
          || !optionalDecimal(line.pendingShortageQuantity) || !optionalDecimal(line.lostQuantity)) throw invalid();
      return line as unknown as TransferImpactLine;
    });
    return { ...(item as unknown as TransferImpactItem), lines };
  });
  return { ...(value as unknown as TransferImpact), items };
}
function restockDto(value: unknown): RestockIndicators {
  if (!record(value) || !instant(value.generatedAt)) throw invalid();
  branchDto(value.branch);
  const items = listDto(value.items, item => {
    if (!record(item) || !uuid(item.productExternalId) || !text(item.sku) || !text(item.name) || !text(item.baseUnitName)
        || !decimal(item.availableQuantity) || !decimal(item.minimumQuantity)) throw invalid();
    return item as unknown as RestockItem;
  });
  return { ...(value as unknown as RestockIndicators), items };
}
function branchComparisonDto(value: unknown): BranchComparison {
  if (!record(value) || !text(value.timeZone) || !month(value.month) || !day(value.from) || !day(value.to)
      || typeof value.inProgress !== 'boolean' || !instant(value.generatedAt)) throw invalid();
  const branches = listDto(value.branches, item => {
    if (!record(item) || typeof item.dataAvailable !== 'boolean' || typeof item.partialHistory !== 'boolean') throw invalid();
    branchDto(item.branch);
    const known = item.dataAvailable;
    if (known ? !count(item.salesCount) || !decimal(item.netAmount) || item.asOrigin === null || item.asDestination === null
      : item.salesCount !== null || item.netAmount !== null || item.asOrigin !== null || item.asDestination !== null) throw invalid();
    return { ...(item as unknown as BranchPerformance), asOrigin: known ? complianceMetricsDto(item.asOrigin) : null,
      asDestination: known ? complianceMetricsDto(item.asDestination) : null };
  });
  return { ...(value as unknown as BranchComparison), branches };
}
function transferDto(value: unknown): Transfer {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !isTransferStatus(value.status) ||
      !instant(value.requestedAt)) throw invalid();
  branchDto(value.origin);
  branchDto(value.destination);
  personDto(value.requestedBy);
  if (value.approval !== null && (!record(value.approval) || !instant(value.approval.occurredAt))) throw invalid();
  if (value.approval !== null) personDto((value.approval as Record<string, unknown>).actor);
  if (value.dispatch !== null && (!record(value.dispatch) || !text(value.dispatch.carrier) || !instant(value.dispatch.departedAt)
      || !instant(value.dispatch.estimatedArrivalAt) || !text(value.dispatch.routeCode)
      || !isRoutePriority(value.dispatch.routePriority))) throw invalid();
  if (value.dispatch !== null) personDto((value.dispatch as Record<string, unknown>).actor);
  if (value.arrival !== null && (!record(value.arrival) || !instant(value.arrival.occurredAt))) throw invalid();
  if (value.arrival !== null) personDto((value.arrival as Record<string, unknown>).actor);
  if (value.discrepancy !== null) discrepancyDto(value.discrepancy);
  // A response stored for an idempotent replay before RF-0040 has no timing: it reads as not computed.
  const timing = value.timing ?? null;
  if (timing !== null && (value.dispatch === null || !timingDto(timing))) throw invalid();
  if (value.sourceDiscrepancy !== null && (!record(value.sourceDiscrepancy) || !uuid(value.sourceDiscrepancy.externalId)
      || !uuid(value.sourceDiscrepancy.transferExternalId) || !text(value.sourceDiscrepancy.transferCode))) throw invalid();
  return { ...(value as unknown as Transfer), timing: timing as DeliveryTiming | null, lines: listDto(value.lines, transferLineDto),
    events: value.events === null ? null : listDto(value.events, transferEventDto) };
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
  if (!orderReference(value.replacesOrder) || !orderReference(value.replacedBy)) throw invalid();
  branchDto(value.branch);
  personDto(value.createdBy);
  if (received) personDto(value.receivedBy);
  return { ...(value as unknown as PurchaseOrder), lines: listDto(value.lines, purchaseLineDto),
    events: value.events === null ? null : listDto(value.events, purchaseEventDto) };
}

function saleLineDto(value: unknown): SaleLine {
  if (!record(value) || !count(value.lineNo) || !uuid(value.productExternalId) || !uuid(value.priceListExternalId) ||
      !uuid(value.movementExternalId) ||
      !['sku', 'productName', 'unit', 'baseUnitName', 'priceListName'].every(k => text(value[k])) ||
      !['factor', 'commercialQuantity', 'baseQuantity', 'unitPrice', 'discountPercent', 'netUnitPrice', 'netAmount',
        'exactNetAmount'].every(k => decimal(value[k]))) throw invalid();
  return value as unknown as SaleLine;
}
function saleDto(value: unknown): Sale {
  if (!record(value) || !uuid(value.externalId) || !text(value.code) || !instant(value.confirmedAt) ||
      !uuid(value.operationExternalId) ||
      !['grossTotal', 'discountTotal', 'totalNet', 'exactTotalNet'].every(k => decimal(value[k]))) throw invalid();
  branchDto(value.branch);
  personDto(value.confirmedBy);
  const lines = listDto(value.lines, saleLineDto);
  if (lines.length === 0) throw invalid();
  return { ...(value as unknown as Sale), lines };
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
  /** RF-0030: administrators see every list; local profiles only the active ones they may use in sales. */
  async priceLists(): Promise<PriceList[]> {
    return listDto(await this.request('/catalog/price-lists'), priceListDto);
  }
  async priceList(id: string): Promise<PriceList> {
    return priceListDto(await this.request(`/catalog/price-lists/${encodeURIComponent(id)}`));
  }
  async createPriceList(data: PriceListInput): Promise<PriceList> {
    return priceListDto(await this.request('/catalog/price-lists', 'POST', data));
  }
  async updatePriceList(id: string, data: PriceListInput & { active: boolean }): Promise<PriceList> {
    return priceListDto(await this.request(`/catalog/price-lists/${encodeURIComponent(id)}/update`, 'POST', data));
  }
  /** Defines or replaces the price of one product unit; `price` is an exact decimal string with a dot. */
  async setListPrice(id: string, data: { product: string; unit: string; price: string }): Promise<PriceList> {
    return priceListDto(await this.request(`/catalog/price-lists/${encodeURIComponent(id)}/prices`, 'POST', data));
  }
  /**
   * RF-0021/RF-0023: creates a draft order in the operator's branch. Reuse the same key to retry a request whose outcome
   * is unknown; the server returns the original order without creating another.
   */
  async createPurchase(input: NewPurchaseOrderInput, idempotencyKey: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request('/purchases/orders', 'POST', input, undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0022: rewrites a draft; a pending or received order answers 409 `PURCHASE_NOT_EDITABLE`. */
  async updatePurchase(id: string, input: PurchaseOrderInput, idempotencyKey: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request(`/purchases/orders/${encodeURIComponent(id)}/update`, 'POST', input, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0022: cancels a draft or pending order before its receipt, with an optional reason; stock never changes. */
  async cancelPurchase(id: string, reason: string | null, idempotencyKey: string): Promise<PurchaseOrder> {
    return purchaseDto(await this.request(`/purchases/orders/${encodeURIComponent(id)}/cancel`, 'POST',
      reason ? { reason } : {}, undefined, { 'Idempotency-Key': idempotencyKey }));
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
  /** RF-0026: current weighted-average valuation of a branch; managers read their own, administrators any. */
  async inventoryValuation(branch: string): Promise<InventoryValuation> {
    const value = await this.request(`/inventory/branches/${encodeURIComponent(branch)}/valuation`);
    if (!record(value) || !instant(value.readAt) || !decimal(value.totalValue) || !decimal(value.exactTotalValue)) throw invalid();
    return { branch: branchDto(value.branch), readAt: value.readAt as string, totalValue: value.totalValue as string,
      exactTotalValue: value.exactTotalValue as string, items: listDto(value.items, valuationItemDto) };
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
  /**
   * RF-0027–RF-0029: validates stock and confirms a sale of the operator's branch with the chosen price list. Reuse the
   * same key to retry a request whose outcome is unknown: the server returns the original record without selling twice.
   */
  async confirmSale(input: SaleInput, idempotencyKey: string): Promise<Sale> {
    return saleDto(await this.request('/sales', 'POST', input, undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0031: confirmed sale records of a branch or, for administrators without a branch, of the network. */
  async sales(filter: { branch?: string; limit?: number } = {}): Promise<SaleHistory> {
    const query = new URLSearchParams();
    if (filter.branch) query.set('branch', filter.branch);
    if (filter.limit !== undefined) query.set('limit', String(filter.limit));
    const suffix = query.size > 0 ? `?${query}` : '';
    const value = await this.request(`/sales${suffix}`);
    if (!record(value) || !instant(value.readAt) || !count(value.total)) throw invalid();
    return { readAt: value.readAt as string, branch: value.branch === null ? null : branchDto(value.branch),
      total: value.total as number, items: listDto(value.items, saleDto) };
  }
  async sale(id: string): Promise<Sale> {
    return saleDto(await this.request(`/sales/${encodeURIComponent(id)}`));
  }

  /** RF-0032: active branches, other than the operator's, that may be chosen as origin of a transfer request. */
  async transferOrigins(): Promise<Branch[]> {
    return listDto(await this.request('/transfers/origins'), branchDto);
  }
  /**
   * RF-0032: the destination operator requests products from another branch; nothing is reserved. Reuse the same key to
   * retry a request whose outcome is unknown: the server returns the original transfer without creating another.
   */
  async requestTransfer(input: TransferInput, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request('/transfers', 'POST', input, undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0033: the origin operator records the quantity to send of every line, at most the requested one. */
  async prepareTransfer(id: string, lines: { lineNo: number; quantity: string }[], idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/prepare`, 'POST', { lines }, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0034-CA-01: the origin manager approves a transfer in preparation. */
  async approveTransfer(id: string, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/approve`, 'POST', {}, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0034-CA-03: the origin manager rejects a transfer in preparation; the reason is mandatory. */
  async rejectTransfer(id: string, reason: string, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/reject`, 'POST', { reason }, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0034-CA-02: cancellation before dispatch; the reason is mandatory once the transfer is approved. */
  async cancelTransfer(id: string, reason: string | null, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/cancel`, 'POST', reason ? { reason } : {},
      undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /**
   * RF-0035: the origin operator dispatches an approved transfer; the same key replays a dispatch whose outcome is
   * unknown without moving stock again.
   */
  async dispatchTransfer(id: string, input: TransferDispatchInput, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/dispatch`, 'POST', input, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /**
   * RF-0036/RF-0037: the destination operator confirms the single arrival with the quantity received per line (0 is
   * valid); the same key replays a confirmation whose outcome is unknown without entering stock again.
   */
  async receiveTransfer(id: string, lines: TransferReceptionLine[], idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/receive`, 'POST', { lines }, undefined,
      { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0038-CA-02: a manager of the origin or destination marks the shortage alert «Resuelto» with its treatment. */
  async resolveShortage(id: string, treatment: ShortageTreatment, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/discrepancy/resolve`, 'POST', { treatment },
      undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0039-CA-02: the origin manager writes off the pending transit of a shortage once, with reason and evidence. */
  async writeOffTransit(id: string, input: TransitLossInput, idempotencyKey: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}/discrepancy/transit-loss`, 'POST', input,
      undefined, { 'Idempotency-Key': idempotencyKey }));
  }
  /** RF-0041: routes in classification order; local profiles receive the active routes of their branch. */
  async routes(priority?: RoutePriority): Promise<Route[]> {
    return listDto(await this.request(`/logistics/routes${priority ? `?priority=${priority}` : ''}`), routeDto);
  }
  async createRoute(input: RouteInput): Promise<Route> {
    return routeDto(await this.request('/logistics/routes', 'POST', input));
  }
  async updateRoute(id: string, input: RouteUpdate): Promise<Route> {
    return routeDto(await this.request(`/logistics/routes/${encodeURIComponent(id)}/update`, 'POST', input));
  }
  /** RF-0043: compliance by branch and route of a period of dates in America/Bogota; managers read their own branch. */
  async complianceReport(filter: ComplianceFilter): Promise<ComplianceReport> {
    const query = new URLSearchParams({ from: filter.from, to: filter.to });
    if (filter.branch) query.set('branch', filter.branch);
    return complianceDto(await this.request(`/logistics/compliance?${query}`));
  }
  /** RF-0044: the branch is required for the administrator; everybody else reads their own. */
  async salesComparison(branch?: string): Promise<SalesComparison> {
    const query = branch ? `?${new URLSearchParams({ branch })}` : '';
    return salesComparisonDto(await this.request(`/dashboard/sales-comparison${query}`));
  }
  async rotation(filter: RotationFilter): Promise<RotationReport> {
    const query = new URLSearchParams({ from: filter.from, to: filter.to });
    if (filter.branch) query.set('branch', filter.branch);
    return rotationDto(await this.request(`/dashboard/rotation?${query}`));
  }
  /** RF-0046: the month in progress by default; the branch is required for the administrator. */
  async demand(filter: DemandFilter): Promise<DemandReport> {
    const query = new URLSearchParams();
    if (filter.month) query.set('month', filter.month);
    if (filter.branch) query.set('branch', filter.branch);
    return demandDto(await this.request(`/dashboard/demand${query.size ? `?${query}` : ''}`));
  }
  /** RF-0047. */
  async transferImpact(branch?: string): Promise<TransferImpact> {
    const query = branch ? `?${new URLSearchParams({ branch })}` : '';
    return transferImpactDto(await this.request(`/dashboard/transfers${query}`));
  }
  /** RF-0048. */
  async restockIndicators(branch?: string): Promise<RestockIndicators> {
    const query = branch ? `?${new URLSearchParams({ branch })}` : '';
    return restockDto(await this.request(`/dashboard/restock${query}`));
  }
  /** RF-0049: exclusive to the general administrator. */
  async branchComparison(period?: string): Promise<BranchComparison> {
    const query = period ? `?${new URLSearchParams({ month: period })}` : '';
    return branchComparisonDto(await this.request(`/dashboard/branch-comparison${query}`));
  }
  async transfer(id: string): Promise<Transfer> {
    return transferDto(await this.request(`/transfers/${encodeURIComponent(id)}`));
  }
  /** Transfers of a branch as origin, destination or both, or of the network for administrators; newest first. */
  async transfers(filter: TransferHistoryFilter = {}, signal?: AbortSignal): Promise<TransferHistory> {
    const query = new URLSearchParams();
    for (const key of ['branch', 'direction', 'status', 'discrepancy'] as const) if (filter[key]) query.set(key, filter[key]);
    if (filter.limit !== undefined) query.set('limit', String(filter.limit));
    const suffix = query.size > 0 ? `?${query}` : '';
    const value = await this.request(`/transfers${suffix}`, 'GET', undefined, signal);
    if (!record(value) || !instant(value.readAt) || !count(value.total)) throw invalid();
    return { readAt: value.readAt as string, branch: value.branch === null ? null : branchDto(value.branch),
      total: value.total as number, items: listDto(value.items, transferDto) };
  }
}
