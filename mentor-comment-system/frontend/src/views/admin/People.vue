<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">师生管理</div>
        <div class="mc-page-sub">
          维护导师与学生档案及其指导关系；导师仅能对指导关系内的学生填写点评。停用而非删除，历史点评与审核记录保持可追溯
        </div>
      </div>
    </div>

    <div class="people-grid">
      <!-- 导师列表 -->
      <div class="mc-card teacher-panel">
        <div class="panel-head">
          <span>
            导师
            <span class="panel-count">{{ teachers.length }} 人</span>
          </span>
          <el-button type="primary" size="small" :icon="Plus" @click="openTeacherForm()">
            新增
          </el-button>
        </div>
        <div class="teacher-list">
          <div
            v-for="t in teachers"
            :key="t.id"
            class="teacher-item"
            :class="{ active: selectedId === t.id, disabled: t.status === 0 }"
            role="button"
            tabindex="0"
            @click="selectTeacher(t)"
            @keydown.enter="selectTeacher(t)"
          >
            <div class="ti-avatar">{{ t.name?.slice(0, 1) }}</div>
            <div class="ti-meta">
              <div class="ti-name">
                {{ t.name }}
                <el-tag v-if="t.status === 0" type="info" size="small" effect="plain">
                  停用
                </el-tag>
              </div>
              <div class="ti-sub">
                {{ t.title || '教师' }} · {{ t.teacherNo }} · 指导 {{ t.studentCount ?? 0 }} 人
              </div>
            </div>
            <el-dropdown trigger="click" @command="(c) => onTeacherCommand(c, t)">
              <el-button text size="small" :icon="MoreFilled" @click.stop />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑档案</el-dropdown-item>
                  <el-dropdown-item command="reset">重置密码</el-dropdown-item>
                  <el-dropdown-item v-if="t.status !== 0" command="disable" divided>
                    停用
                  </el-dropdown-item>
                  <el-dropdown-item v-else command="enable" divided>启用</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <EmptyHint v-if="!teachers.length" compact icon="UserFilled" title="暂无导师" />
        </div>
      </div>

      <!-- 学生列表 -->
      <div class="mc-card student-panel">
        <div class="panel-head">
          <span>
            {{ selectedTeacher?.name || '—' }} 的指导学生
            <span class="panel-count">{{ students.length }} 人</span>
          </span>
          <div class="head-btns">
            <el-button size="small" :icon="Plus" @click="openStudentForm()">新增学生</el-button>
            <el-button
              type="primary"
              size="small"
              :icon="Link"
              :disabled="!selectedId"
              @click="openBind"
            >
              绑定学生
            </el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="students" stripe row-key="id" class="student-table">
          <el-table-column prop="studentNo" label="学号" width="120">
            <template #default="{ row }">
              <span class="mc-mono">{{ row.studentNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="clazzName" label="班级" min-width="130" />
          <el-table-column label="状态" width="76">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="info" size="small" effect="plain">停用</el-tag>
              <el-tag v-else type="success" size="small" effect="plain">在用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="112" fixed="right" align="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openStudentForm(row)"
                >编辑</el-button
              >
              <el-dropdown trigger="click" @command="(c) => onStudentCommand(c, row)">
                <el-button size="small" :icon="MoreFilled" class="more-btn" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="row.status === 0" command="enable"
                      >启用</el-dropdown-item
                    >
                    <el-dropdown-item v-else command="disable">停用</el-dropdown-item>
                    <el-dropdown-item command="unbind" divided>解除指导关系</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
        <EmptyHint
          v-if="!loading && !students.length && selectedId"
          icon="User"
          title="该导师暂无指导学生"
          desc="点击右上角「绑定学生」建立指导关系；导师只能对指导关系内的学生填写点评"
        />
        <div v-if="!selectedId" class="pick-hint">
          <el-icon><Pointer /></el-icon> 请选择左侧导师查看其指导学生
        </div>
      </div>
    </div>

    <!-- 绑定弹窗 -->
    <el-dialog v-model="bindVisible" title="绑定学生" width="520px" :close-on-click-modal="false">
      <div class="bind-tip">
        为导师「{{ selectedTeacher?.name }}」选择要绑定指导关系的学生（可多选）
      </div>
      <el-select
        v-model="bindIds"
        multiple
        filterable
        placeholder="选择学生"
        class="w-full"
        :multiple-limit="50"
      >
        <el-option
          v-for="s in bindableStudents"
          :key="s.id"
          :label="`${s.name}（${s.studentNo} · ${s.clazzName || ''}）`"
          :value="s.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" :disabled="!bindIds.length" @click="doBind">
          确认绑定（{{ bindIds.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 导师表单 -->
    <el-dialog
      v-model="teacherFormVisible"
      :title="teacherEditingId ? '编辑导师档案' : '新增导师'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="teacherFormRef" :model="teacherForm" :rules="teacherRules" label-position="top">
        <div class="form-row">
          <el-form-item label="姓名" prop="name" class="grow">
            <el-input v-model="teacherForm.name" maxlength="20" placeholder="如：张明远" />
          </el-form-item>
          <el-form-item label="工号（同时作为登录名）" prop="teacherNo" class="grow">
            <el-input
              v-model="teacherForm.teacherNo"
              maxlength="20"
              placeholder="如：T2026001"
              :disabled="!!teacherEditingId"
            />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="职称" class="grow">
            <el-input v-model="teacherForm.title" maxlength="20" placeholder="如：副教授" />
          </el-form-item>
          <el-form-item label="学院" class="grow">
            <el-input
              v-model="teacherForm.college"
              maxlength="50"
              placeholder="如：计算与智能技术学院"
            />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="手机" prop="phone" class="grow">
            <el-input v-model="teacherForm.phone" maxlength="11" placeholder="选填" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email" class="grow">
            <el-input v-model="teacherForm.email" maxlength="60" placeholder="选填" />
          </el-form-item>
        </div>
        <div v-if="!teacherEditingId" class="form-hint">
          保存后系统自动创建登录账号，用户名 = 工号，初始密码在保存成功后展示一次
        </div>
      </el-form>
      <template #footer>
        <el-button @click="teacherFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTeacher">保存</el-button>
      </template>
    </el-dialog>

    <!-- 学生表单 -->
    <el-dialog
      v-model="studentFormVisible"
      :title="studentEditingId ? '编辑学生档案' : '新增学生'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="studentFormRef" :model="studentForm" :rules="studentRules" label-position="top">
        <div class="form-row">
          <el-form-item label="姓名" prop="name" class="grow">
            <el-input v-model="studentForm.name" maxlength="20" placeholder="如：王小明" />
          </el-form-item>
          <el-form-item label="学号（同时作为登录名）" prop="studentNo" class="grow">
            <el-input
              v-model="studentForm.studentNo"
              maxlength="20"
              placeholder="如：20250373006"
              :disabled="!!studentEditingId"
            />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="班级" prop="clazzId" class="grow">
            <el-select
              v-model="studentForm.clazzId"
              filterable
              placeholder="选择班级"
              class="w-full"
            >
              <el-option v-for="c in clazzList" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="性别" class="w-140">
            <el-select v-model="studentForm.gender" placeholder="选填" class="w-full">
              <el-option :value="1" label="男" />
              <el-option :value="2" label="女" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="手机" prop="phone" class="grow">
            <el-input v-model="studentForm.phone" maxlength="11" placeholder="选填" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email" class="grow">
            <el-input v-model="studentForm.email" maxlength="60" placeholder="选填" />
          </el-form-item>
        </div>
        <div v-if="!studentEditingId" class="form-hint">
          保存后系统自动创建登录账号，用户名 = 学号，初始密码在保存成功后展示一次
        </div>
      </el-form>
      <template #footer>
        <el-button @click="studentFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStudent">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Pointer, MoreFilled, Link } from '@element-plus/icons-vue'
import EmptyHint from '../../components/EmptyHint.vue'
import {
  allTeachers,
  studentsOfTeacher,
  students as studentsApi,
  clazzOptions,
  bind,
  unbind,
  createTeacher,
  updateTeacher,
  disableTeacher,
  enableTeacher,
  createStudent,
  updateStudent,
  disableStudent,
  enableStudent,
  resetPassword
} from '../../api/person'

/**
 * 师生管理。
 *
 * 两处刻意的交互设计：
 * 1) 停用不是删除。档案被点评、审核记录、操作日志引用，物理删除会让历史报表出现"无主点评"。
 * 2) 停用导师时若存在未完结点评，后端返回 409 与条数，这里弹二次确认后才强制停用——
 *    否则那些点评会永久卡在流程里，而这套系统的研究目的就是规范流程。
 */
const teachers = ref([])
const selectedId = ref(null)
const students = ref([])
const loading = ref(false)
const allStudents = ref([])
const clazzList = ref([])

const bindVisible = ref(false)
const bindIds = ref([])
const binding = ref(false)

const saving = ref(false)
const teacherFormVisible = ref(false)
const teacherFormRef = ref()
const teacherEditingId = ref(null)
const teacherForm = reactive({
  name: '',
  teacherNo: '',
  title: '',
  college: '',
  phone: '',
  email: ''
})

const studentFormVisible = ref(false)
const studentFormRef = ref()
const studentEditingId = ref(null)
const studentForm = reactive({
  name: '',
  studentNo: '',
  clazzId: null,
  gender: null,
  phone: '',
  email: ''
})

const phoneRule = { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
const emailRule = {
  pattern: /^$|^[\w.-]+@[\w.-]+\.[A-Za-z]{2,}$/,
  message: '邮箱格式不正确',
  trigger: 'blur'
}
const noRule = {
  pattern: /^[A-Za-z0-9_-]{3,20}$/,
  message: '仅支持字母数字下划线，3~20 位',
  trigger: 'blur'
}

const teacherRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  teacherNo: [{ required: true, message: '请填写工号', trigger: 'blur' }, noRule],
  phone: [phoneRule],
  email: [emailRule]
}

const studentRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  studentNo: [{ required: true, message: '请填写学号', trigger: 'blur' }, noRule],
  clazzId: [{ required: true, message: '请选择班级', trigger: 'change' }],
  phone: [phoneRule],
  email: [emailRule]
}

const selectedTeacher = computed(() => teachers.value.find((t) => t.id === selectedId.value))
const boundIds = computed(() => new Set(students.value.map((s) => s.id)))
const bindableStudents = computed(() => allStudents.value.filter((s) => !boundIds.value.has(s.id)))

async function loadTeachers() {
  try {
    teachers.value = (await allTeachers()) || []
    if (teachers.value.length) {
      const keep = teachers.value.find((t) => t.id === selectedId.value)
      await selectTeacher(keep || teachers.value[0])
    } else {
      selectedId.value = null
      students.value = []
    }
  } catch (e) {
    /* 已提示 */
  }
}

async function loadAllStudents() {
  try {
    allStudents.value = (await studentsApi(null, false)) || []
  } catch (e) {
    /* 已提示 */
  }
}

async function loadClazz() {
  try {
    clazzList.value = (await clazzOptions()) || []
  } catch (e) {
    /* 已提示 */
  }
}

async function selectTeacher(t) {
  if (!t) return
  selectedId.value = t.id
  loading.value = true
  try {
    students.value = (await studentsOfTeacher(t.id)) || []
  } catch (e) {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

// ---------------- 指导关系 ----------------

function openBind() {
  bindIds.value = []
  bindVisible.value = true
}

async function doBind() {
  binding.value = true
  try {
    await bind({ teacherId: selectedId.value, studentIds: bindIds.value })
    ElMessage.success('绑定成功')
    bindVisible.value = false
    await selectTeacher(selectedTeacher.value)
    await loadTeachers()
  } catch (e) {
    /* 已提示 */
  } finally {
    binding.value = false
  }
}

async function doUnbind(row) {
  try {
    await ElMessageBox.confirm(
      `确认解除「${selectedTeacher.value?.name}」与「${row.name}」的指导关系？`,
      '解除关系',
      { type: 'warning', confirmButtonText: '解除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await unbind(row.relationId)
    ElMessage.success('已解除')
    await selectTeacher(selectedTeacher.value)
    await loadTeachers()
  } catch (e) {
    /* 已提示 */
  }
}

// ---------------- 导师档案 ----------------

function openTeacherForm(row) {
  teacherEditingId.value = row?.id || null
  Object.assign(teacherForm, {
    name: row?.name || '',
    teacherNo: row?.teacherNo || '',
    title: row?.title || '',
    college: row?.college || '',
    phone: row?.phone || '',
    email: row?.email || ''
  })
  teacherFormVisible.value = true
}

async function saveTeacher() {
  await teacherFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...teacherForm, status: 1 }
      if (teacherEditingId.value) {
        await updateTeacher(teacherEditingId.value, payload)
        ElMessage.success('已保存')
      } else {
        const r = await createTeacher(payload)
        showInitialPassword(r)
      }
      teacherFormVisible.value = false
      await loadTeachers()
    } catch (e) {
      /* 已提示 */
    } finally {
      saving.value = false
    }
  })
}

function showInitialPassword(r) {
  ElMessageBox.alert(
    `登录名：${r.username}\n初始密码：${r.initialPassword}\n\n该密码只展示这一次，请转告本人并提醒首次登录后修改。`,
    '账号已创建',
    { confirmButtonText: '我已记录', type: 'success' }
  )
}

/** 学生行「更多」：停用/启用是低频且不可逆感强的操作，不该和「编辑」并排 */
async function onStudentCommand(cmd, row) {
  if (cmd === 'enable') return doEnableStudent(row)
  if (cmd === 'disable') return doDisableStudent(row)
  if (cmd === 'unbind') return doUnbind(row)
}

async function onTeacherCommand(cmd, row) {
  if (cmd === 'edit') {
    openTeacherForm(row)
    return
  }
  if (cmd === 'reset') {
    try {
      await ElMessageBox.confirm(`确认重置「${row.name}」的登录密码？`, '重置密码', {
        type: 'warning',
        confirmButtonText: '重置',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
    try {
      const r = await resetPassword(row.userId)
      ElMessageBox.alert(`新密码：${r.password}\n\n请转告本人并提醒尽快修改。`, '密码已重置', {
        confirmButtonText: '我已记录',
        type: 'success'
      })
    } catch (e) {
      /* 已提示 */
    }
    return
  }
  if (cmd === 'enable') {
    try {
      await enableTeacher(row.id)
      ElMessage.success('已启用')
      await loadTeachers()
    } catch (e) {
      /* 已提示 */
    }
    return
  }
  if (cmd === 'disable') {
    await doDisableTeacher(row)
  }
}

async function doDisableTeacher(row) {
  try {
    await disableTeacher(row.id, false)
    ElMessage.success('已停用')
  } catch (e) {
    // 409：存在未完结点评。提示条数后由教务决定是否强制停用
    const msg = String(e?.message || '')
    if (!msg.includes('未完结点评')) return
    try {
      await ElMessageBox.confirm(`${msg}。确认强制停用？`, '存在未完结点评', {
        type: 'warning',
        confirmButtonText: '强制停用',
        cancelButtonText: '取消'
      })
    } catch (err) {
      return
    }
    try {
      await disableTeacher(row.id, true)
      ElMessage.success('已强制停用')
    } catch (err) {
      /* 已提示 */
      return
    }
  }
  await loadTeachers()
}

// ---------------- 学生档案 ----------------

function openStudentForm(row) {
  studentEditingId.value = row?.id || null
  Object.assign(studentForm, {
    name: row?.name || '',
    studentNo: row?.studentNo || '',
    clazzId: row?.clazzId || null,
    gender: row?.gender ?? null,
    phone: row?.phone || '',
    email: row?.email || ''
  })
  studentFormVisible.value = true
}

async function saveStudent() {
  await studentFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...studentForm, status: 1 }
      if (studentEditingId.value) {
        await updateStudent(studentEditingId.value, payload)
        ElMessage.success('已保存')
      } else {
        const r = await createStudent(payload)
        showInitialPassword(r)
      }
      studentFormVisible.value = false
      await loadTeachers()
      await loadAllStudents()
    } catch (e) {
      /* 已提示 */
    } finally {
      saving.value = false
    }
  })
}

async function doDisableStudent(row) {
  try {
    await ElMessageBox.confirm(
      `停用后「${row.name}」将无法登录，历史点评仍保留。确认停用？`,
      '停用学生',
      { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await disableStudent(row.id)
    ElMessage.success('已停用')
    await selectTeacher(selectedTeacher.value)
    await loadAllStudents()
  } catch (e) {
    /* 已提示 */
  }
}

async function doEnableStudent(row) {
  try {
    await enableStudent(row.id)
    ElMessage.success('已启用')
    await selectTeacher(selectedTeacher.value)
    await loadAllStudents()
  } catch (e) {
    /* 已提示 */
  }
}

onMounted(() => {
  loadClazz()
  loadAllStudents()
  loadTeachers()
})
</script>

<style scoped>
.people-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
  align-items: start;
}
.teacher-panel {
  padding: 14px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  gap: 8px;
}
.head-btns {
  display: flex;
  gap: 8px;
}
.panel-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--mc-text-3);
  margin-left: 6px;
}
.teacher-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}
.teacher-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 8px 10px 12px;
  border: 1px solid var(--mc-border-weak);
  background: #fff;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  font-family: inherit;
}
.teacher-item:hover {
  border-color: #c7d4f8;
  background: #f8faff;
}
.teacher-item.active {
  border-color: var(--mc-primary);
  background: var(--mc-primary-weak);
  box-shadow: 0 0 0 2px rgba(43, 92, 230, 0.1);
}
.teacher-item.disabled {
  opacity: 0.66;
}
.ti-avatar {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #2b5ce6, #4f7bf0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.ti-meta {
  flex: 1;
  min-width: 0;
}
.ti-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--mc-text-1);
  display: flex;
  align-items: center;
  gap: 6px;
}
.ti-sub {
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-top: 2px;
}
.student-panel {
  padding: 14px 16px;
}
.student-table {
  margin-top: 4px;
}
.more-btn {
  margin-left: 2px;
  padding: 5px 6px;
}
.pick-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--mc-text-3);
  font-size: 13px;
  padding: 60px 0;
}
.bind-tip {
  font-size: 12.5px;
  color: var(--mc-text-2);
  margin-bottom: 12px;
}
.form-row {
  display: flex;
  gap: 12px;
}
.grow {
  flex: 1;
  min-width: 0;
}
.w-140 {
  width: 140px;
}
.w-full {
  width: 100%;
}
.form-hint {
  font-size: 12px;
  color: var(--mc-text-3);
  line-height: 1.6;
  background: var(--mc-bg);
  padding: 8px 12px;
  border-radius: 8px;
}

@media (max-width: 1100px) {
  .people-grid {
    grid-template-columns: 1fr;
  }
}
</style>
