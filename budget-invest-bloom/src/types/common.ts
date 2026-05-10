export interface ApiResponse<T> {
  id: string;
  status: number;
  message: string;
  timestamp: string;
  body: T;
}
