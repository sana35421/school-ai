import request from './request'

export interface UploadRecord {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  status: string
  createdAt: string
}

export async function uploadChatFile(file: File): Promise<UploadRecord> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadRecord, UploadRecord>('/upload/file', formData, { timeout: 120000 })
}
