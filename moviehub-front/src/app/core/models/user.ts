export type Role = 'USER' | 'ADMIN';

/** Mirrors com.moviehub.user.dto.UserResponse. */
export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  avatarUrl: string | null;
  role: Role;
  createdAt: string;
}
