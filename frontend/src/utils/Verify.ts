interface VerifyRule {
  message: string
}

type VerifyCallback = (error?: Error) => void

type VerifyHandler = (rule: VerifyRule, value: unknown, callback: VerifyCallback) => void

const regs = {
  email: /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/,
  number: /^([0]|[1-9][0-9]*)$/,
  password: /^(?=.*\d)(?=.*[a-zA-Z])(?=.*[~!@#$%^&*_])[\da-zA-Z~!@#$%^&*_]{8,}$/,
  shareCode: /^[A-Za-z0-9]+$/,
}

const verify = (rule: VerifyRule, value: unknown, reg: RegExp, callback: VerifyCallback) => {
  if (value === null || value === undefined || value === '') {
    callback()
    return
  }

  if (reg.test(String(value))) {
    callback()
  } else {
    callback(new Error(rule.message))
  }
}

const getPasswordError = (value: string): string | null => {
  if (!value) return null
  if (value.length < 8) {
    return '密码长度至少 8 位'
  }
  if (!/\d/.test(value)) {
    return '密码必须包含数字'
  }
  if (!/[a-zA-Z]/.test(value)) {
    return '密码必须包含字母'
  }
  if (!/[~!@#$%^&*_]/.test(value)) {
    return '密码必须包含特殊字符（~!@#$%^&*_）'
  }
  if (!/^[\da-zA-Z~!@#$%^&*_]+$/.test(value)) {
    return '密码只能包含数字、字母和特殊字符（~!@#$%^&*_）'
  }
  return null
}

const email: VerifyHandler = (rule, value, callback) => verify(rule, value, regs.email, callback)
const number: VerifyHandler = (rule, value, callback) => verify(rule, value, regs.number, callback)

const password: VerifyHandler = (_rule, value, callback) => {
  const passwordValue = String(value ?? '')
  if (!passwordValue) {
    callback()
    return
  }

  const error = getPasswordError(passwordValue)
  if (error) {
    callback(new Error(error))
    return
  }
  callback()
}

const shareCode: VerifyHandler = (rule, value, callback) => verify(rule, value, regs.shareCode, callback)

export default {
  email,
  number,
  password,
  shareCode,
}
