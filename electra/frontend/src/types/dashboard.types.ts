export interface MetricGroup {
  name: string;
  value: number;
}
export interface ModuleHealth {
  name: string;
  tasks: number;
  returns: number;
  concentration: number;
}
export interface Dashboard {
  total: number;
  blocked: number;
  returned: number;
  awaitingDeploy: number;
  approved: number;
  byDeveloper: MetricGroup[];
  byTester: MetricGroup[];
  byTechnology: MetricGroup[];
  byStatus: MetricGroup[];
  modules: ModuleHealth[];
}
