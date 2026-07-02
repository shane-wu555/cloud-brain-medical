<template>
  <div class="wks">
    <!-- ── Navbar ── -->
    <header class="wks-nav">
      <div class="wks-nav__brand">
        <span class="wks-nav__logo">+</span>
        <span class="wks-nav__title">门诊</span>
      </div>
      <div class="wks-nav__right">
        <span class="wks-nav__info">{{ doctorDept }}{{ doctorDept ? ' ｜ ' : '' }}{{ auth.user?.name }} 医师</span>
        <span class="wks-nav__date">{{ today }} {{ dayOfWeek }}</span>
        <el-button size="small" text @click="logout" style="color:rgba(255,255,255,0.85)">退出</el-button>
      </div>
    </header>

    <!-- ── Body: sidebar | main | AI ── -->
    <div class="wks-body">

      <!-- Left: queue sidebar -->
      <aside class="wks-sidebar">
        <div class="sidebar-hdr">
          <span>候诊队列</span>
          <el-button :loading="refreshing" size="small" text @click="refreshQueue" style="font-size:16px">↺</el-button>
        </div>
        <div class="sidebar-search-wrap">
          <el-input v-model="queueKeyword" clearable size="small" placeholder="搜索姓名/就诊号" />
        </div>

        <div class="sidebar-tabs">
          <button :class="['stab', queueTab === 'all' && 'stab--active']" @click="queueTab = 'all'">
            全部 {{ appointments.length }}
          </button>
          <button :class="['stab', queueTab === 'waiting' && 'stab--active']" @click="queueTab = 'waiting'">
            待诊 {{ waitingCount }}
          </button>
          <button :class="['stab', queueTab === 'skipped' && 'stab--active']" @click="queueTab = 'skipped'">
            过号 {{ skippedCount }}
          </button>
          <button :class="['stab', queueTab === 'finished' && 'stab--active']" @click="queueTab = 'finished'">
            已接诊 {{ finishedCount }}
          </button>
        </div>

        <div class="queue-list">
          <div
            v-for="item in filteredQueue"
            :key="item.id"
            :class="['qcard', current?.id === item.id && 'qcard--active']"
            @click="selectAppointment(item)"
          >
            <div class="qcard__top">
              <span class="qcard__num">{{ item.queueNumber }}</span>
              <span class="qcard__name">{{ item.patientName }}</span>
              <el-tag :type="statusTagType(item.status)" size="small" effect="light">{{ statusLabel(item.status) }}</el-tag>
            </div>
            <div class="qcard__sub">
              <span>{{ item.businessNo }}</span>
              <span>{{ formatTime(item.startTime) }}</span>
            </div>
            <div class="qcard__ops" @click.stop>
              <el-button v-if="['WAITING','REVISIT_WAITING'].includes(item.status)" size="small" type="primary" link @click="call(item)">叫号</el-button>
              <el-button v-if="item.status === 'CALLED'" size="small" type="success" link @click="start(item)">接诊</el-button>
              <el-button v-if="['WAITING','CALLED','REVISIT_WAITING'].includes(item.status)" size="small" link @click="skip(item)">过号</el-button>
            </div>
          </div>
          <div v-if="!filteredQueue.length" class="queue-empty">暂无患者</div>
        </div>

        <div class="sidebar-footer">共 {{ appointments.length }} 人</div>
      </aside>

      <!-- Center: main content -->
      <main class="wks-main">
        <div v-if="!current" class="main-empty">
          <el-empty description="请从左侧选择患者开始接诊" :image-size="90" />
        </div>

        <template v-else>
          <!-- Patient header -->
          <div class="patient-hdr">
            <div class="pat-avatar">{{ current.patientName.slice(-1) }}</div>
            <div class="pat-info">
              <div class="pat-row1">
                <span class="pat-name">{{ current.patientName }}</span>
                <el-tag type="primary" size="small" effect="plain">
                  {{ current.source === 'ONLINE' ? '网上挂号' : '现场挂号' }}
                </el-tag>
              </div>
              <div class="pat-row2">
                <span><em>就诊科室</em>{{ current.departmentName }}</span>
                <span><em>就诊号</em>{{ current.businessNo }}</span>
                <span><em>就诊时间</em>{{ current.visitDate }}</span>
              </div>
            </div>
            <div v-if="recordForm.allergyHistory" class="pat-allergy">
              <span class="pat-allergy__lbl">过敏史</span>
              <el-tag type="danger" size="small">{{ recordForm.allergyHistory }}</el-tag>
            </div>
          </div>

          <!-- Tab bar -->
          <div class="main-tabs">
            <button
              v-for="t in mainTabs" :key="t.key"
              :class="['mtab', mainTab === t.key && 'mtab--active']"
              @click="mainTab = t.key"
            >{{ t.label }}</button>
          </div>

          <!-- 病历书写 -->
          <div v-show="mainTab === 'record'" class="main-content">
            <div class="med-doc">
              <div class="med-doc__title">门&ensp;诊&ensp;病&ensp;历</div>
              <div class="med-doc__rule-thick"></div>

              <div class="med-doc__row med-doc__info-row">
                <span class="med-doc__lbl">姓名</span>
                <span class="med-doc__staticval">{{ current.patientName }}</span>
                <span class="med-doc__lbl">科室</span>
                <span class="med-doc__staticval">{{ current.departmentName }}</span>
                <span class="med-doc__lbl">日期</span>
                <span class="med-doc__staticval med-doc__staticval--flex">{{ current.visitDate || today }}</span>
                <span class="med-doc__lbl">就诊号</span>
                <span class="med-doc__staticval med-doc__staticval--flex">{{ current.businessNo }}</span>
              </div>

              <div class="med-doc__row med-doc__row--top">
                <span class="med-doc__lbl med-doc__lbl--w">主&emsp;诉</span>
                <textarea class="med-doc__area" v-model="recordForm.chiefComplaint" rows="2" :readonly="isRecordLocked" />
              </div>

              <div class="med-doc__row">
                <span class="med-doc__lbl med-doc__lbl--w">过去史</span>
                <input class="med-doc__input" v-model="recordForm.pastHistory" style="flex:2" :readonly="isRecordLocked" />
                <span class="med-doc__lbl" style="margin-left:14px;white-space:nowrap">过敏史</span>
                <input class="med-doc__input" v-model="recordForm.allergyHistory" style="flex:1" :readonly="isRecordLocked" />
              </div>

              <div class="med-doc__row med-doc__row--top">
                <span class="med-doc__lbl med-doc__lbl--w">现病史</span>
                <textarea class="med-doc__area" v-model="recordForm.presentIllness" rows="2" :readonly="isRecordLocked" />
              </div>

              <!-- 辅助检查：已确认报告 + 已开医嘱，始终显示 -->
              <div class="med-doc__row med-doc__row--top">
                <span class="med-doc__lbl med-doc__lbl--w">辅助检查</span>
                <div class="med-doc__checklist">
                  <div v-for="r in formalReports" :key="r.id" class="med-doc__check-item med-doc__check-item--done">
                    <span class="check-tag">已回报</span>
                    <span>{{ r.reportType }}：{{ r.conclusion }}</span>
                  </div>
                  <div v-for="o in currentOrders" :key="o.id" class="med-doc__check-item"
                       :class="o.status === 'COMPLETED' ? 'med-doc__check-item--done' : 'med-doc__check-item--pending'">
                    <span class="check-tag">{{ { CHECK:'检查', LAB:'检验', DISPOSAL:'处置' }[o.orderType] ?? o.orderType }}</span>
                    <span>{{ o.itemName }}</span>
                    <span v-if="o.status === 'COMPLETED'" class="check-done-mark">✓</span>
                  </div>
                  <div v-if="!formalReports.length && !currentOrders.length" class="med-doc__check-placeholder">暂无检查/检验记录，可在「医嘱开立」页开单</div>
                </div>
              </div>

              <div class="med-doc__row med-doc__row--top">
                <span class="med-doc__lbl med-doc__lbl--w">诊&emsp;断</span>
                <textarea class="med-doc__area med-doc__area--bold" v-model="recordForm.diagnosis" rows="2" :readonly="isRecordLocked" />
              </div>

              <div class="med-doc__row med-doc__row--top">
                <span class="med-doc__lbl med-doc__lbl--w">建&emsp;议</span>
                <textarea class="med-doc__area" v-model="recordForm.treatmentPlan" rows="2" :readonly="isRecordLocked" />
              </div>

              <div class="med-doc__row med-doc__row--top">
                <span class="med-doc__lbl med-doc__lbl--w">处&emsp;方</span>
                <div class="med-doc__prescriptions">
                  <div v-for="rx in prescriptions" :key="rx.id" class="med-doc__rx">
                    <span class="med-doc__rx-status">{{ rxStatusLabel(rx.status) }}</span>
                    <span v-for="item in rx.items" :key="item.id" class="med-doc__rx-item">
                      {{ item.drugName }} {{ item.dosage }} {{ item.usage }} {{ item.frequency }} {{ item.days }}天 {{ item.quantity }}份
                    </span>
                  </div>
                  <div v-if="!prescriptions.length" class="med-doc__rx-placeholder">暂无处方，可在「处方」页开立</div>
                </div>
              </div>

              <div class="med-doc__signature-row">
                <span>日期：{{ current.visitDate || today }}</span>
                <span>医生签名：{{ current.doctorName || auth.user?.name || '' }}</span>
              </div>

              <div class="med-doc__rule"></div>
              <div class="med-doc__footer">
                <el-button size="small" @click="printRecord">病历打印</el-button>
                <div style="display:flex;gap:8px">
                  <el-button v-if="!isRecordLocked" type="primary" size="small" @click="saveRecord">保存病历</el-button>
                  <el-button v-if="current.status !== 'FINISHED'" type="success" size="small" @click="finishVisit">完成接诊</el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 处方 -->
          <div v-show="mainTab === 'rx'" class="main-content">
            <!-- AI 建议 -->
            <div v-if="rxSuggestions.length" class="rx-ai-hint">
              <div class="rx-ai-hint__hdr">
                <span>用药建议</span>
                <el-button size="small" type="primary" @click="createRxFromSuggestion">采纳为待缴费处方</el-button>
              </div>
              <div v-for="s in rxSuggestions" :key="s.drugName" class="rx-ai-hint__item">
                <strong>{{ s.drugName }}</strong>
                <span class="muted">{{ s.specification ? `${s.specification} · ` : '' }}{{ s.dosage }} · {{ s.usage }} · {{ s.frequency }} · {{ s.days }}天{{ s.quantity ? ` · ${s.quantity}份` : '' }}</span>
                <span v-if="rxWarnings.length" class="rx-warn">⚠ {{ rxWarnings.join('；') }}</span>
              </div>
            </div>

            <!-- 已开处方（紧凑） -->
            <div class="rx-section-hdr">
              <span>本次处方</span>
              <el-button size="small" text @click="loadPrescriptions">↺ 刷新</el-button>
            </div>
            <div v-if="!prescriptions.length" class="rx-empty">暂无处方记录</div>
            <div v-for="rx in prescriptions" :key="rx.id" class="rx-card rx-card--compact">
              <div class="rx-card__hdr">
                <span class="rx-card__no">{{ rx.prescriptionNo }}</span>
                <el-tag :type="rxStatusType(rx.status)" size="small" effect="light">{{ rxStatusLabel(rx.status) }}</el-tag>
                <span class="rx-card__amount">¥{{ rx.totalAmount?.toFixed(2) }}</span>
              </div>
              <div class="rx-card__body">
                <div v-for="item in rx.items" :key="item.id" class="rx-card__item">
                  {{ item.drugName }} · {{ item.dosage }} · {{ item.usage }} · {{ item.frequency }} · {{ item.days }}天 · {{ item.quantity }}份
                </div>
              </div>
            </div>

            <!-- 药品目录 -->
            <div class="rx-section-hdr" style="margin-top:16px">
              <span>手动开处方</span>
            </div>
            <el-input v-model="drugKeyword" clearable placeholder="搜索药品名称或规格" style="margin-bottom:10px" />
            <el-table :data="filteredDrugs" size="small" :max-height="220">
              <el-table-column prop="drugName" label="药品" />
              <el-table-column prop="specification" label="规格" width="120" />
              <el-table-column prop="unitPrice" label="单价(元)" width="90" />
              <el-table-column label="" width="60">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="addDrugToRx(row)">加入</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="manualRxItems.length" class="rx-manual" style="margin-top:12px">
              <div v-for="(item, idx) in manualRxItems" :key="idx" class="rx-manual-row">
                <span class="rx-manual-name">{{ item.drugName }}</span>
                <el-input v-model="item.dosage" placeholder="用量" size="small" style="width:80px" />
                <el-input v-model="item.usage" placeholder="用法" size="small" style="width:70px" />
                <el-input v-model="item.frequency" placeholder="频次" size="small" style="width:80px" />
                <el-input-number v-model="item.days" :min="1" :max="30" size="small" style="width:70px" controls-position="right" />
                <span class="muted">天</span>
                <el-input-number v-model="item.quantity" :min="1" :max="999" size="small" style="width:78px" controls-position="right" />
                <span class="muted">份</span>
                <el-button size="small" link type="danger" @click="manualRxItems.splice(idx,1)">✕</el-button>
              </div>
              <el-button type="primary" size="small" style="margin-top:10px" @click="submitManualRx">提交处方</el-button>
            </div>
          </div>

          <!-- 医嘱开立 -->
          <div v-show="mainTab === 'orders'" class="main-content">
            <div class="toolbar">
              <el-input v-model="itemKeyword" clearable placeholder="搜索项目名称或编码" style="width:200px" />
              <el-select v-model="itemCategory" clearable placeholder="项目分类" style="width:130px">
                <el-option label="检查" value="CHECK" />
                <el-option label="检验" value="LAB" />
                <el-option label="处置" value="DISPOSAL" />
              </el-select>
              <el-select v-model="orderUrgency" style="width:100px">
                <el-option label="常规" value="ROUTINE" />
                <el-option label="急诊" value="EMERGENCY" />
              </el-select>
              <el-button type="primary" :disabled="!selectedItems.length" @click="submitOrders">
                批量开单 {{ selectedItems.length }}
              </el-button>
            </div>
            <el-table :data="filteredItems" @selection-change="selectedItems = $event" row-key="code">
              <el-table-column type="selection" width="50" />
              <el-table-column label="" width="54">
                <template #default="{ row }">
                  <el-tag v-if="isAiRecommended(row)" size="small" type="success" effect="plain">推荐</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="code" label="编码" width="160" />
              <el-table-column prop="name" label="项目" min-width="120" />
              <el-table-column label="分类" width="80">
                <template #default="{ row }">{{ ({ CHECK:'检查', LAB:'检验', DISPOSAL:'处置' } as Record<string,string>)[row.category] ?? row.category }}</template>
              </el-table-column>
              <el-table-column prop="price" label="价格" width="90" />
            </el-table>
          </div>

          <!-- 已完成报告 -->
          <div v-show="mainTab === 'reports'" class="main-content">
            <el-table :data="formalReports" row-class-name="report-row">
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  {{ ({ CHECK:'检查', LAB:'检验', DISPOSAL:'处置' } as Record<string,string>)[row.reportType] ?? row.reportType }}
                </template>
              </el-table-column>
              <el-table-column prop="conclusion" label="结论" />
              <el-table-column label="" width="80" align="right">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="viewReport(row)">查看报告</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 报告详情弹窗 -->
          <el-dialog v-model="reportDialogVisible" title="报告详情" width="780px" :destroy-on-close="true">
            <div v-if="selectedReport" class="med-report med-report--readonly">
              <div class="med-report__hospital">智慧云脑诊疗中心</div>
              <div class="med-report__title">{{ reportDocumentTitle(selectedReport.reportType) }}</div>
              <div class="med-report__rule-thick"></div>

              <div class="med-report__info-grid">
                <div class="rinfo-cell">
                  <em>姓　名</em><span>{{ current?.patientName || selectedReportOrder?.patientName || '' }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>登记号</em><span>{{ current?.businessNo || selectedReportOrder?.id?.slice(0, 10).toUpperCase() || '' }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>{{ selectedReport.reportType === 'LAB' ? '检验项目' : '检查项目' }}</em><span>{{ selectedReportOrder?.itemName || reportTypeLabel(selectedReport.reportType) }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>{{ selectedReport.reportType === 'LAB' ? '样本类型' : '检查部位' }}</em><span>{{ selectedReportOrder?.bodyPart || '—' }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>{{ selectedReport.reportType === 'LAB' ? '检验日期' : '检查日期' }}</em><span>{{ selectedReport.confirmedAt ? selectedReport.confirmedAt.slice(0,10) : today }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>报告医生</em><span>{{ selectedReport.confirmedBy || '' }}</span>
                </div>
              </div>

              <div class="med-report__clinical">
                <em>临床诊断 / 目的</em>
                <span>{{ selectedReportOrder?.purpose || recordForm.diagnosis || '待定' }}</span>
              </div>

              <div class="med-report__rule"></div>

              <table v-if="selectedReport.reportType === 'LAB' && selectedLabResults.length" class="lab-report-table">
                <thead>
                  <tr>
                    <th>项目名称</th>
                    <th>结果</th>
                    <th>单位</th>
                    <th>参考范围</th>
                    <th>提示</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in selectedLabResults" :key="item.id || item.itemCode">
                    <td>{{ item.itemName }}</td>
                    <td class="lab-report-table__value">{{ item.resultValue }}</td>
                    <td>{{ item.unit || '—' }}</td>
                    <td>{{ item.referenceRange || '—' }}</td>
                    <td>{{ abnormalLabel(item.abnormalFlag) }}</td>
                  </tr>
                </tbody>
              </table>

              <div v-if="selectedReport.reportType !== 'LAB'" class="med-report__section">
                <div class="med-report__section-lbl">检查所见 / 执行过程</div>
                <div class="med-report__area med-report__area--readonly">{{ selectedReport.findings || '—' }}</div>
              </div>

              <div class="med-report__rule"></div>

              <div class="med-report__section">
                <div class="med-report__section-lbl med-report__section-lbl--emphasis">结　论 / 结　果</div>
                <div class="med-report__area med-report__area--readonly med-report__area--emphasis">{{ selectedReport.conclusion || '—' }}</div>
              </div>

              <div class="med-report__section" style="margin-top:14px">
                <div class="med-report__section-lbl">后续建议</div>
                <div class="med-report__area med-report__area--readonly med-report__area--single">{{ selectedReport.advice || '—' }}</div>
              </div>

              <div class="med-report__rule"></div>

              <div class="med-report__sig-footer">
                <div class="sig-block">
                  <span><span class="sig-label">报告医师：</span><span class="sig-name-print">{{ selectedReport.confirmedBy || '' }}</span></span>
                  <span><span class="sig-label">医师签名：</span><span class="sig-cursive">{{ selectedReport.confirmedBy || '' }}</span></span>
                  <span><span class="sig-label">报告日期：</span><span class="sig-date">{{ selectedReport.confirmedAt ? selectedReport.confirmedAt.slice(0,10) : today }}</span></span>
                </div>
                <div class="stamp-block">
                  <div class="stamp-circle stamp-circle--published">
                    <span>已审核</span>
                  </div>
                </div>
              </div>

              <div class="med-report__notice">
                注：本报告由检查/检验医师审核后发布，仅供临床医师参考，如有疑义请及时与医技科室联系。
              </div>
            </div>
            <template #footer>
              <el-button @click="reportDialogVisible = false">关闭</el-button>
              <el-button type="primary" @click="applyReportToRecord(selectedReport!)">导入辅助检查</el-button>
            </template>
          </el-dialog>

          <!-- 历史病历 -->
          <div v-show="mainTab === 'history'" class="main-content">
            <div class="toolbar">
              <el-input v-model="historyReason" placeholder="历史病历访问原因" style="width:260px" />
              <el-button @click="loadHistory">查看历史</el-button>
            </div>
            <el-table :data="historyRecords">
              <el-table-column prop="visitDate" label="日期" width="110" />
              <el-table-column prop="patientName" label="患者" width="110" />
              <el-table-column prop="chiefComplaint" label="主诉" />
              <el-table-column prop="diagnosis" label="诊断" />
            </el-table>
          </div>
        </template>
      </main>

      <!-- Right: AI panel -->
      <aside class="wks-ai">
        <el-card shadow="never" class="ai-card">
          <template #header>
            <div class="ai-header">
              <span>AI 临床助手</span>
              <el-tag :type="aiFallback ? 'warning' : aiModel ? 'success' : 'info'" effect="plain" size="small">
                {{ aiModelLabel }}
              </el-tag>
            </div>
          </template>
          <div class="ai-messages" v-loading="aiLoading" element-loading-text="AI 分析中…">
            <div v-for="message in aiMessages" :key="message.id" :class="['ai-message', `ai-message--${message.kind}`]">
              <span class="ai-msg-label">{{ message.label }}</span>
              <p>{{ message.content }}</p>
              <div class="ai-msg-actions">
                <el-button v-if="message.kind === 'diagnosis'" size="small" type="primary" @click="applyDiagnosis(message)">填入诊断</el-button>
                <el-button v-if="message.kind === 'medication'" size="small" type="success" @click="applyMedication(message)">采纳为处方</el-button>
                <el-button v-if="message.kind === 'exam'" size="small" @click="applyExamItems(message)">推荐检查置顶</el-button>
                <el-button v-if="message.kind === 'advice'" size="small" @click="applyAdvice(message)">填入建议</el-button>
              </div>
            </div>
            <el-empty v-if="!aiLoading && !aiMessages.length" description="生成建议后在此显示" :image-size="60" />
          </div>

          <el-input v-model="aiPrompt" type="textarea" :rows="3" placeholder="结合本次病历给出鉴别诊断、检查建议和风险提醒" />
          <el-button type="primary" class="full ai-action" :disabled="!current" :loading="aiLoading" @click="generateAssistance">
            {{ aiLoading ? 'AI 分析中…' : '生成辅助建议' }}
          </el-button>

        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { callAppointment, getTodayQueue, skipAppointment, startAppointment, updateAppointmentStatus, type Appointment } from '../../api/appointment';
import { getMedicalRecords, getPatientHistory, initDoctorRecord, writeDoctorNote, type MedicalRecord } from '../../api/medical-record';
import { getClinicalAssistance, type ClinicalSuggestion } from '../../api/ai';
import { createMedicalOrder, getLabResults, getMedicalItems, getMedicalOrders, getReports, type LaboratoryResultItem, type MedicalItem, type MedicalOrder, type MedicalReport } from '../../api/medical-order';
import { createPrescription, getDrugs, getPrescriptions, type Drug, type Prescription } from '../../api/pharmacy';

const router = useRouter();
const auth = useAuthStore();

const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;

const appointments = ref<Appointment[]>([]);
const records = ref<MedicalRecord[]>([]);
const historyRecords = ref<MedicalRecord[]>([]);
const formalReports = ref<MedicalReport[]>([]);
const medicalItems = ref<MedicalItem[]>([]);
const selectedItems = ref<MedicalItem[]>([]);
const current = ref<Appointment>();
const drugs = ref<Drug[]>([]);

const queueKeyword = ref('');
const queueTab = ref('all');
const mainTab = ref('record');
const itemKeyword = ref('');
const itemCategory = ref('');
const orderUrgency = ref('ROUTINE');
const historyReason = ref('复诊关联病史查阅');
const recordVersion = ref<number>();
const currentRecordId = ref<string>();
const dirty = ref(false);
const refreshing = ref(false);
let loadingRecord = false;

const mainTabs = [
  { key: 'record', label: '病历书写' },
  { key: 'rx', label: '处方' },
  { key: 'orders', label: '检查申请' },
  { key: 'reports', label: '已完成报告' },
  { key: 'history', label: '历史病历' },
];

const currentOrders = ref<MedicalOrder[]>([]);
const prescriptions = ref<Prescription[]>([]);
const reportDialogVisible = ref(false);
const selectedReport = ref<MedicalReport>();
const selectedLabResults = ref<LaboratoryResultItem[]>([]);
const drugKeyword = ref('');
type RxDraftItem = {
  drugId: string;
  drugName: string;
  specification?: string;
  quantity: number;
  dosage: string;
  usage: string;
  frequency: string;
  days: number;
  note?: string;
};

type AiDrugSuggestion = {
  drugName: string;
  specification?: string;
  dosage: string;
  usage: string;
  frequency: string;
  days: number;
  quantity?: number;
  note?: string;
};

const manualRxItems = ref<RxDraftItem[]>([]);

const aiPrompt = ref('结合当前病历给出鉴别诊断方向和进一步检查建议');
const aiMessages = ref<Array<ClinicalSuggestion & { id: string }>>([]);
const aiLoading = ref(false);
const aiModel = ref('');
const aiFallback = ref(false);
const diagnosisSource = ref<'HUMAN' | 'AI'>('HUMAN');
const diagnosisAiRecordId = ref<string>();
const rxAiRecordId = ref<string>();
const rxSuggestions = ref<AiDrugSuggestion[]>([]);
const rxWarnings = ref<string[]>([]);
const aiRecommendedNames = ref<string[]>([]);

const recordForm = reactive({
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  allergyHistory: '',
  diagnosis: '',
  treatmentPlan: ''
});

const doctorDept = computed(() => appointments.value[0]?.departmentName ?? '');
const isRecordLocked = computed(() => current.value?.status === 'FINISHED');

const ACTIVE_STATUSES = ['WAITING', 'CALLED', 'IN_VISIT', 'REVISIT_WAITING'];
const waitingCount = computed(() => appointments.value.filter(a => ACTIVE_STATUSES.includes(a.status)).length);
// 过号 = 仍在等待但已过号至少一次（missedCount > 0）
const skippedCount = computed(() => appointments.value.filter(a => ACTIVE_STATUSES.includes(a.status) && a.missedCount > 0).length);
const finishedCount = computed(() => appointments.value.filter(a => a.status === 'FINISHED').length);
const selectedReportOrder = computed(() => {
  if (!selectedReport.value) return undefined;
  const reportOrderId = selectedReport.value.medicalOrderId ?? selectedReport.value.orderId;
  return currentOrders.value.find(order => order.id === reportOrderId);
});

const filteredQueue = computed(() => {
  let list = appointments.value;
  if (queueTab.value === 'waiting') list = list.filter(a => ACTIVE_STATUSES.includes(a.status));
  else if (queueTab.value === 'skipped') list = list.filter(a => ACTIVE_STATUSES.includes(a.status) && a.missedCount > 0);
  else if (queueTab.value === 'finished') list = list.filter(a => a.status === 'FINISHED');
  const kw = queueKeyword.value.trim().toLowerCase();
  return kw ? list.filter(a => (a.patientName + a.businessNo).toLowerCase().includes(kw)) : list;
});

function isAiRecommended(item: MedicalItem) {
  return aiRecommendedNames.value.some(name => {
    const a = item.name.trim();
    const b = name.trim();
    if (a === b) return true;
    if (a.toLowerCase() === b.toLowerCase()) return true;
    // 包含匹配兜底（应对 LLM 轻微改写）
    return a.includes(b) || b.includes(a);
  });
}

const filteredItems = computed(() => {
  const list = medicalItems.value.filter(item =>
    (!itemCategory.value || item.category === itemCategory.value) &&
    (!itemKeyword.value || `${item.code}${item.name}`.toLowerCase().includes(itemKeyword.value.toLowerCase()))
  );
  if (!aiRecommendedNames.value.length) return list;
  return [...list].sort((a, b) => {
    const aRec = isAiRecommended(a);
    const bRec = isAiRecommended(b);
    if (aRec === bRec) return 0;
    return aRec ? -1 : 1;
  });
});

const filteredDrugs = computed(() => {
  const kw = drugKeyword.value.trim().toLowerCase();
  return kw ? drugs.value.filter(d => (d.drugName + d.specification).toLowerCase().includes(kw)) : drugs.value;
});

function parseDailyTimes(frequency?: string) {
  const text = (frequency ?? '').toLowerCase();
  if (!text) return 1;
  if (/tid|3|三/.test(text)) return 3;
  if (/bid|2|二|两/.test(text)) return 2;
  if (/qid|4|四/.test(text)) return 4;
  if (/隔日/.test(text)) return 0.5;
  return 1;
}

function parseDoseUnits(dosage?: string) {
  const matched = (dosage ?? '').match(/(\d+(?:\.\d+)?)\s*(片|粒|袋|支|瓶|丸|枚|贴)/);
  return matched ? Number(matched[1]) : undefined;
}

function parsePackageUnits(specification?: string) {
  const matched = (specification ?? '').match(/[x×*]\s*(\d+)\s*(片|粒|袋|支|瓶|丸|枚|贴)/i);
  return matched ? Number(matched[1]) : undefined;
}

function estimateRxQuantity(drug: Drug, suggestion: Pick<AiDrugSuggestion, 'dosage' | 'frequency' | 'days'>) {
  const days = Math.max(1, Number(suggestion.days) || 1);
  const dailyTimes = parseDailyTimes(suggestion.frequency);
  const doseUnits = parseDoseUnits(suggestion.dosage);
  const packageUnits = parsePackageUnits(drug.specification);
  if (doseUnits && packageUnits) {
    return Math.max(1, Math.ceil((doseUnits * dailyTimes * days) / packageUnits));
  }
  if (/注射|针|输液/.test(drug.drugName) || /ml/i.test(drug.specification)) {
    return Math.max(1, Math.ceil(dailyTimes * days));
  }
  return 1;
}

function normalizeAiRxSuggestion(suggestion: AiDrugSuggestion, drug: Drug): AiDrugSuggestion {
  const quantity = suggestion.quantity && suggestion.quantity > 0
    ? Math.ceil(suggestion.quantity)
    : estimateRxQuantity(drug, suggestion);
  return {
    ...suggestion,
    drugName: drug.drugName,
    specification: drug.specification,
    quantity,
    note: suggestion.note ?? '',
  };
}

function matchDrugForSuggestion(suggestion: Pick<AiDrugSuggestion, 'drugName' | 'specification'>) {
  const name = suggestion.drugName ?? '';
  const spec = suggestion.specification ?? '';
  return drugs.value.find(d => d.drugName === name && (!spec || d.specification === spec))
    ?? drugs.value.find(d => d.drugName === name)
    ?? drugs.value.find(d => d.drugName.includes(name) || name.includes(d.drugName));
}

function rxStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING_PAYMENT: '待缴费', PAID: '已缴费', WAITING_DISPENSE: '待取药', DISPENSED: '已取药', RETURNED: '已退药', CANCELLED: '已取消'
  };
  return map[status] ?? status;
}
function rxStatusType(status: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'PENDING_PAYMENT') return 'warning';
  if (status === 'PAID' || status === 'WAITING_DISPENSE') return 'primary';
  if (status === 'DISPENSED') return 'success';
  if (status === 'RETURNED' || status === 'CANCELLED') return 'info';
  return '';
}

function addDrugToRx(drug: Drug) {
  if (manualRxItems.value.find(i => i.drugId === drug.id)) {
    ElMessage.warning('该药品已在处方中');
    return;
  }
  manualRxItems.value.push({
    drugId: drug.id,
    drugName: drug.drugName,
    specification: drug.specification,
    quantity: 1,
    dosage: '',
    usage: '口服',
    frequency: '每日三次',
    days: 7,
  });
}

async function loadPrescriptions() {
  if (!current.value) return;
  const [rxList, drugList] = await Promise.all([
    getPrescriptions({ patientId: current.value.patientId }),
    drugs.value.length ? Promise.resolve(drugs.value) : getDrugs()
  ]);
  prescriptions.value = rxList;
  if (drugList !== drugs.value) drugs.value = drugList;
}

async function submitManualRx() {
  if (!current.value || !manualRxItems.value.length) return;
  if (!recordForm.diagnosis) { ElMessage.warning('请先填写诊断'); return; }
  const items = manualRxItems.value.map(i => ({
    drugId: i.drugId, quantity: i.quantity,
    dosage: i.dosage, usage: i.usage, frequency: i.frequency, days: i.days
  }));
  await createPrescription({
    appointmentId: current.value.id,
    medicalRecordId: currentRecordId.value,
    patientId: current.value.patientId,
    patientName: current.value.patientName,
    diagnosis: recordForm.diagnosis,
    aiAdoptionStatus: 'HUMAN_ONLY',
    items
  });
  manualRxItems.value = [];
  ElMessage.success('处方已开立，待患者缴费');
  await loadPrescriptions();
  mainTab.value = 'record';
}

const aiModelLabel = computed(() => {
  if (!aiModel.value) return '未生成';
  return aiFallback.value ? `${aiModel.value} / Mock` : aiModel.value;
});

function statusLabel(status: string) {
  const map: Record<string, string> = {
    WAITING: '待诊', CALLED: '已叫号', IN_VISIT: '接诊中',
    FINISHED: '已接诊', REVISIT_WAITING: '待复诊'
  };
  return map[status] ?? status;
}

function statusTagType(status: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (['WAITING', 'REVISIT_WAITING'].includes(status)) return 'warning';
  if (status === 'CALLED') return 'primary';
  if (status === 'IN_VISIT') return 'success';
  if (status === 'FINISHED') return 'info';
  if (status === 'SKIPPED') return 'danger';
  return '';
}

function formatTime(t?: string) {
  if (!t) return '';
  return t.length >= 16 ? t.slice(11, 16) : t;
}

function formatDateTime(t?: string) {
  if (!t) return '';
  return t.length >= 16 ? t.slice(0, 16).replace('T', ' ') : t;
}

function reportTypeLabel(type?: string) {
  return ({ CHECK: '检查', LAB: '检验', DISPOSAL: '处置' } as Record<string, string>)[type ?? ''] ?? (type || '');
}

function reportDocumentTitle(type?: string) {
  return ({ CHECK: '影像检查报告', LAB: '临床检验报告', DISPOSAL: '处置记录报告' } as Record<string, string>)[type ?? ''] ?? '检查报告';
}

function abnormalLabel(flag?: string) {
  return ({ HIGH: '↑', LOW: '↓', ABNORMAL: '异常', NORMAL: '' } as Record<string, string>)[flag ?? ''] ?? (flag || '');
}

async function selectAppointment(row?: Appointment) {
  loadingRecord = true;
  current.value = row;
  Object.assign(recordForm, { chiefComplaint: row?.triageSummary ?? '', presentIllness: '', pastHistory: '', allergyHistory: '', diagnosis: '', treatmentPlan: '' });
  aiMessages.value = [];
  rxSuggestions.value = [];
  rxWarnings.value = [];
  aiRecommendedNames.value = [];
  diagnosisSource.value = 'HUMAN';
  diagnosisAiRecordId.value = undefined;
  rxAiRecordId.value = undefined;
  recordVersion.value = undefined;
  currentRecordId.value = undefined;
  historyRecords.value = [];
  currentOrders.value = [];
  prescriptions.value = [];
  formalReports.value = [];
  if (row) {
    const currentRecords = await getMedicalRecords({ appointmentId: row.id });
    const currentRecord = currentRecords[0];
    if (currentRecord) {
      recordForm.chiefComplaint = currentRecord.chiefComplaint || currentRecord.aiTriageSummary;
      recordForm.presentIllness = currentRecord.presentIllness ?? '';
      recordForm.pastHistory = currentRecord.pastHistory ?? '';
      recordForm.allergyHistory = currentRecord.allergyHistory ?? '';
      recordForm.diagnosis = currentRecord.diagnosis ?? currentRecord.preliminaryDiagnosis ?? '';
      recordForm.treatmentPlan = currentRecord.treatmentPlan ?? '';
      recordVersion.value = currentRecord.version;
      currentRecordId.value = currentRecord.id;
    }
    const [orders, rxList] = await Promise.all([
      getMedicalOrders({ appointmentId: row.id }),
      getPrescriptions({ patientId: row.patientId }),
    ]);
    currentOrders.value = orders;
    prescriptions.value = rxList;
    const orderIds = new Set(currentOrders.value.map(order => order.id));
    formalReports.value = (await getReports()).filter(report => orderIds.has(report.medicalOrderId ?? report.orderId ?? ''));
  }
  mainTab.value = 'record';
  loadingRecord = false;
  dirty.value = false;
}

async function loadQueue() {
  appointments.value = await getTodayQueue();
}

async function refreshQueue() {
  refreshing.value = true;
  await loadQueue();
  refreshing.value = false;
}

async function call(appointment: Appointment) {
  await callAppointment(appointment.id);
  ElMessage.success('已叫号');
  await loadQueue();
}

async function start(appointment: Appointment) {
  current.value = await startAppointment(appointment.id);
  await selectAppointment(current.value);
  ElMessage.success('已开始接诊');
  await loadQueue();
}

async function skip(appointment: Appointment) {
  await skipAppointment(appointment.id);
  ElMessage.success('已顺延');
  await loadQueue();
}

async function saveRecord() {
  if (!current.value) return;
  if (recordVersion.value === undefined) {
    const created = await initDoctorRecord({
      appointmentId: current.value.id,
      patientId: current.value.patientId,
      patientName: current.value.patientName,
      doctorId: current.value.doctorId,
      doctorName: current.value.doctorName,
      departmentName: current.value.departmentName,
      visitDate: current.value.visitDate,
      period: current.value.period ?? '',
      triageSummary: current.value.triageSummary ?? '',
      riskLevel: current.value.riskLevel ?? 'LOW'
    });
    recordVersion.value = created.version;
    currentRecordId.value = created.id;
  }
  const saved = await writeDoctorNote({
    appointmentId: current.value.id,
    version: recordVersion.value,
    chiefComplaint: recordForm.chiefComplaint,
    presentIllness: recordForm.presentIllness,
    pastHistory: recordForm.pastHistory,
    allergyHistory: recordForm.allergyHistory,
    physicalExamination: '',
    preliminaryDiagnosis: recordForm.diagnosis,
    treatmentPlan: recordForm.treatmentPlan,
    doctorRevisionNote: '',
    diagnosisCreatedByType: diagnosisSource.value,
    diagnosisAiRecordId: diagnosisAiRecordId.value
  });
  recordVersion.value = saved.version;
  currentRecordId.value = saved.id;
  dirty.value = false;
  ElMessage.success('病历已保存');
  records.value = await getMedicalRecords({});
}

async function finishVisit() {
  if (!current.value) return;
  if (dirty.value || recordVersion.value === undefined) {
    ElMessage.warning({ message: '请先保存当前病历再完成接诊', duration: 4000 });
    return;
  }
  try {
    await updateAppointmentStatus(current.value.id, 'FINISHED');
    ElMessage.success('接诊已结束');
    current.value = undefined;
    await loadQueue();
  } catch (e: any) {
    const message = e?.response?.data?.message ?? e?.message ?? '请检查网络或服务状态';
    ElMessage.error({ message: `完成接诊失败：${message}`, duration: 5000 });
  }
}

async function loadHistory() {
  if (!current.value) return;
  historyRecords.value = await getPatientHistory(current.value.patientId, current.value.id, historyReason.value);
}

async function submitOrders() {
  if (!current.value) return;
  const existingCodes = new Set(currentOrders.value.map(o => o.itemCode));
  const duplicates = selectedItems.value.filter(i => existingCodes.has(i.code));
  if (duplicates.length) {
    ElMessage.warning(`以下项目本次已开单，请勿重复：${duplicates.map(d => d.name).join('、')}`);
    return;
  }
  await Promise.all(selectedItems.value.map(item => createMedicalOrder({
    appointmentId: current.value!.id,
    patientId: current.value!.patientId,
    patientName: current.value!.patientName,
    orderType: item.category,
    itemCode: item.code,
    itemName: item.name,
    purpose: recordForm.diagnosis || recordForm.chiefComplaint,
    bodyPart: item.category === 'CHECK' ? '头部' : '',
    amount: item.price,
    urgency: orderUrgency.value
  })));
  selectedItems.value = [];
  ElMessage.success('医技申请已生成');
  if (current.value) {
    currentOrders.value = await getMedicalOrders({ appointmentId: current.value.id });
  }
}

async function generateAssistance() {
  if (!current.value) return;
  aiLoading.value = true;
  try {
    // 确保目录已加载
    if (!medicalItems.value.length) {
      medicalItems.value = (await getMedicalItems()).filter(i => i.category !== 'DRUG');
    }
    if (!drugs.value.length) {
      drugs.value = await getDrugs();
    }
    const result = await getClinicalAssistance({
      appointmentId: current.value.id,
      patientId: current.value.patientId,
      chiefComplaint: recordForm.chiefComplaint,
      presentIllness: recordForm.presentIllness,
      pastHistory: recordForm.pastHistory,
      allergyHistory: recordForm.allergyHistory,
      prompt: aiPrompt.value,
      availableExamItems: medicalItems.value.map(i => ({ code: i.code, name: i.name, category: i.category })),
      availableDrugs: drugs.value.map(d => ({ drugName: d.drugName, specification: d.specification })),
    });
    aiModel.value = result.model;
    aiFallback.value = result.fallbackUsed;
    aiMessages.value = result.suggestions.map((suggestion, index) => ({
      id: index === 0 ? result.aiRecordId : `${result.aiRecordId}-${index}`,
      ...suggestion
    }));

    const examMsg = result.suggestions.find(s => s.kind === 'exam');
    if (examMsg?.metadata?.projectNames?.length) {
      aiRecommendedNames.value = examMsg.metadata.projectNames as string[];
    }

    const medMsg = result.suggestions.find(s => s.kind === 'medication');
    if (medMsg?.metadata?.drugs?.length) {
      rxSuggestions.value = (medMsg.metadata.drugs as AiDrugSuggestion[]).map(suggestion => {
        const drug = matchDrugForSuggestion(suggestion);
        return drug ? normalizeAiRxSuggestion(suggestion, drug) : { ...suggestion, note: suggestion.note ?? '' };
      });
      rxAiRecordId.value = result.aiRecordId;
    }
    if (result.suggestions.length) {
      ElMessage.success(`已生成 ${result.suggestions.length} 条临床建议`);
    } else {
      ElMessage.warning('AI 未返回建议内容，请稍后重试');
    }
  } catch (e: any) {
    const msg = (e as any)?.response?.data?.detail ?? (e as any)?.message ?? '请检查 AI 服务是否启动，或网络超时';
    ElMessage.error({ message: `AI 生成失败：${msg}`, duration: 6000 });
    console.error('[AI] generateAssistance error:', e);
  } finally {
    aiLoading.value = false;
  }
}

function applyDiagnosis(message: ClinicalSuggestion & { id: string }) {
  const primary = message.metadata?.primaryDiagnosis as string | undefined;
  recordForm.diagnosis = primary || message.content;
  diagnosisSource.value = 'AI';
  diagnosisAiRecordId.value = message.id;
  ElMessage.success('诊断已填入，请复核确认');
}

async function applyMedication(message: ClinicalSuggestion & { id: string }) {
  const drugsData = message.metadata?.drugs as AiDrugSuggestion[] | undefined;
  if (!drugsData?.length) {
    ElMessage.warning('未包含结构化用药数据，请在处方页手动开药');
    return;
  }
  if (!drugs.value.length) drugs.value = await getDrugs();
  rxSuggestions.value = drugsData.map(suggestion => {
    const drug = matchDrugForSuggestion(suggestion);
    return drug ? normalizeAiRxSuggestion(suggestion, drug) : { ...suggestion, note: suggestion.note ?? '' };
  });
  rxAiRecordId.value = message.id;
  rxWarnings.value = [];
  mainTab.value = 'rx';
  ElMessage.success('用药建议已加入处方页，确认后点击"采纳为待缴费处方"');
}

function applyExamItems(message: ClinicalSuggestion & { id: string }) {
  const projectNames = message.metadata?.projectNames as string[] | undefined;
  if (projectNames?.length) aiRecommendedNames.value = projectNames;
  mainTab.value = 'orders';
  ElMessage.success('AI 推荐检查已置顶，可直接勾选批量开单');
}

function applyAdvice(message: ClinicalSuggestion & { id: string }) {
  recordForm.treatmentPlan = recordForm.treatmentPlan
    ? `${recordForm.treatmentPlan}\n${message.content}`
    : message.content;
  mainTab.value = 'record';
  ElMessage.success('临床建议已填入');
}

async function createRxFromSuggestion() {
  if (!current.value || !rxSuggestions.value.length) return;
  if (!drugs.value.length) drugs.value = await getDrugs();
  if (!recordForm.diagnosis) {
    ElMessage.warning({ message: '请先填写诊断再生成处方', duration: 4000 });
    return;
  }
  try {
    const unmatched: string[] = [];
    const items = rxSuggestions.value.map(suggestion => {
      const name = suggestion.drugName ?? '';
      const drug = matchDrugForSuggestion(suggestion);
      if (!drug) { unmatched.push(name); return null; }
      const normalized = normalizeAiRxSuggestion(suggestion, drug);
      return {
        drugId: drug.id,
        quantity: normalized.quantity ?? 1,
        dosage: normalized.dosage,
        usage: normalized.usage,
        frequency: normalized.frequency,
        days: normalized.days,
        note: normalized.note,
      };
    }).filter((i): i is NonNullable<typeof i> => i !== null);

    if (unmatched.length) {
      ElMessage.warning({ message: `以下药品在药库中未找到，已跳过：${unmatched.join('、')}`, duration: 5000 });
    }
    if (!items.length) {
      ElMessage.error('没有可匹配的药品，请手动开处方');
      return;
    }
    await createPrescription({
      appointmentId: current.value.id,
      medicalRecordId: currentRecordId.value,
      patientId: current.value.patientId,
      patientName: current.value.patientName,
      diagnosis: recordForm.diagnosis,
      aiAssistanceId: rxAiRecordId.value,
      aiAdoptionStatus: 'AI_ACCEPTED',
      aiRevisionNote: '医生采纳 AI 处方建议后生成，仍需缴费与药房审核',
      items
    });
    rxSuggestions.value = [];
    rxWarnings.value = [];
    ElMessage.success('处方已生成，待患者缴费');
    await loadPrescriptions();
    mainTab.value = 'record';
  } catch (e: any) {
    ElMessage.error({ message: `处方生成失败：${e?.response?.data?.message ?? e?.message ?? '请检查服务状态'}`, duration: 5000 });
  }
}

async function viewReport(report: MedicalReport) {
  selectedReport.value = report;
  selectedLabResults.value = [];
  const orderId = report.medicalOrderId ?? report.orderId;
  if (report.reportType === 'LAB' && orderId) {
    try {
      selectedLabResults.value = await getLabResults(orderId);
    } catch {
      selectedLabResults.value = [];
    }
  }
  reportDialogVisible.value = true;
}

function applyReportToRecord(report: MedicalReport) {
  const typeLabel = ({ CHECK: '检查', LAB: '检验', DISPOSAL: '处置' } as Record<string, string>)[report.reportType] ?? report.reportType;
  const line = `${typeLabel}：${report.conclusion}`;
  recordForm.presentIllness = recordForm.presentIllness
    ? `${recordForm.presentIllness}\n${line}`
    : line;
  reportDialogVisible.value = false;
  mainTab.value = 'record';
  ElMessage.success('已导入至现病史');
}

function printRecord() {
  mainTab.value = 'record';
  nextTick(() => window.print());
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(async () => {
  await loadQueue();
  records.value = await getMedicalRecords({});
  medicalItems.value = (await getMedicalItems()).filter(item => item.category !== 'DRUG');
  getDrugs().then(list => { drugs.value = list; }).catch(() => {});
});

watch(recordForm, () => { if (!loadingRecord) dirty.value = true; }, { deep: true });
watch(mainTab, (tab) => {
  if (tab === 'rx') loadPrescriptions();
  if (tab === 'orders' && !medicalItems.value.length) {
    getMedicalItems().then(list => { medicalItems.value = list.filter(i => i.category !== 'DRUG'); }).catch(() => {});
  }
  if (tab === 'history') loadHistory();
  if (tab === 'record' && current.value) {
    getMedicalOrders({ appointmentId: current.value.id })
      .then(list => { currentOrders.value = list; })
      .catch(() => {});
    getPrescriptions({ patientId: current.value.patientId })
      .then(list => { prescriptions.value = list; })
      .catch(() => {});
  }
});
</script>

<style scoped>
/* ── Root ── */
.wks {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  font-family: Inter, "Microsoft YaHei", system-ui, sans-serif;
}

/* ── Navbar ── */
.wks-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 20px;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
}
.wks-nav__brand { display: flex; align-items: center; gap: 10px; }
.wks-nav__logo {
  width: 30px; height: 30px;
  background: #fff; color: #0899a5;
  border-radius: 7px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 900; line-height: 1;
  flex-shrink: 0;
}
.wks-nav__title { font-size: 16px; font-weight: 600; letter-spacing: 0.5px; }
.wks-nav__right { display: flex; align-items: center; gap: 20px; font-size: 13px; }
.wks-nav__info { opacity: 0.9; }
.wks-nav__date { opacity: 0.8; }

/* ── Body ── */
.wks-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  height: calc(100vh - 52px);
}

/* ── Left sidebar ── */
.wks-sidebar {
  width: 270px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-hdr {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 8px;
  font-size: 18px; font-weight: 700;
}
.sidebar-search-wrap { padding: 0 12px 8px; }
.sidebar-tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
  padding: 0;
}
.stab {
  flex: 1; border: none; background: none;
  padding: 8px 2px; font-size: 11px; color: #6b7280;
  cursor: pointer; border-bottom: 2px solid transparent;
  transition: all 0.15s; white-space: nowrap;
}
.stab--active { color: #0cbdcc; border-bottom-color: #0cbdcc; font-weight: 600; }
.stab:hover:not(.stab--active) { color: #374151; }

.queue-list { flex: 1; overflow-y: auto; }
.queue-empty { text-align: center; color: #9ca3af; padding: 36px 0; font-size: 13px; }

.qcard {
  padding: 9px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  border-bottom: 1px solid #f3f4f6;
  transition: background 0.12s;
}
.qcard:hover { background: #f9fafb; }
.qcard--active { background: #e6f9fa; border-left-color: #0cbdcc; }
.qcard__top { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.qcard__num { font-size: 13px; color: #9ca3af; min-width: 20px; }
.qcard__name { font-size: 16px; font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qcard__sub { display: flex; justify-content: space-between; font-size: 12px; color: #9ca3af; padding-left: 26px; margin-bottom: 5px; }
.qcard__ops { padding-left: 22px; display: flex; gap: 2px; }
.sidebar-footer { padding: 8px 14px; border-top: 1px solid #e5e7eb; font-size: 12px; color: #9ca3af; text-align: center; }

/* ── Main content ── */
.wks-main {
  flex: 1; min-width: 0; overflow-y: auto;
  padding: 14px 16px;
  display: flex; flex-direction: column;
}
.main-empty { flex: 1; display: flex; align-items: center; justify-content: center; }

/* Patient header */
.patient-hdr {
  background: #fff; border-radius: 8px; padding: 14px 18px;
  display: flex; align-items: center; gap: 14px; margin-bottom: 12px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 7%);
}
.pat-avatar {
  width: 46px; height: 46px; border-radius: 50%;
  background: #ccf2f4; color: #0899a5;
  font-size: 20px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.pat-info { flex: 1; min-width: 0; }
.pat-row1 { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.pat-name { font-size: 18px; font-weight: 700; }
.pat-row2 { display: flex; gap: 20px; flex-wrap: wrap; font-size: 13px; color: #374151; }
.pat-row2 em { color: #9ca3af; font-style: normal; margin-right: 3px; }
.pat-allergy { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.pat-allergy__lbl { font-size: 13px; color: #6b7280; }

/* Tab bar */
.main-tabs {
  display: flex;
  background: #fff; border-radius: 8px 8px 0 0;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}
.mtab {
  padding: 11px 20px; border: none; background: none;
  font-size: 14px; color: #6b7280; cursor: pointer;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  transition: all 0.15s;
}
.mtab--active { color: #0899a5; border-bottom-color: #0cbdcc; font-weight: 600; }
.mtab:hover:not(.mtab--active) { color: #374151; }

.main-content {
  background: #fff; border-radius: 0 0 8px 8px; padding: 18px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 5%);
}
.main-content:not([style*="display: none"]) {
  flex: 1; display: flex !important; flex-direction: column;
}
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }

/* ── AI panel ── */
.wks-ai {
  width: 360px; flex-shrink: 0;
  display: flex; flex-direction: column;
  overflow: hidden; padding: 12px;
  border-left: 1px solid #e5e7eb; background: #f8fafc;
}
.ai-card {
  border-color: #a8e8ec;
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}
.ai-card :deep(.el-card__body) {
  flex: 1; display: flex; flex-direction: column; overflow: hidden; padding: 14px;
}
.ai-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ai-message p, .rx-item p { margin: 4px 0 0; font-size: 13px; }
/* 消息区占满剩余空间并滚动；textarea 和按钮 flex-shrink:0 钉在底部 */
.ai-messages { flex: 1; overflow-y: auto; margin-bottom: 10px; min-height: 80px; }
.ai-card :deep(.el-textarea) { flex-shrink: 0; }
.ai-action { margin-top: 8px; flex-shrink: 0; }
.ai-message { margin-bottom: 10px; padding: 10px; border-left: 3px solid #6366f1; background: #eef2ff; border-radius: 0 4px 4px 0; }
.ai-message--diagnosis { border-left-color: #6366f1; background: #eef2ff; }
.ai-message--exam     { border-left-color: #0899a5; background: #e6f9fa; }
.ai-message--medication { border-left-color: #16a34a; background: #f0fdf4; }
.ai-message--risk     { border-left-color: #dc2626; background: #fef2f2; }
.ai-message--advice   { border-left-color: #d97706; background: #fffbeb; }
.ai-msg-label { font-weight: 700; font-size: 12px; }
.ai-msg-actions { margin-top: 6px; }
.rx-item { margin-bottom: 10px; padding: 10px; border-left: 3px solid #16a34a; background: #f0fdf4; border-radius: 0 4px 4px 0; }
.ai-action { margin-top: 8px; }
.muted { color: #64748b; }
.full { width: 100%; }
.ai-exam-banner {
  background: #e6f9fa; border: 1px solid #a8e8ec; border-radius: 6px;
  padding: 7px 14px; margin-bottom: 10px; font-size: 13px; color: #0899a5; font-weight: 500;
}

/* ── Document-style medical record ── */
.med-doc {
  font-family: "SimSun", "宋体", "Microsoft YaHei", sans-serif;
  font-size: 14px; color: #111;
}
.med-doc__title { text-align: center; font-size: 18px; font-weight: bold; letter-spacing: 5px; padding-bottom: 10px; }
.med-doc__rule-thick { border: none; border-top: 3px double #444; margin-bottom: 14px; }
.med-doc__rule { border: none; border-top: 1px solid #aaa; margin: 14px 0 12px; }
.med-doc__info-row { flex-wrap: wrap; gap: 4px 14px; margin-bottom: 12px; }
.med-doc__row { display: flex; align-items: center; gap: 4px; margin-bottom: 10px; }
.med-doc__row--top { align-items: flex-start; }
.med-doc__row--grow { flex: 1; }
.med-doc__lbl { white-space: nowrap; font-size: 14px; }
.med-doc__lbl--w { min-width: 4.5em; text-align: justify; text-align-last: justify; }
.med-doc__staticval {
  border-bottom: 1px solid #666; padding: 1px 4px;
  font-size: 14px; min-width: 60px;
}
.med-doc__staticval--flex { flex: 1; }
.med-doc__input {
  flex: 1; min-width: 0; width: 0;
  border: none; border-bottom: 1px solid #666;
  outline: none; background: transparent;
  font-family: inherit; font-size: inherit; color: inherit;
  padding: 2px 4px;
}
.med-doc__input--bold { font-weight: 600; }
.med-doc__input:focus { border-bottom-color: #0899a5; }
.med-doc__input[readonly],
.med-doc__area[readonly] { color: #6b7280; cursor: default; }
.med-doc__locked-banner {
  display: flex; align-items: center; gap: 6px;
  background: #fef9ec; border: 1px solid #fcd34d; border-radius: 6px;
  padding: 7px 14px; margin-bottom: 12px; font-size: 13px; color: #92400e;
}
.med-doc__area {
  flex: 1; min-width: 0; width: 0;
  border: none; border-bottom: 1px solid #666;
  outline: none; background: transparent;
  font-family: inherit; font-size: inherit; color: inherit;
  resize: vertical; padding: 2px 4px; line-height: 1.8; min-height: 54px;
}
.med-doc__area:focus { border-bottom-color: #0899a5; }
.med-doc__area--bold { font-weight: 600; }
.med-doc__area--grow { resize: vertical; }
.med-doc__prescriptions {
  flex: 1;
  min-width: 0;
  border-bottom: 1px solid #666;
  padding: 2px 4px 4px;
  min-height: 34px;
}
.med-doc__rx {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 10px;
  line-height: 1.8;
}
.med-doc__rx + .med-doc__rx { margin-top: 2px; }
.med-doc__rx-no { color: #64748b; font-size: 12px; }
.med-doc__rx-status {
  color: #0899a5;
  font-size: 12px;
  border: 1px solid #a8e8ec;
  border-radius: 3px;
  padding: 0 4px;
}
.med-doc__rx-item { font-size: 13.5px; }
.med-doc__rx-placeholder { color: #9ca3af; font-size: 12px; line-height: 1.8; }
.med-doc__check-placeholder { font-size: 12px; color: #9ca3af; font-style: italic; padding: 2px 0; }
.check-done-mark { color: #0899a5; font-size: 12px; margin-left: 4px; }
.med-doc__checklist { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.med-doc__check-item { display: flex; align-items: baseline; gap: 6px; font-size: 13.5px; line-height: 1.6; }
.med-doc__check-item--done { color: #0899a5; }
.med-doc__check-item--pending { color: #6b7280; }
.check-tag {
  font-size: 11px; padding: 1px 5px; border-radius: 3px; flex-shrink: 0;
  background: #e6f9fa; color: #0899a5; border: 1px solid #a8e8ec;
}
.med-doc__check-item--pending .check-tag {
  background: #f3f4f6; color: #6b7280; border-color: #d1d5db;
}
.med-doc__signature-row {
  display: flex;
  justify-content: flex-end;
  gap: 56px;
  margin: 18px 0 6px;
  font-size: 14px;
  color: #111;
}
.med-doc__signature-row span {
  min-width: 160px;
  border-bottom: 1px solid #666;
  padding: 0 4px 3px;
}
.med-doc__footer { display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 12px; }

/* ── Prescription tab ── */
.rx-ai-hint { background: #f0fdf9; border: 1px solid #a8e8ec; border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; }
.rx-ai-hint__hdr { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; font-weight: 600; font-size: 13px; }
.rx-ai-hint__item { font-size: 13px; padding: 3px 0; display: flex; gap: 12px; align-items: baseline; }
.rx-warn { color: #d97706; font-size: 12px; }
.rx-section-hdr { display: flex; align-items: center; justify-content: space-between; font-weight: 600; font-size: 13px; margin-bottom: 10px; }
.rx-empty { text-align: center; color: #9ca3af; padding: 24px 0; font-size: 13px; }
.rx-card { border: 1px solid #e5e7eb; border-radius: 6px; margin-bottom: 10px; overflow: hidden; }
.rx-card__hdr { display: flex; align-items: center; gap: 10px; background: #f9fafb; padding: 8px 12px; border-bottom: 1px solid #e5e7eb; }
.rx-card__no { font-size: 12px; color: #6b7280; flex: 1; }
.rx-card__amount { font-size: 13px; font-weight: 600; color: #0899a5; margin-left: auto; }
.rx-card__body { padding: 8px 12px; }
.rx-card--compact .rx-card__hdr { padding: 6px 12px; }
.rx-card--compact .rx-card__item { font-size: 12px; padding: 1px 0; }
.rx-card__item { font-size: 13px; color: #374151; padding: 2px 0; }
.rx-manual { margin-top: 12px; padding: 12px; background: #f9fafb; border-radius: 6px; border: 1px solid #e5e7eb; }
.rx-manual-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.rx-manual-name { font-size: 13px; font-weight: 500; min-width: 80px; }

/* ── Report dialog ── */
.report-dialog { font-size: 14px; color: #111; }
.report-dialog__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.report-dialog__type { font-size: 16px; font-weight: 700; color: #0899a5; }
.report-dialog__meta { font-size: 12px; color: #9ca3af; }
.report-dialog__rule { border: none; border-top: 2px solid #e5e7eb; margin-bottom: 16px; }
.report-dialog__section { margin-bottom: 16px; }
.report-dialog__label { font-size: 12px; color: #6b7280; margin-bottom: 6px; font-weight: 600; letter-spacing: 0.5px; }
.report-dialog__body { line-height: 1.8; color: #1f2937; background: #f9fafb; border-radius: 6px; padding: 10px 14px; }
.report-dialog__body--emphasis { color: #0899a5; font-weight: 600; font-size: 15px; }

.med-report {
  font-family: "SimSun", "宋体", "Microsoft YaHei", sans-serif;
  font-size: 14px;
  color: #111;
  background: #fff;
}
.med-report__hospital {
  text-align: center;
  font-size: 15px;
  color: #374151;
  margin-bottom: 4px;
}
.med-report__title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 5px;
  margin-bottom: 10px;
}
.med-report__rule-thick {
  border: none;
  border-top: 3px double #444;
  margin-bottom: 14px;
}
.med-report__rule {
  border: none;
  border-top: 1px solid #bbb;
  margin: 14px 0;
}
.med-report__info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 24px;
  margin-bottom: 12px;
}
.rinfo-cell {
  display: flex;
  align-items: baseline;
  min-width: 0;
}
.rinfo-cell em {
  width: 5.6em;
  color: #555;
  font-style: normal;
  white-space: nowrap;
}
.rinfo-cell span {
  flex: 1;
  min-width: 0;
  border-bottom: 1px solid #ccc;
  padding: 2px 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.med-report__clinical {
  display: flex;
  align-items: baseline;
  margin-bottom: 8px;
}
.med-report__clinical em {
  color: #555;
  font-style: normal;
  white-space: nowrap;
  margin-right: 8px;
}
.med-report__clinical span {
  flex: 1;
  border-bottom: 1px solid #ccc;
  padding: 2px 4px;
}
.med-report__section { margin-bottom: 6px; }
.med-report__section-lbl {
  font-weight: 700;
  color: #374151;
  margin-bottom: 6px;
}
.med-report__section-lbl--emphasis { color: #0899a5; }
.med-report__area {
  width: 100%;
  min-height: 92px;
  border: none;
  border-bottom: 1px solid #777;
  outline: none;
  resize: vertical;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.55;
  padding: 4px;
  white-space: pre-wrap;
  box-sizing: border-box;
}
.med-report__area--readonly {
  background: transparent;
  resize: none;
}
.med-report__area--single {
  min-height: 28px;
  height: 28px;
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.med-report__area--emphasis {
  font-weight: 600;
  font-size: 13.5px;
  color: #0899a5;
}
.med-report__sig-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-top: 12px;
}
.sig-block {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.sig-label { color: #555; }
.sig-name-print,
.sig-date {
  display: inline-block;
  min-width: 96px;
  border-bottom: 1px solid #bbb;
  padding: 0 4px 2px;
}
.sig-cursive {
  display: inline-block;
  min-width: 96px;
  border-bottom: 1px solid #bbb;
  padding: 0 4px 2px;
  color: #0899a5;
  font-weight: 700;
  font-family: "KaiTi", "楷体", cursive;
}
.stamp-block { flex-shrink: 0; padding-right: 12px; }
.stamp-circle {
  width: 74px;
  height: 74px;
  border: 2px solid #ef4444;
  border-radius: 50%;
  color: #ef4444;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: rotate(-12deg);
  font-weight: 700;
}
.stamp-circle--published { opacity: 0.9; }
.med-report__notice {
  margin-top: 14px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.7;
}
.lab-report-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
  table-layout: fixed;
}
.lab-report-table th {
  background: #f3f4f6;
  color: #374151;
  font-weight: 700;
}
.lab-report-table th,
.lab-report-table td {
  border: 1px solid #d1d5db;
  padding: 8px 10px;
  line-height: 1.5;
}
.lab-report-table th:first-child,
.lab-report-table td:first-child {
  width: 47%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lab-report-table th:nth-child(2),
.lab-report-table td:nth-child(2) { width: 16%; }
.lab-report-table th:nth-child(3),
.lab-report-table td:nth-child(3) { width: 11%; }
.lab-report-table th:nth-child(4),
.lab-report-table td:nth-child(4) { width: 18%; }
.lab-report-table th:nth-child(5),
.lab-report-table td:nth-child(5) { width: 8%; }
.lab-report-table__value { font-weight: 700; color: #0899a5; }

.report-sheet {
  font-family: "SimSun", "宋体", "Microsoft YaHei", sans-serif;
  color: #111;
  font-size: 14px;
  padding: 4px 4px 0;
}
.report-sheet__hospital {
  text-align: center;
  color: #374151;
  font-size: 15px;
  margin-bottom: 4px;
}
.report-sheet__title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 4px;
  margin-bottom: 14px;
}
.report-sheet__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 14px;
  line-height: 1.8;
  border-bottom: 1px solid #d1d5db;
  padding-bottom: 6px;
  margin-bottom: 6px;
}
.report-sheet__meta span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.report-sheet__rule {
  border-top: 3px double #444;
  margin: 12px 0 14px;
}
.report-sheet__table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
  table-layout: fixed;
}
.report-sheet__table th,
.report-sheet__table td {
  border: 1px solid #d1d5db;
  padding: 8px 10px;
  text-align: left;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.report-sheet__table th {
  background: #f3f4f6;
  font-weight: 600;
}
.report-sheet__table th:nth-child(2),
.report-sheet__table td:nth-child(2) { width: 16%; }
.report-sheet__table th:nth-child(3),
.report-sheet__table td:nth-child(3) { width: 11%; }
.report-sheet__table th:nth-child(4),
.report-sheet__table td:nth-child(4) { width: 18%; }
.report-sheet__table th:nth-child(5),
.report-sheet__table td:nth-child(5) { width: 8%; }
.report-sheet__project-col {
  width: 47%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.report-sheet__result {
  font-weight: 700;
  color: #0899a5;
}
.report-sheet__section {
  margin-bottom: 14px;
}
.report-sheet__label {
  font-weight: 700;
  margin-bottom: 6px;
}
.report-sheet__body {
  min-height: 42px;
  line-height: 1.9;
  border: 1px solid #d1d5db;
  padding: 9px 12px;
  white-space: pre-wrap;
}
.report-sheet__body--emphasis {
  font-weight: 700;
}
.report-sheet__signatures {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid #9ca3af;
  padding-top: 12px;
  margin-top: 16px;
  color: #374151;
}

/* ── Print ── */
@media print {
  .wks-nav,
  .wks-sidebar,
  .wks-ai,
  .patient-hdr,
  .main-tabs,
  .med-doc__footer { display: none !important; }

  .wks { height: auto !important; overflow: visible !important; background: #fff; }
  .wks-body { height: auto; overflow: visible; display: block; }
  .wks-main { padding: 0; overflow: visible; }
  .main-content { box-shadow: none; border-radius: 0; padding: 0; }
  .med-doc { max-width: 100%; }
  .med-doc__input, .med-doc__area {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
}
</style>
