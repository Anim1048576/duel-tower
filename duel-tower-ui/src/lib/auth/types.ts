export type AuthUser = {
  username: string
  roles: string[]
}

export type LoginRequest = {
  username: string
  password: string
}

export type SignupRequest = {
  username: string
  password: string
}
