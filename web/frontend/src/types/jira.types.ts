export interface JiraUser {
  accountId: string;
  displayName: string;
}
export interface JiraIssue {
  key: string;
  fields: {
    summary: string;
    status: { id: string; name: string };
    assignee: JiraUser | null;
  };
}
export interface JiraSearch {
  issues: JiraIssue[];
  nextPageToken?: string;
  isLast?: boolean;
}
export interface JiraNamed {
  id: string;
  name: string;
  key?: string;
}
export interface JiraValues {
  values: JiraNamed[];
  isLast?: boolean;
  total?: number;
}
export interface JiraTransition {
  id: string;
  name: string;
  to: JiraNamed;
}
