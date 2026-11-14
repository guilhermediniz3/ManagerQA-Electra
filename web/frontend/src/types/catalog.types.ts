export interface CatalogItem {
  id: number;
  name: string;
  active: boolean;
  technologyIds?: number[];
}
export type CatalogKind = "developers" | "testers" | "technologies" | "modules";
