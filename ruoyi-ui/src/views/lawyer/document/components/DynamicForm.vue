<template>
  <section class="doc-form-pane" aria-label="案件要素采集表单">
    <div class="doc-pane-head">
      <div class="doc-pane-title-group">
        <h2>案件要素采集</h2>
        <span v-if="template" class="doc-pane-subtitle">{{ template.name }}</span>
      </div>
      <div class="doc-form-quick-actions">
        <el-button
          v-if="template && hasDemoData"
          size="small"
          plain
          :disabled="generating"
          @click="handleFillDemo"
        >
          <el-icon><MagicStick /></el-icon>
          <span>填入示例数据</span>
        </el-button>
        <el-button
          v-if="template"
          size="small"
          text
          :disabled="generating"
          @click="handleReset"
        >
          清空重置
        </el-button>
      </div>
    </div>

    <div v-if="!template" class="doc-form-empty">
      <el-icon size="28"><EditPen /></el-icon>
      <p>请先在上方选择一种法律文书模板</p>
    </div>

    <template v-else>
      <div class="doc-form-scroll">
        <el-form
          ref="formRef"
          :model="modelValue"
          :rules="rules"
          label-position="top"
          class="doc-dynamic-form"
          scroll-to-error
        >
          <div class="doc-form-group doc-form-title-group">
            <el-form-item label="自定义文书标题（可选）">
              <el-input
                :model-value="customTitle"
                maxlength="200"
                clearable
                :placeholder="`留空自动生成，如：${template.name}-当事人姓名`"
                @update:model-value="emit('update:customTitle', $event)"
              />
            </el-form-item>
          </div>

          <div
            v-for="group in groupedFields"
            :key="group.name"
            class="doc-form-group"
          >
            <div class="doc-form-group-title">{{ group.name }}</div>
            <div class="doc-form-fields-grid">
              <el-form-item
                v-for="field in group.fields"
                :key="field.key"
                :prop="field.key"
                :label="field.label"
                :class="{ 'is-full-span': isFullSpanField(field) }"
              >
                <el-input
                  v-if="field.type === 'textarea'"
                  v-model="modelValue[field.key]"
                  type="textarea"
                  :rows="3"
                  :maxlength="field.maxLength || 2000"
                  show-word-limit
                  :placeholder="field.placeholder || `请输入${field.label}`"
                />

                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="modelValue[field.key]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '请选择日期'"
                  style="width: 100%"
                  clearable
                />

                <el-select
                  v-else-if="field.type === 'select'"
                  v-model="modelValue[field.key]"
                  :placeholder="field.placeholder || `请选择${field.label}`"
                  style="width: 100%"
                  clearable
                >
                  <el-option
                    v-for="opt in (field.options || [])"
                    :key="opt"
                    :label="opt"
                    :value="opt"
                  />
                </el-select>

                <el-input
                  v-else-if="field.type === 'money' || field.type === 'number'"
                  v-model="modelValue[field.key]"
                  inputmode="decimal"
                  :maxlength="field.maxLength || 32"
                  :placeholder="field.placeholder || `请输入${field.label}`"
                  clearable
                >
                  <template v-if="resolveUnit(field)" #append>
                    {{ resolveUnit(field) }}
                  </template>
                </el-input>

                <el-input
                  v-else
                  v-model="modelValue[field.key]"
                  :maxlength="field.maxLength || (field.type === 'idcard' ? 18 : field.type === 'phone' ? 20 : 200)"
                  :placeholder="field.placeholder || `请输入${field.label}`"
                  clearable
                />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>

      <div class="doc-form-footer">
        <span class="doc-form-hint">带 * 为必填要素，未填非必填项将留空占位</span>
        <el-button
          v-hasPermi="['lawyer:document:add']"
          type="primary"
          :loading="generating"
          @click="handleSubmit"
        >
          <el-icon v-if="!generating"><Promotion /></el-icon>
          <span>{{ generating ? 'AI 正在起草中...' : 'AI 生成文书草稿' }}</span>
        </el-button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, MagicStick, Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  template: {
    type: Object,
    default: null
  },
  modelValue: {
    type: Object,
    default: () => ({})
  },
  customTitle: {
    type: String,
    default: ''
  },
  rules: {
    type: Object,
    default: () => ({})
  },
  generating: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'update:customTitle', 'submit', 'reset'])

const formRef = ref(null)

