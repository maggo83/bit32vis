export type Width = 32 | 40;
export type Style =
  | "standard"
  | "high-contrast"
  | "monochrome"
  | "black-and-white";
export type Mode = "A|" | "A-" | "A+" | "A/";

export interface Edge {
  readonly startRow: number;
  readonly startColumn: number;
  readonly endRow: number;
  readonly endColumn: number;
}

export interface Dimensions {
  readonly rows: number;
  readonly columns: number;
  readonly edgeCount: number;
  readonly pixelWidth: number;
  readonly pixelHeight: number;
}

export interface VisSpec<Input extends number | bigint> {
  readonly input: Input;
  readonly mixed: Input;
  readonly connections: Uint8Array;
  readonly cells: ReadonlyArray<ReadonlyArray<number>>;
  readonly style: Style;
  readonly preferredMode: Mode;
  readonly actualMode: Mode;
  readonly fallback: boolean;
  readonly luminanceIndex: number;
  readonly swapped: boolean;
  readonly background: string;
  readonly foreground: string;
}

export type VisSpec32 = VisSpec<number>;
export type VisSpec40 = VisSpec<bigint>;

export interface PixelGrid<
  PixelWidth extends number = number,
  PixelHeight extends number = number,
> {
  readonly width: PixelWidth;
  readonly height: PixelHeight;
  readonly pixels: Uint8Array;
  readonly background: string;
  readonly foreground: string;
  readonly style: Style;
}

export type PixelGrid32 = PixelGrid<16, 22>;
export type PixelGrid40 = PixelGrid<22, 22>;

export interface SmoothBlob {
  readonly topRow: number;
  readonly leftColumn: number;
  readonly bottomRow: number;
  readonly rightColumn: number;
}

export const STANDARD: "standard";
export const HIGH_CONTRAST: "high-contrast";
export const MONOCHROME: "monochrome";
export const BLACK_AND_WHITE: "black-and-white";
export const STYLES: readonly Style[];
export const MODES: readonly Mode[];

export const ROWS32: 7;
export const COLUMNS32: 5;
export const EDGE_COUNT32: 58;
export const PIXEL_WIDTH32: 16;
export const PIXEL_HEIGHT32: 22;
export const ROWS40: 7;
export const COLUMNS40: 7;
export const EDGE_COUNT40: 84;
export const PIXEL_WIDTH40: 22;
export const PIXEL_HEIGHT40: 22;
export const EDGES32: readonly Edge[];
export const EDGES40: readonly Edge[];

export function mix32(input: number): number;
export function mix40(input: bigint): bigint;
export function dimensions(width: Width): Dimensions;
export function edges(width: Width): readonly Edge[];
export function freeConnectionCount(width: Width, mode: Mode): number;
export function usableConnectionCount(width: Width, mode: Mode): number;
export function matchesMode(
  width: Width,
  connections: Uint8Array,
  mode: Mode,
): boolean;
export function smoothBlobs(
  width: Width,
  connections: Uint8Array,
): SmoothBlob[];

export function spec32(input: number, style?: Style): VisSpec32;
export function spec40(input: bigint, style?: Style): VisSpec40;
export function pixels32(input: number, style?: Style): PixelGrid32;
export function pixels40(input: bigint, style?: Style): PixelGrid40;
export function parseHex32(value: string): number;
export function parseHex40(value: string): bigint;
export function formatHex32(input: number): string;
export function formatHex40(input: bigint): string;
export function bip380ChecksumInput(checksum: string): bigint;