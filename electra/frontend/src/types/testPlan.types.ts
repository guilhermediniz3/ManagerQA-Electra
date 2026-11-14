export interface TestPlan {
  id: number;
  name: string;
  jira: string;
  observation: string;
  status: string;
  taskStatus?: string;
  data: string;
  deliveryData?: string;
  developerId?: number;
  testerId?: number;
  systemModuleId?: number;
  callNumber?: string;
  matriz?: string;
  userName?: string;
  password?: string;
  created?: boolean;
  created_by?: string;
  testeSuiteId?: number[];
}
