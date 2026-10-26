<template>
  <div class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">师生管理</div>
        <div class="mc-page-sub">维护导师与学生的指导关系；导师仅能对指导关系内的学生填写点评</div>
      </div>
    </div>

    <div class="people-grid">
      <!-- 导师列表 -->
      <div class="mc-card teacher-panel">
        <div class="panel-head">
          <span>导师</span>
          <span class="panel-count">{{ teachers.length }} 人</span>
        </div>
        <div class="teacher-list">
          <button
            v-for="t in teachers"
            :key="t.id"
            type="button"
            class="teacher-item"
            :class="{ active: selectedId === t.id }"
            @click="selectTeacher(t)"
          >
            <div class="ti-avatar">{{ t.name?.slice(0, 1) }}</div>
            <div class="ti-meta">
              <div class="ti-name">{{ t.name }}</div>
              <div class="ti-sub">{{ t.title || '教师' }} · {{ t.teacherNo }}</div>
            </div>
            <el-icon v-if="selectedId === t.id" class="ti-check"><ArrowRight /></el-icon>
          </button>
          <el-empty v-if="!teachers.length" description="暂无导师" :image-size="60" />
        </div>
      </div>

      <!-- 学生列表 -->
      <div class="mc-card student-panel">
        <div class="panel-head">
          <span>
            {{ selectedTeacher?.name || '—' }} 的指导学生
            <span class="panel-count">{{ students.length }} 人</span>
          </span>
          <el-button
            type="primary"
            size="small"
            :icon="Plus"
            :disabled="!selectedId"
            @click="openBind"
          >
            绑定学生
          </el-button>
        </div>

        <el-table v-loading="loading" :data="students" stripe row-key="id" class="student-table">
          <el-table-column prop="studentNo" label="学号" width="130">
            <template #default="{ row }">
              <span class="mc-mono">{{ row.studentNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="姓名" width="110" />
          <el-table-column prop="clazzName" label="班级" min-width="140" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button text type="danger" size="small" @click="doUnbind(row)">解除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!loading && !students.length && selectedId"
          description="该导师暂无指导学生，点击右上角「绑定学生」添加"
          :image-size="72"
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowRight, Pointer } from '@element-plus/icons-vue'
import {
  teachers as teachersApi,
  studentsOfTeacher,
  students as studentsApi,
  bind,
  unbind
} from '../../api/person'

const teachers = ref([])
const selectedId = ref(null)
const students = ref([])
const loading = ref(false)

const allStudents = ref([])
const bindVisible = ref(false)
const bindIds = ref([])
const binding = ref(false)

const selectedTeacher = computed(() => teachers.value.find((t) => t.id === selectedId.value))
const boundIds = computed(() => new Set(students.value.map((s) => s.id)))
const bindableStudents = computed(() => allStudents.value.filter((s) => !boundIds.value.has(s.id)))

async function loadTeachers() {
  try {
    teachers.value = (await teachersApi()) || []
    if (teachers.value.length && !selectedId.value) {
      selectTeacher(teachers.value[0])
    }
  } catch (e) {
    /* 已提示 */
  }
}

async function loadAllStudents() {
  try {
    allStudents.value = (await studentsApi()) || []
  } catch (e) {
    /* 已提示 */
  }
}

async function selectTeacher(t) {
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
  } catch (e) {
    /* 已提示 */
  }
}

onMounted(() => {
  loadTeachers()
  loadAllStudents()
})
</script>

<style scoped>
.people-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
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
  padding: 10px 12px;
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
}
.ti-sub {
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-top: 2px;
}
.ti-check {
  color: var(--mc-primary);
}
.student-panel {
  padding: 14px 16px;
}
.student-table {
  margin-top: 4px;
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
.w-full {
  width: 100%;
}

@media (max-width: 1100px) {
  .people-grid {
    grid-template-columns: 1fr;
  }
}
</style>
