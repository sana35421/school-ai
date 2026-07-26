import request from './request'
import type { Conversation, Message } from './chat'

export interface StudentOverview {
  id: number
  studentId: string
  realName: string
  lastActiveAt: string | null
  conversationCount: number
}

export interface KbDocument {
  id: number
  name: string
  type: string
  size: number
  status: 'pending' | 'indexing' | 'completed' | 'failed'
  uploadedAt: string
}

export const getClassStudents = () =>
  request.get<StudentOverview[], StudentOverview[]>('/admin/class/students')

export const getClassConversations = (studentId?: number, days = 7) =>
  request.get<Conversation[], Conversation[]>(
    `/admin/class/conversations?studentId=${studentId ?? ''}&days=${days}`
  )

export const getClassMessages = (conversationId: string) =>
  request.get<Message[], Message[]>(`/admin/class/messages/${conversationId}`)

export const getKbDocuments = () =>
  request.get<KbDocument[], KbDocument[]>('/admin/kb/documents')

export const uploadKbDocument = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post<KbDocument, KbDocument>('/admin/kb/documents', form)
}

export const deleteKbDocument = (docId: number) =>
  request.delete(`/admin/kb/documents/${docId}`)
