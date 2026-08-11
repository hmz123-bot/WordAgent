import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
});

// 响应拦截器
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败';
    console.error('API Error:', message);
    return Promise.reject(error);
  }
);

// ========== 通用类型 ==========

export interface ApiResponse<T> {
  success: boolean;
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

// ========== 文档管理 ==========

export interface DocumentAsset {
  documentId: string;
  fileName: string;
  fileSize: number;
  fileHash: string;
  storagePath: string;
  ownerId: string;
  currentVersion: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  workspaceId: string;
}

export interface DocumentVersion {
  versionNumber: number;
  createdAt: string;
  changeSummary: string;
}

export interface ImageResource {
  id: string;
  name: string;
  mimeType: string;
  encoding: 'base64' | 'url';
  data: string;
  width?: number;
  height?: number;
  altText?: string;
  relationshipId?: string;
}

export interface DocumentNode {
  nodeId: string;
  nodeType: string;
  text: string | null;
  children: DocumentNode[];
  styleId: string | null;
  attributes: Record<string, unknown>;
  tableFormat?: TableFormat;
  image?: ImageResource;
}

export interface TableFormat {
  width?: number;
  widthType?: string;
  alignment?: 'LEFT' | 'CENTER' | 'RIGHT';
  rtl?: boolean;
  indent?: number;
  borders?: TableBorder[];
  shading?: TableShading;
  columns?: TableColumn[];
  columnCount?: number;
  cellSpacing?: number;
  cellMarginTop?: number;
  cellMarginBottom?: number;
  cellMarginLeft?: number;
  cellMarginRight?: number;
  rows?: TableRowProperties[];
}

export interface TableBorder {
  side: string;
  style: string;
  size: number;
  color: string;
  space?: string;
  shadow?: boolean;
}

export interface TableShading {
  fill: string;
  pattern?: string;
  patternColor?: string;
}

export interface TableColumn {
  index: number;
  width: number;
  widthType?: string;
}

export interface TableRowProperties {
  rowIndex: number;
  height?: number;
  heightRule?: string;
  headerRow?: boolean;
  cantSplit?: boolean;
  cells: TableCellProperties[];
}

export interface TableCellProperties {
  columnIndex: number;
  colSpan?: number;
  rowSpan?: number;
  hMerge?: boolean;
  vMerge?: boolean;
  verticalAlign?: string;
  textDirection?: string;
  width?: number;
  shading?: TableShading;
  borders?: TableBorder[];
  marginTop?: number;
  marginBottom?: number;
  marginLeft?: number;
  marginRight?: number;
}

/** 获取文档列表 */
export function getDocuments() {
  return api.get('/v1/documents') as Promise<ApiResponse<DocumentAsset[]>>;
}

/** 获取文档详情 */
export function getDocument(id: string) {
  return api.get(`/v1/documents/${id}`) as Promise<ApiResponse<DocumentAsset>>;
}

/** 获取文档投影（编辑内容） */
export function getDocumentProjection(id: string) {
  return api.get(`/v1/documents/${id}/projection`) as Promise<ApiResponse<unknown>>;
}

/** 上传文档文件 */
export function importDocument(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/v1/documents/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  }) as Promise<ApiResponse<{ documentId: string; fileName: string; status: string }>>;
}

/** 删除文档 */
export function deleteDocument(id: string) {
  return api.delete(`/v1/documents/${id}`) as Promise<ApiResponse<null>>;
}

/** 更新文档状态 */
export function updateDocumentStatus(id: string, status: string) {
  return api.put(`/v1/documents/${id}/status?status=${status}`) as Promise<ApiResponse<null>>;
}

/** 归档文档 */
export function archiveDocument(id: string) {
  return api.put(`/v1/documents/${id}/archive`) as Promise<ApiResponse<null>>;
}

/** 获取文档版本历史 */
export function getVersionHistory(id: string) {
  return api.get(`/v1/documents/${id}/versions`) as Promise<ApiResponse<DocumentVersion[]>>;
}

/** 获取文档节点树 */
export function getDocumentNodes(id: string) {
  return api.get(`/v1/documents/${id}/nodes`) as Promise<ApiResponse<DocumentNode[]>>;
}

/** 更新节点文本内容 */
export function updateNodeText(documentId: string, nodeId: string, text: string) {
  return api.put(`/v1/documents/${documentId}/nodes/${nodeId}/text`, { text }) as Promise<ApiResponse<void>>;
}

// ========== 保存文档 ==========

export interface NodeUpdate {
  nodeId: string;
  text: string;
}

export interface SaveResult {
  success: boolean;
  changeSetId?: string;
  newVersion: number;
  errorCode?: string;
  errorMessage?: string;
}

/** 保存文档编辑结果（批量更新节点 + 自动创建并提交变更集） */
export function saveDocument(documentId: string, changes: NodeUpdate[], summary?: string) {
  return api.post(`/v1/documents/${documentId}/save`, {
    changes,
    summary: summary || '手动编辑保存',
  }) as Promise<ApiResponse<SaveResult>>;
}

// ========== 变更集 ==========

export interface Change {
  changeId: string;
  operation: string;
  targetNodeId: string;
  targetNodeType: string;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  position: string | null;
  context: string | null;
}

export interface ChangeSet {
  changeSetId: string;
  documentId: string;
  expectedVersion: number;
  summary: string;
  authorId: string;
  authorType: string;
  changes: Change[];
  reviewStatus: string;
  createdAt: string;
  updatedAt: string;
  rejectionReason?: string;
  failureMessage?: string;
}

