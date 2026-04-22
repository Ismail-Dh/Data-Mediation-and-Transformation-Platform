export interface User {
  id: number;
  username: string;
  role: 'ADMIN' | 'DEVELOPER';
}

export interface CreateUserRequest {
  username: string;
  password: string;
  role: 'ADMIN' | 'DEVELOPER';
}
export interface UpdateUserRequest {
  username?: string;
  password?: string;
  role?: 'ADMIN' | 'DEVELOPER';
}