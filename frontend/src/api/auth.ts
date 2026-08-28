import request from './request'

export interface LoginPayload {
  studentId: string
  password: string
}

export interface User {
  id: number
  studentId: string
  username: string
  realName: string
  classId: number | null
  role: 'student' | 'counselor' | 'admin' | 'super_admin'
}

export interface LoginResult {
  token: string
  user: User
}

export const login = (data: LoginPayload) =>
  request.post<LoginResult, LoginResult>('/auth/login', data)

export const loginWithYibanTestUser = () =>
  request.post<LoginResult, LoginResult>('/auth/yiban/login')

export const fetchMe = () => request.get<User, User>('/auth/me')

export const logout = () => request.post('/auth/logout')
