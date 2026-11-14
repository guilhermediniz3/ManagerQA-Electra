export interface UserAccount {
  id: number;
  name: string;
  email: string;
  active: boolean;
  password?: string;
}
