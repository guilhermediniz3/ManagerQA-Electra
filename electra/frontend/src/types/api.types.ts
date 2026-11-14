export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
export interface ApiError {
  message: string;
}
export interface SelectOption {
  value: string;
  label: string;
}
