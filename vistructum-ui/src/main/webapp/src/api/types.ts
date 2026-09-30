export type Source = "mask" | "fullscan";
export type Verdict = "CONFIRMED" | "FALSE_ALARM";
export type FindingState = "open" | "reviewed" | "confirmed" | "false_alarm" | "any";

export interface Me {
  player: string;
  name: string;
  expiresAt: string;
  canShare: boolean;
  blockLog: boolean;
  canRollback: boolean;
  punishments: string | null;
}

export interface InferenceModel {
  kind: string;
  version: string;
}

export type ScanCause = "MANUAL" | "DAILY";

export type ScanState = "QUEUED" | "RUNNING" | "DONE" | "CANCELLED" | "FAILED";

export interface ScanJob {
  id: number;
  world: string;
  cause: ScanCause;
  status: ScanState;
  doneTiles: number;
  totalTiles: number;
  startedAt: string;
  findings: number;
  failures: number;
}

export interface Status {
  trackedChanges: number;
  openFindings: number;
  inference: { mode: "LOCAL" | "REMOTE"; available: boolean; models: InferenceModel[]; detail: string | null };
  scans: ScanJob[];
  recordingEnabled: boolean;
  textures: TextureStatus;
}

export interface TextureStatus {
  enabled: boolean;
  available: boolean;
  version: string | null;
}

export interface AssetStatus {
  available: boolean;
  version: string | null;
  downloading: boolean;
}

export interface AssetsDocument {
  version: string;
  blockstates: Record<string, unknown>;
  models: Record<string, unknown>;
  items: Record<string, unknown>;
  textures: string[];
  animated: Record<string, unknown>;
}

export interface Box {
  minX: number;
  minY: number;
  minZ: number;
  maxX: number;
  maxY: number;
  maxZ: number;
}

export interface PlayerRef {
  uuid: string;
  name: string | null;
}

export interface Review {
  verdict: Verdict;
  reviewer: string;
  reviewedAt: string;
}

export interface FindingSummary {
  id: number;
  source: Source;
  world: string;
  box: Box;
  score: number;
  votes: number;
  detail: string;
  modelVersion: string;
  createdAt: string;
  players: PlayerRef[];
  review: Review | null;
  hasEvidence: boolean;
  hasTerrain: boolean;
  sharedSince: string | null;
  shareUrl: string | null;
  rolledBackAt: string | null;
  rolledBackBy: string | null;
}

export interface FindingDetail extends FindingSummary {
  teleport: string;
}

export interface FindingPage {
  items: FindingSummary[];
  total: number;
  page: number;
  pageSize: number;
}

export interface Scene {
  source: Source;
  width: number;
  height: number;
  window: { top: number; left: number; bottom: number; right: number };
  palette: string[];
  blocks: number[];
  heights: number[];
  luminance: number[];
}

export interface Heatmap {
  width: number;
  height: number;
  values: number[];
}

export interface BlockVolume {
  minX: number;
  minY: number;
  minZ: number;
  sizeX: number;
  sizeY: number;
  sizeZ: number;
  palette: string[];
  cells: number[];
}

export interface Terrain {
  blocks: BlockVolume;
  sceneOrigin: { x: number; z: number } | null;
}

export type BlockAction = "PLACE" | "BREAK";

export interface BlockChange {
  t: number;
  player: string;
  playerName: string;
  action: BlockAction;
  x: number;
  y: number;
  z: number;
  blockData: string;
}

export interface MotionFrame {
  t: number;
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
  flags: number;
  mainHand: string;
}

export interface Recording {
  player: string;
  playerName: string;
  frames: MotionFrame[];
}

export interface Evidence {
  findingId: number;
  before: BlockVolume;
  changes: BlockChange[];
  recordings: Recording[];
}

export interface RollbackResult {
  restored: number;
  skipped: number;
}

export interface ShareResult {
  url: string;
  sharedSince: string;
}

export interface StatsDay {
  day: string;
  source: Source;
  created: number;
  confirmed: number;
  falseAlarms: number;
}

export interface Stats {
  days: StatsDay[];
  reviewers: { reviewer: string; confirmed: number; falseAlarms: number }[];
  open: number;
  oldestOpen: string | null;
  precision: { source: Source; confirmed: number; falseAlarms: number }[];
}

export type ActivityKind = "CONFIRMED" | "FALSE_ALARM" | "SHARED" | "UNSHARED" | "ATTRIBUTED" | "ROLLED_BACK";

export interface ActivityItem {
  at: string;
  actor: string;
  kind: ActivityKind;
  findingId: number;
}

export interface ActivityPage {
  items: ActivityItem[];
}

export interface PlayerInfo {
  uuid: string;
  name: string | null;
  findings: { total: number; open: number; confirmed: number; falseAlarms: number };
}

export type PunishmentType = "BAN" | "MUTE" | "WARN" | "KICK";

export interface Punishment {
  type: PunishmentType;
  reason: string | null;
  operator: string | null;
  issuedAt: string | null;
  expiresAt: string | null;
  active: boolean;
}

export interface PunishmentHistory {
  source: string;
  items: Punishment[];
}

export type Palette = Record<string, number>;

export interface PublicFinding {
  id: number;
  createdAt: string;
  verdict: Verdict | null;
  players: PlayerRef[];
}

export interface PublicEvidence {
  finding: PublicFinding;
  evidence: Evidence;
}

export interface Paging {
  page: number;
  pageSize: number;
}

export interface FindingFilter {
  state: FindingState;
  source: Source | "";
  world: string;
  player: string;
  since: string;
}
