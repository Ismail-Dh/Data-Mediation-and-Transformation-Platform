export interface User {
  id: number;
  username: string;
  email:string;
  role: 'ADMIN' | 'DEVELOPER';
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  role: 'ADMIN' | 'DEVELOPER';
}
export interface UpdateUserRequest {
  username?: string;
  email?: string;
  password?: string;
  role?: 'ADMIN' | 'DEVELOPER';
}