/** 获取文档变更集列表 */
export function getChangeSets(documentId: string) {
  return api.get(`/v1/documents/${documentId}/changesets`) as Promise<ApiResponse<ChangeSet[]>>;
}

/** 创建变更集（草稿） */
export function createChangeSet(documentId: string, summary: string, changes: unknown[]) {
  return api.post(`/v1/documents/${documentId}/changesets`, {
    documentId,
    summary,
    changes
  }) as Promise<ApiResponse<ChangeSet>>;
}

/** 提审变更集（草稿 → 待审阅） */
export function submitChangeSet(documentId: string, changeSetId: string) {
  return api.post(`/v1/documents/${documentId}/changesets/${changeSetId}/submit`) as Promise<ApiResponse<ChangeSet>>;
}

/** 接受变更集（合并到文档） */
export function acceptChangeSet(documentId: string, changeSetId: string) {
  return api.post(`/v1/documents/${documentId}/changesets/${changeSetId}/accept`) as Promise<ApiResponse<ChangeSet>>;
}

/** 拒绝变更集 */
export function rejectChangeSet(documentId: string, changeSetId: string, reason?: string) {
  return api.post(`/v1/documents/${documentId}/changesets/${changeSetId}/reject`, { reason }) as Promise<ApiResponse<ChangeSet>>;
}

/** 删除变更集 */
export function deleteChangeSet(documentId: string, changeSetId: string) {
  return api.delete(`/v1/documents/${documentId}/changesets/${changeSetId}`) as Promise<ApiResponse<null>>;
}

// ========== 搜索 ==========

export interface SearchMatch {
  nodeId: string;
  nodeType: string;
  textContent: string;
  context: string;
  matchStart: number;
  matchEnd: number;
  attributes: Record<string, unknown>;
}

export interface SearchResult {
  documentId: string;
  query: string;
  totalMatches: number;
  searchTimeMs: number;
  matches: SearchMatch[];
}

export interface SearchQuery {
  query: string;
  mode?: 'text' | 'regex' | 'fuzzy';
  caseSensitive?: boolean;
  wholeWord?: boolean;
  maxResults?: number;
  contextChars?: number;
  formatQuery?: boolean;
}

/** 在单个文档中搜索 */
export function searchInDocument(documentId: string, params: SearchQuery) {
  return api.post(`/v1/documents/${documentId}/search`, params) as Promise<ApiResponse<SearchResult>>;
}

/** 在所有文档中搜索（聚合结果） */
export async function searchAllDocuments(params: SearchQuery): Promise<{
  results: Array<{ documentId: string; title: string; totalMatches: number; matches: SearchMatch[] }>;
  totalMatches: number;
}> {
  const docRes = await getDocuments();
  const documents = docRes.data;

  const searchPromises = documents.map(async (doc) => {
    try {
      const res = await searchInDocument(doc.documentId, params);
      const sr = res.data;
      return {
        documentId: doc.documentId,
        title: doc.fileName.replace(/\.(docx|doc|pdf|txt)$/i, ''),
        totalMatches: sr.totalMatches,
        matches: sr.matches,
      };
    } catch {
      return {
        documentId: doc.documentId,
        title: doc.fileName.replace(/\.(docx|doc|pdf|txt)$/i, ''),
        totalMatches: 0,
        matches: [],
      };
    }
  });

  const results = await Promise.all(searchPromises);
  const filtered = results.filter((r) => r.totalMatches > 0);
  const totalMatches = filtered.reduce((sum, r) => sum + r.totalMatches, 0);

  return { results: filtered, totalMatches };
}

/** 查找并替换 */
export function findAndReplace(documentId: string, data: { query: string; replacement: string; mode?: string }) {
  return api.post(`/v1/documents/${documentId}/search/replace`, data) as Promise<ApiResponse<{ totalMatches: number; replacedCount: number; replacedNodeIds: string[] }>>;
}

// ========== AI 智能编辑 ==========

export interface AiEditRequest {
  documentId: string;
  instruction: string;
  nodeIds?: string[];
  documentContext?: string;
}

export interface AiSuggestion {
  nodeId: string;
  originalText: string;
  suggestedText: string;
  description: string;
  operation: string;
}

export interface AiEditResponse {
  summary: string;
  suggestions: AiSuggestion[];
  rawResponse?: string;
}

export interface AiStatus {
  enabled: boolean;
  configured: boolean;
  model: string;
  endpoint: string;
}

/** 获取 AI 配置状态 */
export function getAiStatus() {
  return api.get('/v1/ai/status') as Promise<ApiResponse<AiStatus>>;
}

/** AI 智能编辑 */
export function aiEdit(documentId: string, instruction: string, context?: string, nodeIds?: string[]) {
  return api.post('/v1/ai/edit', {
    documentId,
    instruction,
    documentContext: context,
    nodeIds,
  } as AiEditRequest) as Promise<ApiResponse<AiEditResponse>>;
}

/** AI 编辑并自动创建变更集 */
export function aiEditAndApply(documentId: string, instruction: string, context?: string, nodeIds?: string[]) {
  return api.post('/v1/ai/edit-and-apply', {
    documentId,
    instruction,
    documentContext: context,
    nodeIds,
  } as AiEditRequest) as Promise<ApiResponse<ChangeSet>>;
}

export default api;