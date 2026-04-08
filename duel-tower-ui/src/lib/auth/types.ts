export type AuthUser = {
  username: string
  roles: string[]
}

export type LoginRequest = {
  username: string
  password: string
}
