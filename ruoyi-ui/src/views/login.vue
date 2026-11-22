<template>
  <main class="login lawyer-surface">
    <section class="login-card" aria-label="登录 AI 律师平台">
      <aside class="login-intro">
        <div class="login-brand"><LawyerMark /><span>{{ title }}</span></div>
        <div class="login-orbit" aria-hidden="true">
          <div class="orbit-rim"></div>
          <div class="orbit-center"><LawyerMark /></div>
          <i class="orbit-point"></i>
        </div>
        <div class="login-intro-copy">
          <h2>智能法律咨询</h2>
          <p>梳理问题、整理思路，随时继续你的咨询。</p>
        </div>
      </aside>

      <div class="login-form-panel">
        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" label-position="top" @submit.prevent="handleLogin">
          <div class="login-form-heading">
            <h1>欢迎回来</h1>
            <p>使用你的平台账号登录</p>
          </div>

          <el-form-item prop="username" label="账号">
            <el-input v-model="loginForm.username" size="large" autocomplete="username" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password" label="密码">
            <el-input
              v-model="loginForm.password"
              type="password"
              size="large"
              autocomplete="current-password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item v-if="captchaEnabled" prop="code" label="验证码">
            <div class="captcha-row">
              <el-input
                v-model="loginForm.code"
                size="large"
                autocomplete="off"
                placeholder="请输入验证码"
              />
              <button type="button" class="captcha-button" aria-label="刷新验证码" title="刷新验证码" @click="getCode">
                <img v-if="codeUrl" :src="codeUrl" alt="验证码图片" />
                <span v-else>加载验证码</span>
              </button>
            </div>
          </el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
            <router-link v-if="register" to="/register">立即注册</router-link>
          </div>
          <el-button :loading="loading" size="large" type="primary" native-type="submit" class="login-submit">
            {{ loading ? '登录中…' : '登录' }}
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import { Lock, User } from '@element-plus/icons-vue'
import LawyerMark from '@/components/LawyerMark.vue'

const title = import.meta.env.VITE_APP_TITLE
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  if (loading.value) return
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  Object.assign(loginForm.value, {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === "true"
  })
}

getCode()
getCookie()
</script>

<style scoped>
.login {
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  place-items: center;
  padding: 32px 24px;
  background: radial-gradient(ellipse at 25% 0%, var(--law-wash), transparent 56%), var(--law-bg);
}

.login-card {
  display: grid;
  grid-template-columns: 1fr 1.02fr;
  width: min(100%, 880px);
  min-height: 532px;
  overflow: hidden;
  border: 1px solid var(--law-line);
  border-radius: 28px;
  background: var(--law-card);
  box-shadow: var(--law-shadow);
}

.login-intro {
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 26px;
  margin: 0;
  padding: 34px;
  border-radius: 0;
  color: var(--law-ink);
  font: inherit;
  background:
    radial-gradient(ellipse at 60% 45%, color-mix(in srgb, var(--law-card) 78%, transparent), transparent 61%),
    linear-gradient(148deg, var(--law-panel), var(--law-wash));
  isolation: isolate;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 690;
  --mark-size: 34px;
}

.login-orbit {
  position: relative;
  display: grid;
  align-self: center;
  justify-self: center;
  width: 245px;
  height: 245px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--law-accent) 19%, transparent);
  border-radius: 50%;
}

.orbit-rim {
  position: absolute;
  inset: 23px;
  rotate: -19deg;
  border: 1px solid color-mix(in srgb, var(--law-accent) 26%, transparent);
  border-radius: 50%;
  box-shadow: 0 0 36px color-mix(in srgb, var(--law-card) 85%, transparent) inset;
}

.orbit-center {
  z-index: 1;
  display: grid;
  width: 142px;
  height: 142px;
  place-items: center;
  rotate: -10deg;
  border: 1px solid color-mix(in srgb, var(--law-accent) 17%, var(--law-card));
  border-radius: 43px;
  background: linear-gradient(145deg, var(--law-card) 7%, var(--law-wash));
  box-shadow: 0 13px 27px #213e2320, 0 2px 5px #213e2310;
  --mark-size: 86px;
  --mark-bg: transparent;
  --mark-ink: var(--law-accent);
}

.orbit-point {
  position: absolute;
  top: 36px;
  right: 37px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--law-card);
  border-radius: 50%;
  background: var(--law-accent);
}

.login-intro-copy h2 {
  margin: 0;
  font-size: clamp(24px, 3.3vw, 36px);
  font-weight: 690;
  letter-spacing: -.04em;
}

.login-intro-copy p,
.login-form-heading p {
  margin: 10px 0 0;
  color: var(--law-muted);
  font-size: 14px;
  line-height: 1.65;
}

.login-form-panel {
  display: grid;
  place-items: center;
  padding: 36px clamp(28px, 5vw, 60px);
}

.login-form {
  width: 100%;
  --el-color-primary: var(--law-accent);
  --el-text-color-regular: var(--law-ink);
  --el-border-color: var(--law-line);
}

.login-form-heading {
  margin-bottom: 25px;
}

.login-form-heading h1 {
  margin: 0;
  color: var(--law-ink);
  font-size: 35px;
  font-weight: 690;
  letter-spacing: -.04em;
}

.login-form-heading p {
  margin-top: 7px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  font-weight: 620;
  line-height: 1.25;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 11px;
  background: var(--law-bg);
}

.login-form :deep(.el-input__inner) {
  font-size: 14px;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 107px;
  gap: 9px;
  width: 100%;
}

.captcha-button {
  display: grid;
  min-height: 44px;
  overflow: hidden;
  place-items: center;
  padding: 0;
  border: 1px solid var(--law-line);
  border-radius: 11px;
  background: #f4f6f2;
  color: #506158;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.captcha-button img {
  display: block;
  width: 100%;
  max-height: 42px;
  object-fit: contain;
}

.captcha-button:focus-visible {
  outline: 2px solid var(--law-accent);
  outline-offset: 2px;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -1px 0 12px;
}

.login-options a {
  color: var(--law-accent);
}

.login-submit {
  width: 100%;
  min-height: 46px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 650;
  --el-button-text-color: var(--law-on-accent);
  --el-button-hover-text-color: var(--law-on-accent);
  --el-button-hover-bg-color: var(--law-accent-strong);
  --el-button-hover-border-color: var(--law-accent-strong);
}

@media (max-width: 720px) {
  .login {
    padding: 20px 15px;
  }

  .login-card {
    grid-template-columns: 1fr;
    width: min(100%, 420px);
    min-height: auto;
    border-radius: 22px;
  }

  .login-intro {
    grid-template-rows: auto;
    padding: 21px 24px;
    border-bottom: 1px solid var(--law-line);
  }

  .login-orbit,
  .login-intro-copy {
    display: none;
  }

  .login-form-panel {
    padding: 26px 24px 28px;
  }

  .login-form-heading h1 {
    font-size: 30px;
  }

  .login-form :deep(.el-input__inner) {
    font-size: 16px;
  }
}
</style>
