<template>
  <el-dialog
    :model-value="modelValue"
    title="修改密码"
    width="440px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @closed="reset"
  >
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="修改成功后当前登录会失效，需要用新密码重新登录"
      class="notice"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="pwd-form"
      @submit.prevent
    >
      <el-form-item label="当前密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入当前登录密码"
        />
      </el-form-item>

      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="8-32 位，需含字母和数字"
        />
        <!-- 规则实时反馈：不等到提交才报错，否则用户要试错三次才知道要求 -->
        <ul class="pwd-rules">
          <li :class="{ ok: ruleState.length }">
            <el-icon><component :is="ruleState.length ? 'CircleCheck' : 'CircleClose'" /></el-icon>
            8-32 位
          </li>
          <li :class="{ ok: ruleState.letter }">
            <el-icon><component :is="ruleState.letter ? 'CircleCheck' : 'CircleClose'" /></el-icon>
            包含字母
          </li>
          <li :class="{ ok: ruleState.digit }">
            <el-icon><component :is="ruleState.digit ? 'CircleCheck' : 'CircleClose'" /></el-icon>
            包含数字
          </li>
        </ul>
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="再次输入新密码"
          @keyup.enter="submit"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { changePassword } from '../api/auth'

defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 新密码规则状态：仅用于展示，权威校验在后端 */
const ruleState = computed(() => {
  const v = form.newPassword || ''
  return {
    length: v.length >= 8 && v.length <= 32,
    letter: /[A-Za-z]/.test(v),
    digit: /\d/.test(v)
  }
})

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => {
        if (!value) return cb()
        const s = ruleState.value
        if (!s.length) return cb(new Error('新密码长度需为 8-32 位'))
        if (!s.letter || !s.digit) return cb(new Error('新密码需同时包含字母和数字'))
        if (value === form.oldPassword) return cb(new Error('新密码不能与当前密码相同'))
        return cb()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => {
        if (value && value !== form.newPassword) return cb(new Error('两次输入的新密码不一致'))
        return cb()
      },
      trigger: 'blur'
    }
  ]
}

function reset() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  submitting.value = false
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    emit('update:modelValue', false)
    emit('changed')
  } catch (e) {
    // 错误提示已由请求拦截器统一弹出，这里只恢复按钮可用
    submitting.value = false
  }
}
</script>

<style scoped>
.notice {
  margin-bottom: 16px;
}
.pwd-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.pwd-rules {
  list-style: none;
  display: flex;
  gap: 14px;
  margin: 7px 0 0;
  padding: 0;
  font-size: 12px;
  color: var(--mc-text-4);
}
.pwd-rules li {
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color 0.15s;
}
.pwd-rules li.ok {
  color: var(--sn-pos);
}
.pwd-rules .el-icon {
  font-size: 13px;
}
</style>
