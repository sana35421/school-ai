import request from './request'

export interface UploadRecordPayload {
  fileName: string
  fileType: string
  fileSize: number
}

export interface UploadRecordResult {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  createdAt: string
}

export const recordUpload = (payload: UploadRecordPayload) =>
  request.post<UploadRecordResult, UploadRecordResult>('/upload/record', payload)