const DEMO_DATA = {
  labor_arbitration: {
    applicantName: '张伟',
    applicantGender: '男',
    applicantBirth: '1994-06-15',
    applicantIdCard: '310104199406152816',
    applicantPhone: '13812345678',
    applicantAddress: '上海市徐汇区漕溪北路 398 号 502 室',
    employerName: '上海启明智联科技有限公司',
    employerCreditCode: '91310104MA1FR8892X',
    employerLegalRep: '李强',
    employerAddress: '上海市浦东新区张江高科技园区科苑路 88 号 3 幢',
    entryDate: '2022-03-01',
    leaveDate: '2026-10-31',
    position: '高级前端开发工程师',
    monthlySalary: '18000.00',
    arbitrationCommission: '上海市浦东新区劳动人事争议仲裁委员会',
    claims: '1. 请求裁决被申请人支付违法解除劳动合同赔偿金人民币 180000 元；\n2. 请求裁决被申请人支付 2026 年 10 月未发工资 18000 元及未休年休假折算工资 4137.93 元。',
    factsAndReasons: '申请人于 2022 年 3 月 1 日入职被申请人处担任高级前端开发工程师，双方签订了期限至 2027 年 2 月 28 日的书面劳动合同，月平均工资为 18000 元。2026 年 10 月 31 日，被申请人以“部门架构调整”为由单方口头通知解除劳动合同，未提前三十日书面通知，亦未支付任何经济补偿或赔偿金，且拖欠 2026 年 10 月工资。被申请人的行为已构成违法解除劳动合同，现依法申请仲裁。',
    evidenceList: '1. 劳动合同书复印件；\n2. 2025 年 11 月至 2026 年 9 月银行工资流水；\n3. 解除劳动关系通知及微信沟通记录截图；\n4. 社会保险个人权益记录。'
  },
  payment_demand: {
    lawFirmName: '北京德衡（上海）律师事务所',
    lawyerName: '陈明哲',
    lawyerPhone: '13901887766',
    clientName: '上海华信精密机械有限公司',
    debtorName: '苏州恒远自动化设备有限公司',
    debtorAddress: '江苏省苏州市工业园区星湖街 328 号创意产业园 4 号楼',
    debtBasis: '委托人与贵司于 2025 年 8 月 12 日签订《精密零部件采购合同》（合同编号：HX-2025-0812），委托人已按约完成全部货物交付并经贵司签收验收，贵司仅支付首付款，尚余货款长期拖欠未付。',
    principalAmount: '245000.00',
    interestOrPenalty: '按逾期付款日万分之三计算违约金，暂计至发函日约 18500 元',
    deadlineDays: '7',
    paymentAccount: '户名：上海华信精密机械有限公司\n开户行：中国工商银行上海市张江支行\n账号：1001234509006789012',
    issueDate: '2026-11-20'
  },
  loan_agreement: {
    lenderName: '王芳',
    lenderIdCard: '320102198809201428',
    borrowerName: '刘洋',
    borrowerIdCard: '32010619900412351X',
    borrowerPhone: '13651234567',
    borrowerAddress: '江苏省南京市鼓楼区中山北路 212 号 1201 室',
    amount: '100000.00',
    loanDate: '2026-11-21',
    repaymentDate: '2027-11-20',
    annualInterestRate: '4.5',
    paymentMethod: '银行转账',
    lenderAccount: '户名：王芳\n开户行：招商银行南京分行营业部\n账号：6225882100001234',
    purpose: '个人店铺装修及流动资金周转',
    guarantorName: '赵磊',
    disputeCourt: '出借人住所地人民法院'
  },
  lease_termination: {
    senderRole: '承租人',
    landlordName: '周建国',
    landlordPhone: '13701928374',
    tenantName: '林晓雯',
    tenantPhone: '15821098765',
    propertyAddress: '北京市朝阳区望京西园四区 412 号楼 2 单元 901 室',
    contractSignDate: '2025-05-01',
    leaseStart: '2025-05-01',
    leaseEnd: '2027-04-30',
    terminationReason: '因本人工作调动需赴外地长期任职，无法继续承租上述房屋，已提前三十日与出租人沟通协商提前解除房屋租赁合同。',
    terminationDate: '2026-12-20',
    depositAmount: '8500.00',
    rentSettlement: '租金结算至 2026 年 12 月 20 日止；水、电、燃气、物业等费用按实际抄表结清，出租人应于房屋交接验收合格当日无息退还押金人民币 8500 元。',
    noticeDate: '2026-11-20'
  }
}

const hasDemoData = computed(() => Boolean(props.template && DEMO_DATA[props.template.code]))

const groupedFields = computed(() => {
  const fields = props.template?.fields || []
  const groupMap = new Map()
  for (const field of fields) {
    const groupName = field.group || '基本信息'
    if (!groupMap.has(groupName)) {
      groupMap.set(groupName, [])
    }
    groupMap.get(groupName).push(field)
  }
  return Array.from(groupMap.entries()).map(([name, list]) => ({
    name,
    fields: list
  }))
})

function isFullSpanField(field) {
  return field.type === 'textarea' || /address|commission|basis|purpose|account|reason/i.test(field.key)
}

function resolveUnit(field) {
  if (field.type === 'money') return '元'
  if (field.label?.includes('(%)') || field.label?.includes('利率')) return '%'
  if (field.label?.includes('(天)') || field.label?.includes('期限')) return '天'
  return ''
}

async function handleFillDemo() {
  if (!props.template) return
  const demo = DEMO_DATA[props.template.code]
  if (!demo) return
  const nextModel = { ...props.modelValue }
  for (const field of props.template.fields || []) {
    if (demo[field.key] !== undefined) {
      nextModel[field.key] = demo[field.key]
    }
  }
  emit('update:modelValue', nextModel)
  await nextTick()
  formRef.value?.clearValidate()
  ElMessage.success('已填入演示要素数据，可直接点击生成')
}

async function handleReset() {
  emit('reset')
  await nextTick()
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('submit')
  } catch {
    ElMessage.warning('请检查并完善标红的必填或格式要素')
  }
}

function clearValidate() {
  formRef.value?.clearValidate()
}

defineExpose({ clearValidate })
</script>
