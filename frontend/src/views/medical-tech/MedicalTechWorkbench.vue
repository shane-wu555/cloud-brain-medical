<template>
  <div class="wks">
    <!-- ── Navbar ── -->
    <header class="wks-nav">
      <div class="wks-nav__brand">
        <span class="wks-nav__logo">+</span>
        <span class="wks-nav__title">{{ roleLabel }}</span>
      </div>
      <div class="wks-nav__right">
        <span class="wks-nav__info">{{ auth.user?.name }} 医师</span>
        <span class="wks-nav__date">{{ today }} {{ dayOfWeek }}</span>
        <el-button size="small" text @click="logout" style="color:rgba(255,255,255,0.85)">退出</el-button>
      </div>
    </header>

    <!-- ── Body ── -->
    <div class="wks-body">

      <!-- Left: queue sidebar -->
      <aside class="wks-sidebar">
        <div class="sidebar-hdr">
          <span>待执行队列</span>
          <el-button :loading="refreshing" size="small" text @click="refreshOrders" style="font-size:16px">↺</el-button>
        </div>
        <div class="sidebar-search-wrap">
          <el-input v-model="queueKeyword" clearable size="small" placeholder="搜索姓名/项目" />
        </div>

        <div class="sidebar-tabs">
          <button :class="['stab', queueTab === 'all' && 'stab--active']" @click="queueTab = 'all'">
            全部 {{ orders.length }}
          </button>
          <button :class="['stab', queueTab === 'waiting' && 'stab--active']" @click="queueTab = 'waiting'">
            待执行 {{ waitingCount }}
          </button>
          <button :class="['stab', queueTab === 'done' && 'stab--active']" @click="queueTab = 'done'">
            已完成 {{ doneCount }}
          </button>
        </div>

        <div class="queue-list">
          <div
            v-for="item in filteredOrders" :key="item.id"
            :class="['qcard', current?.id === item.id && 'qcard--active']"
            @click="select(item)"
          >
            <div class="qcard__top">
              <span class="qcard__num">{{ item.queueNumber }}</span>
              <span class="qcard__name">{{ item.patientName }}</span>
              <el-tag v-if="item.urgency === 'EMERGENCY'" type="danger" size="small" effect="light">急诊</el-tag>
            </div>
            <div class="qcard__proj">{{ item.itemName }}</div>
            <div class="qcard__sub">
              <el-tag :type="statusTagType(item.status)" size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
              <span class="qcard__type">{{ formatOrderType(item.orderType) }}</span>
            </div>
            <div class="qcard__ops" @click.stop>
              <el-button v-if="item.status === 'WAITING'" size="small" type="primary" link @click="call(item)">叫号</el-button>
              <el-button v-if="item.status === 'CALLED'" size="small" type="success" link @click="start(item)">开始执行</el-button>
              <el-button v-if="['WAITING','CALLED'].includes(item.status)" size="small" link @click="miss(item)">过号</el-button>
            </div>
          </div>
          <div v-if="!filteredOrders.length" class="queue-empty">暂无医嘱</div>
        </div>
        <div class="sidebar-footer">共 {{ orders.length }} 条</div>
      </aside>

      <!-- Center: main content -->
      <main class="wks-main">
        <div v-if="!current" class="main-empty">
          <el-empty description="请从左侧选择医嘱开始执行" :image-size="90" />
        </div>

        <template v-else>
          <!-- Patient header -->
          <div class="patient-hdr">
            <div class="pat-avatar">{{ current.patientName.slice(-1) }}</div>
            <div class="pat-info">
              <div class="pat-row1">
                <span class="pat-name">{{ current.patientName }}</span>
                <el-tag v-if="current.urgency === 'EMERGENCY'" type="danger" size="small">急诊优先</el-tag>
                <el-tag type="primary" size="small" effect="plain">{{ formatOrderType(current.orderType) }}</el-tag>
                <el-tag :type="statusTagType(current.status)" size="small" effect="plain">{{ statusLabel(current.status) }}</el-tag>
              </div>
              <div class="pat-row2">
                <span><em>检查项目</em>{{ current.itemName }}</span>
                <span><em>检查部位</em>{{ current.bodyPart || '—' }}</span>
                <span><em>临床目的</em>{{ current.purpose || '—' }}</span>
              </div>
            </div>
          </div>

          <!-- Tab bar -->
          <div class="main-tabs">
            <button :class="['mtab', mainTab === 'work' && 'mtab--active']" @click="mainTab = 'work'">
              {{ workTabLabel }}
            </button>
            <button :class="['mtab', mainTab === 'report' && 'mtab--active']" @click="mainTab = 'report'">
              正式报告
            </button>
          </div>

          <!-- ── Work tab ── -->
          <div v-show="mainTab === 'work'" :class="['main-content', role === 'CHECK_DOCTOR' ? 'main-content--viewer' : '']">

            <!-- CHECK_DOCTOR: professional CT viewer -->
            <template v-if="role === 'CHECK_DOCTOR'">
              <div class="ct-viewer">

                <!-- Viewer toolbar -->
                <div class="ct-toolbar">
                  <button class="ct-tool" title="四格布局">
                    <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
                      <rect x="0" y="0" width="6" height="6" rx="1"/><rect x="8" y="0" width="6" height="6" rx="1"/>
                      <rect x="0" y="8" width="6" height="6" rx="1"/><rect x="8" y="8" width="6" height="6" rx="1"/>
                    </svg>
                  </button>
                  <div class="ct-sep"></div>
                  <button class="ct-tool" title="窗宽窗位">☀</button>
                  <button class="ct-tool" title="旋转">↻</button>
                  <button class="ct-tool" title="缩小">⊖</button>
                  <button class="ct-tool" title="放大">⊕</button>
                  <button class="ct-tool ct-tool--active" title="平移">✋</button>
                  <button class="ct-tool" title="十字定位线">✛</button>
                  <button class="ct-tool" title="反色">◑</button>
                  <div class="ct-sep"></div>
                  <button class="ct-tool" title="测量">▷</button>
                  <button class="ct-tool" title="标注">✏</button>
                  <button class="ct-tool" title="更多">···</button>
                  <div class="ct-sep ct-sep--flex"></div>
                  <span v-if="aiStatus === 'PROCESSING'" class="ct-ai-badge ct-ai-badge--running">AI 分析中…</span>
                  <span v-else-if="aiStatus === 'COMPLETED'" class="ct-ai-badge ct-ai-badge--done">✓ AI 已完成</span>
                </div>

                <!-- 2×2 panels -->
                <div class="ct-panels">

                  <!-- Axial -->
                  <div class="ct-panel" @dragover.prevent @drop.prevent="handleDrop" @wheel.prevent="onWheelPanel('axial', $event)">
                    <span class="ct-panel__lbl">【轴检 Axial】</span>
                    <span class="ct-panel__slice" v-if="volume">切片: {{ sliceZ + 1 }} / {{ volume.nz }}</span>

                    <div v-if="volLoading" class="ct-loading">解析体积影像中…</div>

                    <template v-else-if="volume">
                      <canvas ref="canvasAxial" class="ct-panel__canvas"></canvas>
                      <div class="ct-line ct-line--h"></div>
                      <div class="ct-line ct-line--v"></div>
                      <span class="ct-orient ct-orient--ml">R</span>
                      <span class="ct-orient ct-orient--mr">L</span>
                      <span class="ct-orient ct-orient--tl">S</span>
                      <span class="ct-orient ct-orient--bl">I</span>
                      <div class="ct-scale">{{ volume.dx.toFixed(2) }}mm/px</div>
                      <button class="ct-clear" @click="clearFile">✕</button>
                      <input class="ct-slider" type="range" :min="0" :max="volume.nz - 1" v-model.number="sliceZ" />
                    </template>

                    <template v-else-if="imagePreviewUrl">
                      <img :src="imagePreviewUrl" class="ct-panel__img" alt="轴位" />
                      <div class="ct-line ct-line--h"></div>
                      <div class="ct-line ct-line--v"></div>
                      <button class="ct-clear" @click="clearFile">✕</button>
                    </template>

                    <template v-else-if="file">
                      <div class="ct-dicom-hint">
                        <div class="ct-dicom-hint__icon">📄</div>
                        <div class="ct-dicom-hint__name">{{ file.name }}</div>
                        <div class="ct-dicom-hint__sub">DICOM 格式，可提交 AI 分析</div>
                        <button class="ct-clear" @click="clearFile" style="position:static;margin-top:8px">✕ 移除</button>
                      </div>
                    </template>

                    <template v-else>
                      <label class="ct-drop">
                        <div class="ct-drop__icon">⬡</div>
                        <div class="ct-drop__text">拖拽或点击上传影像</div>
                        <div class="ct-drop__sub">NIfTI · NRRD · MHA · DICOM · JPG/PNG</div>
                        <input type="file" accept=".nii,.nii.gz,.nrrd,.nhdr,.mha,.dcm,.jpg,.jpeg,.png,.bmp,.tiff" @change="chooseFile" style="display:none" />
                      </label>
                    </template>
                  </div>

                  <!-- 3D WebGL reconstruction -->
                  <div class="ct-panel ct-panel--3d"
                       @mousedown="on3DDown"
                       @mousemove="on3DMove"
                       @mouseup="on3DUp"
                       @mouseleave="on3DUp">
                    <span class="ct-panel__lbl">【3D 重建】</span>
                    <span v-if="volume" class="ct-3d-hint">拖拽旋转</span>
                    <template v-if="volume">
                      <canvas ref="canvas3D" class="ct-panel__canvas ct-panel__canvas--3d"></canvas>
                    </template>
                    <template v-else>
                      <div class="ct-placeholder">
                        <div class="ct-placeholder__icon">⬡</div>
                        <div class="ct-placeholder__text">3D 体积重建</div>
                        <div class="ct-placeholder__sub">上传体积影像后显示（WebGL2）</div>
                      </div>
                    </template>
                  </div>

                  <!-- Coronal -->
                  <div class="ct-panel" @wheel.prevent="onWheelPanel('coronal', $event)">
                    <span class="ct-panel__lbl">【冠状 Coronal】</span>
                    <span class="ct-panel__slice" v-if="volume">切片: {{ sliceY + 1 }} / {{ volume.ny }}</span>
                    <template v-if="volume">
                      <canvas ref="canvasCoronal" class="ct-panel__canvas"></canvas>
                      <div class="ct-line ct-line--h"></div>
                      <div class="ct-line ct-line--v"></div>
                      <span class="ct-orient ct-orient--ml">R</span>
                      <span class="ct-orient ct-orient--mr">L</span>
                      <span class="ct-orient ct-orient--tl">S</span>
                      <span class="ct-orient ct-orient--bl">I</span>
                      <div class="ct-scale">{{ volume.dy.toFixed(2) }}mm/px</div>
                      <input class="ct-slider" type="range" :min="0" :max="volume.ny - 1" v-model.number="sliceY" />
                    </template>
                    <template v-else>
                      <div class="ct-placeholder">
                        <div class="ct-placeholder__icon">⬡</div>
                        <div class="ct-placeholder__text">冠状面视图</div>
                        <div class="ct-placeholder__sub">上传体积影像后显示</div>
                      </div>
                      <span class="ct-orient ct-orient--ml" style="opacity:.3">R</span>
                      <span class="ct-orient ct-orient--mr" style="opacity:.3">L</span>
                    </template>
                  </div>

                  <!-- Sagittal -->
                  <div class="ct-panel" @wheel.prevent="onWheelPanel('sagittal', $event)">
                    <span class="ct-panel__lbl">【矢状 Sagittal】</span>
                    <span class="ct-panel__slice" v-if="volume">切片: {{ sliceX + 1 }} / {{ volume.nx }}</span>
                    <template v-if="volume">
                      <canvas ref="canvasSagittal" class="ct-panel__canvas"></canvas>
                      <div class="ct-line ct-line--h"></div>
                      <div class="ct-line ct-line--v"></div>
                      <span class="ct-orient ct-orient--ml">A</span>
                      <span class="ct-orient ct-orient--mr">P</span>
                      <span class="ct-orient ct-orient--tl">S</span>
                      <span class="ct-orient ct-orient--bl">I</span>
                      <div class="ct-scale">{{ volume.dz.toFixed(2) }}mm/px</div>
                      <input class="ct-slider" type="range" :min="0" :max="volume.nx - 1" v-model.number="sliceX" />
                    </template>
                    <template v-else>
                      <div class="ct-placeholder">
                        <div class="ct-placeholder__icon">⬡</div>
                        <div class="ct-placeholder__text">矢状面视图</div>
                        <div class="ct-placeholder__sub">上传体积影像后显示</div>
                      </div>
                      <span class="ct-orient ct-orient--ml" style="opacity:.3">A</span>
                      <span class="ct-orient ct-orient--mr" style="opacity:.3">P</span>
                    </template>
                  </div>

                </div><!-- /ct-panels -->

                <!-- Bottom action toolbar -->
                <div class="ct-actions">
                  <!-- W/L inline controls -->
                  <span class="ct-wl-lbl">WL</span>
                  <input class="ct-wl-inp" type="number" v-model.number="winC" @change="rerenderMpr" />
                  <span class="ct-wl-lbl">WW</span>
                  <input class="ct-wl-inp" type="number" v-model.number="winW" @change="rerenderMpr" />
                  <div class="ct-sep ct-sep--sm"></div>
                  <button class="ct-act" @click="setWindow('brain')">脑窗</button>
                  <button class="ct-act" @click="setWindow('bone')">骨窗</button>
                  <button class="ct-act" @click="setWindow('lung')">肺窗</button>
                  <button class="ct-act" @click="setWindow('soft')">软组织</button>
                  <div class="ct-act-gap"></div>
                  <label class="ct-act" :class="{ 'ct-act--disabled': !current }">
                    ⬆ 单文件影像
                    <input type="file" accept=".nii,.nii.gz,.nrrd,.nhdr,.mha,.dcm,.jpg,.jpeg,.png,.bmp,.tiff" @change="chooseFile" style="display:none" :disabled="!current" />
                  </label>
                  <label class="ct-act" :class="{ 'ct-act--disabled': !current }" title="上传一整个 DICOM 文件夹">
                    📂 DICOM 文件夹
                    <input type="file" webkitdirectory multiple @change="loadDicomFolder" style="display:none" :disabled="!current" />
                  </label>
                  <button class="ct-act ct-act--primary" :disabled="!file || !current" @click="uploadCt">
                    提交 AI 分析
                  </button>
                  <button class="ct-act ct-act--report" @click="mainTab = 'report'">
                    📋 生成报告
                  </button>
                </div>

              </div>
            </template>

            <!-- LAB_DOCTOR: specimen + lab results -->
            <template v-if="role === 'LAB_DOCTOR'">
              <div class="lab-section">
                <div class="lab-block-title">样本登记</div>
                <div class="lab-grid">
                  <div class="lab-field">
                    <label>样本类型</label>
                    <el-input v-model="lab.specimenType" size="small" />
                  </div>
                  <div class="lab-field">
                    <label>条码号</label>
                    <el-input v-model="lab.barcode" size="small" />
                  </div>
                </div>
                <el-button type="primary" size="small" :disabled="!current" @click="prepareSpecimen">
                  登记并流转至分析
                </el-button>

                <div class="lab-block-title" style="margin-top:20px">检验指标录入</div>
                <div class="lab-grid">
                  <div class="lab-field">
                    <label>指标名称</label>
                    <el-input v-model="lab.itemName" size="small" placeholder="如 血红蛋白" />
                  </div>
                  <div class="lab-field">
                    <label>结果值</label>
                    <el-input v-model="lab.value" size="small" />
                  </div>
                  <div class="lab-field">
                    <label>单位</label>
                    <el-input v-model="lab.unit" size="small" placeholder="如 g/L" />
                  </div>
                </div>
                <el-button size="small" :disabled="!specimenId" @click="saveLab">保存检验指标</el-button>
              </div>
            </template>

            <!-- DISPOSAL_DOCTOR: procedure record -->
            <template v-if="role === 'DISPOSAL_DOCTOR'">
              <div class="disposal-section">
                <div class="lab-block-title">处置操作记录</div>
                <el-input
                  v-model="report.findings"
                  type="textarea" :rows="8"
                  placeholder="记录处置操作过程、耗材、剂量及患者反应…"
                />
              </div>
            </template>
          </div>

          <!-- ── Report tab: document-style ── -->
          <div v-show="mainTab === 'report'" class="main-content">
            <div class="med-report" id="printReport">

              <!-- Header -->
              <div class="med-report__hospital">智慧云脑诊疗中心</div>
              <div class="med-report__title">{{ reportTitle }}</div>
              <div class="med-report__rule-thick"></div>

              <!-- Patient info grid -->
              <div class="med-report__info-grid">
                <div class="rinfo-cell">
                  <em>姓　名</em><span>{{ current.patientName }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>登记号</em><span>{{ current.id.slice(0, 10).toUpperCase() }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>检查项目</em><span>{{ current.itemName }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>检查部位</em><span>{{ current.bodyPart || '—' }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>检查日期</em><span>{{ today }}</span>
                </div>
                <div class="rinfo-cell">
                  <em>报告医师</em><span>{{ auth.user?.name }}</span>
                </div>
              </div>

              <div class="med-report__clinical">
                <em>临床诊断 / 目的</em>
                <span>{{ current.purpose || '待定' }}</span>
              </div>

              <div class="med-report__rule"></div>

              <!-- Findings -->
              <div class="med-report__section">
                <div class="med-report__section-lbl">检查所见 / 执行过程</div>
                <textarea
                  class="med-report__area"
                  v-model="report.findings"
                  placeholder="详细描述检查所见、执行过程…"
                  rows="5"
                />
              </div>

              <div class="med-report__rule"></div>

              <!-- Conclusion -->
              <div class="med-report__section">
                <div class="med-report__section-lbl med-report__section-lbl--emphasis">结　论 / 结　果</div>
                <textarea
                  class="med-report__area med-report__area--emphasis"
                  v-model="report.conclusion"
                  placeholder="填写检查结论或检验结果…"
                  rows="4"
                />
              </div>

              <!-- Advice -->
              <div class="med-report__section" style="margin-top:14px">
                <div class="med-report__section-lbl">后续建议</div>
                <textarea
                  class="med-report__area"
                  v-model="report.advice"
                  placeholder="后续复查建议、注意事项…"
                  rows="2"
                />
              </div>

              <div class="med-report__rule"></div>

              <!-- Signature footer -->
              <div class="med-report__sig-footer">
                <div class="sig-block">
                  <div class="sig-row">
                    <span class="sig-label">报告医师：</span>
                    <span class="sig-name-print">{{ auth.user?.name }}</span>
                  </div>
                  <div class="sig-row sig-row--sign">
                    <span class="sig-label">医师签名：</span>
                    <span class="sig-cursive">{{ auth.user?.name }}</span>
                  </div>
                  <div class="sig-row">
                    <span class="sig-label">报告日期：</span>
                    <span class="sig-date">{{ confirmedAt || today }}</span>
                  </div>
                </div>
                <div class="stamp-block">
                  <div :class="['stamp-circle', published && 'stamp-circle--published']">
                    <span>{{ published ? '已发布' : '待审核' }}</span>
                  </div>
                </div>
              </div>

              <div class="med-report__notice">
                注：本报告由检查医师审核后发布，仅供临床医师参考，如有疑义请及时与检查科联系。
              </div>

              <!-- Actions (hidden when printing) -->
              <div class="med-report__actions no-print">
                <el-button size="small" @click="printReport">打印报告</el-button>
                <div style="display:flex;gap:8px">
                  <el-button size="small" @click="saveDraft">保存草稿</el-button>
                  <el-button type="success" size="small" :disabled="published" @click="confirmAndPublish">
                    {{ published ? '已发布' : '确认发布' }}
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </main>

      <!-- Right: AI panel -->
      <aside class="wks-ai">
        <el-card shadow="never" class="ai-card">
          <template #header>
            <div class="ai-header">
              <span>AI 检查辅助</span>
              <el-tag :type="aiFallback ? 'warning' : aiModel ? 'success' : 'info'" effect="plain" size="small">
                {{ aiModelLabel }}
              </el-tag>
            </div>
          </template>

          <div class="context-block">
            <strong>当前医嘱</strong>
            <p>{{ current ? `${current.patientName} · ${current.itemName}` : '请先选择医嘱' }}</p>
            <p class="muted">{{ current?.purpose || '等待 AI 辅助分析' }}</p>
          </div>

          <div class="ai-messages">
            <div v-for="msg in aiMessages" :key="msg.id" class="ai-message">
              <span class="ai-msg-label">{{ msg.label }}</span>
              <p>{{ msg.content }}</p>
              <div style="display:flex;gap:6px;margin-top:6px;flex-wrap:wrap">
                <el-button v-if="msg.kind === 'findings'" size="small" @click="applyToFindings(msg.content)">
                  填入所见
                </el-button>
                <el-button v-if="msg.kind === 'conclusion'" size="small" @click="applyToConclusion(msg.content)">
                  填入结论
                </el-button>
              </div>
            </div>
            <el-empty v-if="!aiMessages.length" description="点击生成后在此显示" :image-size="60" />
          </div>

          <el-button type="primary" class="full ai-action" :disabled="!current" @click="generateAiDraft">
            生成 AI 报告草稿
          </el-button>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';
import {
  callMedicalOrder, confirmReport, createReportDraft as saveReportDraft,
  createSpecimen, getMedicalOrders, missMedicalOrder,
  refreshAiTask, saveLabResults, startMedicalOrder,
  submitCt, transitionSpecimen, uploadAttachment,
  type MedicalOrder
} from '../../api/medical-order';
import { createReportDraft as createAiReportDraft } from '../../api/ai';
import { readVolume, readDicomSeries, renderAxial, renderCoronal, renderSagittal, type VolumeData } from '../../utils/volumeReader';
import { VolumeRenderer3D } from '../../utils/volumeRenderer3D';

const auth = useAuthStore();
const router = useRouter();
const role = computed(() => auth.user?.role ?? '');

const roleLabel = computed(() => (({
  CHECK_DOCTOR: '检查医生工作台', LAB_DOCTOR: '检验医生工作台', DISPOSAL_DOCTOR: '处置医生工作台'
} as Record<string, string>)[role.value] ?? '医技工作台'));

const workTabLabel = computed(() => (({
  CHECK_DOCTOR: '影像上传', LAB_DOCTOR: '检验登记', DISPOSAL_DOCTOR: '处置记录'
} as Record<string, string>)[role.value] ?? '执行记录'));

const reportTitle = computed(() => (({
  CHECK_DOCTOR: '影像检查报告', LAB_DOCTOR: '临床检验报告', DISPOSAL_DOCTOR: '处置记录报告'
} as Record<string, string>)[role.value] ?? '医技报告'));

const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;

// Queue state
const orders = ref<MedicalOrder[]>([]);
const current = ref<MedicalOrder>();
const queueKeyword = ref('');
const queueTab = ref<'all' | 'waiting' | 'done'>('all');
const mainTab = ref<'work' | 'report'>('work');
const refreshing = ref(false);

// Report state
const report = reactive({ findings: '', conclusion: '', advice: '' });
const confirmedAt = ref('');
const published = ref(false);

// AI state
const aiModel = ref('');
const aiFallback = ref(false);
const aiMessages = ref<Array<{ id: string; label: string; content: string; kind: string }>>([]);
const aiModelLabel = computed(() => !aiModel.value ? '未生成' : aiFallback.value ? `${aiModel.value}/Mock` : aiModel.value);

// Imaging state (CHECK_DOCTOR)
const file = ref<File>();
const imagePreviewUrl = ref('');
const aiTaskId = ref('');
const aiStatus = ref('');

// Volume state
const volume = ref<VolumeData | null>(null);
const volLoading = ref(false);
const sliceZ = ref(0);
const sliceY = ref(0);
const sliceX = ref(0);
const winC = ref(40);
const winW = ref(80);

// Canvas refs
const canvasAxial    = ref<HTMLCanvasElement>();
const canvasCoronal  = ref<HTMLCanvasElement>();
const canvasSagittal = ref<HTMLCanvasElement>();
const canvas3D       = ref<HTMLCanvasElement>();

// 3D renderer
const renderer3D = ref<VolumeRenderer3D | null>(null);
const azim3D = ref(220);
const elev3D = ref(18);

let drag3D = false, lastX3D = 0, lastY3D = 0;
function on3DDown(e: MouseEvent)  { drag3D = true; lastX3D = e.clientX; lastY3D = e.clientY; }
function on3DMove(e: MouseEvent)  {
  if (!drag3D) return;
  azim3D.value = (azim3D.value + (e.clientX - lastX3D) * 0.5) % 360;
  elev3D.value = Math.max(-85, Math.min(85, elev3D.value - (e.clientY - lastY3D) * 0.4));
  lastX3D = e.clientX; lastY3D = e.clientY;
}
function on3DUp()   { drag3D = false; }

// MPR render helpers
function onWheelPanel(plane: 'axial' | 'coronal' | 'sagittal', e: WheelEvent) {
  if (!volume.value) return
  const delta = e.deltaY > 0 ? 1 : -1
  if (plane === 'axial')    sliceZ.value = Math.max(0, Math.min(volume.value.nz - 1, sliceZ.value + delta))
  else if (plane === 'coronal')  sliceY.value = Math.max(0, Math.min(volume.value.ny - 1, sliceY.value + delta))
  else                           sliceX.value = Math.max(0, Math.min(volume.value.nx - 1, sliceX.value + delta))
}

function rerenderMpr() {
  const vol = volume.value;
  if (!vol) return;
  if (canvasAxial.value)    renderAxial(canvasAxial.value,       vol, sliceZ.value, winC.value, winW.value);
  if (canvasCoronal.value)  renderCoronal(canvasCoronal.value,   vol, sliceY.value, winC.value, winW.value);
  if (canvasSagittal.value) renderSagittal(canvasSagittal.value, vol, sliceX.value, winC.value, winW.value);
}
function rerender3D() { renderer3D.value?.render(azim3D.value, elev3D.value, winC.value, winW.value); }

watch(sliceZ, () => { const v = volume.value; if (v && canvasAxial.value)    renderAxial(canvasAxial.value,       v, sliceZ.value, winC.value, winW.value); });
watch(sliceY, () => { const v = volume.value; if (v && canvasCoronal.value)  renderCoronal(canvasCoronal.value,   v, sliceY.value, winC.value, winW.value); });
watch(sliceX, () => { const v = volume.value; if (v && canvasSagittal.value) renderSagittal(canvasSagittal.value, v, sliceX.value, winC.value, winW.value); });
watch([azim3D, elev3D], rerender3D);
watch([winC, winW], () => { rerenderMpr(); rerender3D(); });

watch(volume, async (vol) => {
  // destroy old renderer
  renderer3D.value?.destroy();
  renderer3D.value = null;
  if (!vol) return;
  sliceZ.value = Math.floor(vol.nz / 2);
  sliceY.value = Math.floor(vol.ny / 2);
  sliceX.value = Math.floor(vol.nx / 2);
  await nextTick();
  rerenderMpr();
  if (canvas3D.value) {
    try {
      canvas3D.value.width  = canvas3D.value.clientWidth  || 400;
      canvas3D.value.height = canvas3D.value.clientHeight || 400;
      renderer3D.value = new VolumeRenderer3D(canvas3D.value, vol);
      rerender3D();
    } catch (e) { ElMessage.warning('WebGL2 不可用，3D 视图已跳过'); }
  }
});

function setWindow(preset: 'brain' | 'bone' | 'lung' | 'soft') {
  const map = { brain: [40, 80], bone: [400, 1800], lung: [-600, 1500], soft: [60, 400] } as const;
  [winC.value, winW.value] = map[preset];
}

// Lab state (LAB_DOCTOR)
const lab = reactive({ specimenType: '全血', barcode: `LAB-${Date.now()}`, itemName: '血红蛋白', value: '135', unit: 'g/L' });
const specimenId = ref('');

// Computed queue stats
const waitingCount = computed(() => orders.value.filter(o => ['WAITING','CALLED'].includes(o.status)).length);
const doneCount = computed(() => orders.value.filter(o => o.status === 'COMPLETED').length);

const filteredOrders = computed(() => {
  let list = orders.value;
  if (queueTab.value === 'waiting') list = list.filter(o => ['WAITING','CALLED'].includes(o.status));
  else if (queueTab.value === 'done') list = list.filter(o => o.status === 'COMPLETED');
  const kw = queueKeyword.value.trim().toLowerCase();
  return kw ? list.filter(o => `${o.patientName}${o.itemName}`.toLowerCase().includes(kw)) : list;
});

function statusLabel(s: string) {
  return { WAITING: '待执行', CALLED: '已叫号', IN_PROGRESS: '执行中', COMPLETED: '已完成', MISSED: '过号' }[s] ?? s;
}

function statusTagType(s: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (s === 'WAITING') return 'warning';
  if (s === 'CALLED') return 'primary';
  if (s === 'IN_PROGRESS') return 'primary';
  if (s === 'COMPLETED') return 'success';
  return 'info';
}

function formatOrderType(t: string) {
  return { CHECK: '检查', LAB: '检验', DISPOSAL: '处置' }[t] ?? t;
}

function aiStatusTagType(s: string): '' | 'success' | 'warning' | 'info' {
  if (s === 'COMPLETED') return 'success';
  if (s === 'PROCESSING') return 'warning';
  return 'info';
}

function aiStatusLabel(s: string) {
  return { COMPLETED: '分析完成', PROCESSING: '分析中', FAILED: '分析失败' }[s] ?? (s || '未提交');
}

async function loadOrders() { orders.value = await getMedicalOrders(); }

async function refreshOrders() {
  refreshing.value = true;
  await loadOrders();
  refreshing.value = false;
}

function select(row: MedicalOrder) {
  current.value = row;
  Object.assign(report, { findings: '', conclusion: '', advice: '' });
  file.value = undefined;
  imagePreviewUrl.value = '';
  volume.value = null;
  aiTaskId.value = '';
  aiStatus.value = '';
  aiMessages.value = [];
  aiModel.value = '';
  specimenId.value = '';
  confirmedAt.value = '';
  published.value = false;
  mainTab.value = 'work';
}

async function call(row: MedicalOrder) {
  await callMedicalOrder(row.id);
  ElMessage.success('已叫号');
  await loadOrders();
}

async function start(row: MedicalOrder) {
  await startMedicalOrder(row.id);
  ElMessage.success('已开始执行');
  await loadOrders();
}

async function miss(row: MedicalOrder) {
  await missMedicalOrder(row.id);
  await loadOrders();
}

const VOLUME_EXTS = ['.nii', '.nii.gz', '.nrrd', '.nhdr', '.mha'];

function isVolumeFile(f: File): boolean {
  const name = f.name.toLowerCase();
  return VOLUME_EXTS.some(ext => name.endsWith(ext));
}

async function loadFile(f: File) {
  file.value = f;
  imagePreviewUrl.value = '';
  volume.value = null;
  if (isVolumeFile(f)) {
    volLoading.value = true;
    try {
      volume.value = await readVolume(f);
      const vol = volume.value;
      winC.value = Math.round(vol.wc);
      winW.value = Math.round(vol.ww);
      ElMessage.success(`影像加载成功：${vol.nx}×${vol.ny}×${vol.nz} 体素`);
    } catch (e: unknown) {
      ElMessage.error(String((e as Error).message ?? e));
      file.value = undefined;
    } finally {
      volLoading.value = false;
    }
  } else if (f.type.startsWith('image/')) {
    imagePreviewUrl.value = URL.createObjectURL(f);
  }
}

function chooseFile(event: Event) {
  const f = (event.target as HTMLInputElement).files?.[0];
  if (f) loadFile(f);
  (event.target as HTMLInputElement).value = '';
}

/** Python 微服务地址（dicom-service/app.py） */
const DICOM_SERVICE = 'http://localhost:8765';

async function loadDicomFolder(event: Event) {
  const input = event.target as HTMLInputElement;
  const fileList = input.files;
  if (!fileList || fileList.length === 0) return;
  const files = Array.from(fileList);   // snapshot before clearing
  input.value = '';
  file.value = files[0];
  imagePreviewUrl.value = '';
  volume.value = null;
  volLoading.value = true;

  try {
    // ── 优先尝试 Python 微服务（处理所有压缩格式）────────────────
    let vol: import('../../utils/volumeReader').VolumeData | null = null;
    let serviceOk = false;
    try {
      const hc = await fetch(`${DICOM_SERVICE}/health`, { signal: AbortSignal.timeout(1500) });
      serviceOk = hc.ok;
    } catch { /* 服务未启动 */ }

    if (serviceOk) {
      ElMessage.info(`正在通过 Python 服务解析 ${files.length} 个 DICOM 文件…`);
      const form = new FormData();
      for (const f of files) form.append('files', f, f.name);
      const resp = await fetch(`${DICOM_SERVICE}/dicom2nii`, { method: 'POST', body: form });
      if (!resp.ok) {
        const msg = await resp.text();
        throw new Error(`Python 服务返回错误: ${msg}`);
      }
      const niftiBytes = await resp.arrayBuffer();
      const { readVolume } = await import('../../utils/volumeReader');
      // 将字节包装成 File 对象让 readVolume 识别 .nii.gz
      const niftiFile = new File([niftiBytes], 'volume.nii.gz', { type: 'application/gzip' });
      vol = await readVolume(niftiFile);
    } else {
      // ── 回退：JS 原生解析器（仅支持未压缩 Explicit VR LE）──────
      ElMessage.info(`Python 服务未启动，尝试浏览器内解析…`);
      vol = await readDicomSeries(files);
    }

    volume.value = vol;
    winC.value = Math.round(vol.wc);
    winW.value = Math.round(vol.ww);
    ElMessage.success(`影像加载成功：${vol.nx}×${vol.ny}×${vol.nz} 体素`);
  } catch (e: unknown) {
    const msg = String((e as Error).message ?? e);
    ElMessage.error(msg);
    file.value = undefined;
  } finally {
    volLoading.value = false;
  }
}

function handleDrop(event: DragEvent) {
  const f = event.dataTransfer?.files?.[0];
  if (f) loadFile(f);
}

function clearFile() {
  file.value = undefined;
  imagePreviewUrl.value = '';
  volume.value = null;
}

async function uploadCt() {
  if (!current.value || !file.value) return;
  const attachment = await uploadAttachment(current.value.id, file.value);
  const task = await submitCt(current.value.id, attachment.id);
  aiTaskId.value = task.externalTaskId;
  aiStatus.value = task.status;
  ElMessage.success('CT AI 任务已提交');
}

async function pollAi() {
  const task = await refreshAiTask(aiTaskId.value);
  aiStatus.value = task.status;
  if (task.status === 'COMPLETED') ElMessage.success('AI 分析完成，已同步至报告草稿');
}

async function prepareSpecimen() {
  if (!current.value) return;
  const specimen = await createSpecimen(current.value.id, lab.specimenType, lab.barcode);
  specimenId.value = specimen.id;
  for (const status of ['COLLECTED', 'RECEIVED', 'ANALYZING']) await transitionSpecimen(specimen.id, status);
  ElMessage.success('样本已进入分析阶段');
}

async function saveLab() {
  if (!current.value) return;
  await saveLabResults(current.value.id, specimenId.value, [{
    itemCode: 'HGB', itemName: lab.itemName, resultValue: lab.value,
    unit: lab.unit, referenceRange: '115-150', abnormalFlag: 'NORMAL', createdByType: 'HUMAN'
  }]);
  ElMessage.success('检验指标已保存');
}

async function generateAiDraft() {
  if (!current.value) return;
  const draft = await createAiReportDraft({
    orderId: current.value.id,
    reportType: current.value.orderType,
    itemName: current.value.itemName,
    findings: report.findings,
    conclusion: report.conclusion,
    context: current.value.purpose || current.value.bodyPart || ''
  });
  aiModel.value = draft.model;
  aiFallback.value = draft.fallbackUsed;
  report.findings = draft.findings;
  report.conclusion = draft.conclusion;
  report.advice = draft.advice;
  aiMessages.value = [
    { id: 'f', label: '检查所见建议', content: draft.findings, kind: 'findings' },
    { id: 'c', label: '结论建议', content: draft.conclusion, kind: 'conclusion' },
  ].filter(m => m.content);
  await saveReportDraft(current.value.id, report);
  ElMessage.success('AI 报告草稿已生成并保存');
  mainTab.value = 'report';
}

function applyToFindings(content: string) { report.findings = content; mainTab.value = 'report'; }
function applyToConclusion(content: string) { report.conclusion = content; mainTab.value = 'report'; }

async function saveDraft() {
  if (!current.value) return;
  await saveReportDraft(current.value.id, report);
  ElMessage.success('报告草稿已保存');
}

async function confirmAndPublish() {
  if (!current.value) return;
  await confirmReport(current.value.id, report);
  confirmedAt.value = today;
  published.value = true;
  ElMessage.success('正式报告已发布');
  await loadOrders();
}

function printReport() {
  mainTab.value = 'report';
  nextTick(() => window.print());
}

function logout() { auth.signOut(); router.push('/login'); }

onMounted(loadOrders);
</script>

<style scoped>
/* ── Root ── */
.wks {
  min-height: 100vh;
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
  font-size: 20px; font-weight: 900; line-height: 1; flex-shrink: 0;
}
.wks-nav__title { font-size: 16px; font-weight: 600; }
.wks-nav__right { display: flex; align-items: center; gap: 20px; font-size: 13px; }
.wks-nav__info { opacity: 0.9; }
.wks-nav__date { opacity: 0.8; }

/* ── Body layout ── */
.wks-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  height: calc(100vh - 52px);
}

/* ── Left sidebar ── */
.wks-sidebar {
  width: 250px;
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
  font-size: 15px; font-weight: 700;
}
.sidebar-search-wrap { padding: 0 12px 8px; }
.sidebar-tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
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
.qcard__top { display: flex; align-items: center; gap: 6px; margin-bottom: 3px; }
.qcard__num { font-size: 12px; color: #9ca3af; min-width: 18px; }
.qcard__name { font-size: 15px; font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qcard__proj { font-size: 12px; color: #374151; padding-left: 24px; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.qcard__sub { display: flex; justify-content: space-between; align-items: center; padding-left: 24px; margin-bottom: 4px; }
.qcard__type { font-size: 11px; color: #9ca3af; }
.qcard__ops { padding-left: 22px; display: flex; gap: 2px; }
.sidebar-footer { padding: 8px 14px; border-top: 1px solid #e5e7eb; font-size: 12px; color: #9ca3af; text-align: center; }

/* ── Main content ── */
.wks-main {
  flex: 1; min-width: 0; overflow: hidden;
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

/* Tabs */
.main-tabs {
  display: flex;
  background: #fff; border-radius: 8px 8px 0 0;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}
.mtab {
  padding: 11px 24px; border: none; background: none;
  font-size: 14px; color: #6b7280; cursor: pointer;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  transition: all 0.15s;
}
.mtab--active { color: #0899a5; border-bottom-color: #0cbdcc; font-weight: 600; }
.mtab:hover:not(.mtab--active) { color: #374151; }

.main-content {
  background: #fff; border-radius: 0 0 8px 8px; padding: 18px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 5%);
  flex: 1;
}

/* ── main-content viewer override ── */
.main-content--viewer {
  padding: 0;
  background: transparent;
  border-radius: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* ── CT Viewer (CHECK_DOCTOR) ── */
.ct-viewer {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #0d1117;
  overflow: hidden;
  min-height: 0;
}

/* Toolbar */
.ct-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 38px;
  padding: 0 10px;
  background: #111827;
  border-bottom: 1px solid #1f2937;
  flex-shrink: 0;
}
.ct-tool {
  display: flex; align-items: center; justify-content: center;
  width: 30px; height: 28px;
  border: none; background: transparent;
  color: #9ca3af; font-size: 14px;
  border-radius: 4px; cursor: pointer;
  transition: background 0.12s, color 0.12s;
}
.ct-tool:hover { background: #1f2937; color: #e5e7eb; }
.ct-tool--active { background: #1e3a5f; color: #38bdf8; }
.ct-sep { width: 1px; height: 20px; background: #1f2937; margin: 0 4px; flex-shrink: 0; }
.ct-sep--flex { flex: 1; width: auto; background: transparent; }
.ct-ai-badge {
  font-size: 11px; padding: 3px 8px;
  border-radius: 20px; white-space: nowrap;
}
.ct-ai-badge--running { background: #1e3a20; color: #4ade80; }
.ct-ai-badge--done { background: #1c3557; color: #38bdf8; }

/* 2×2 panels grid */
.ct-panels {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 2px;
  background: #020617;
  min-height: 0;
}

.ct-panel {
  position: relative;
  background: #000;
  overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  cursor: crosshair;
}
.ct-panel + .ct-panel { border-left: 1px solid #0a1628; }
.ct-panel:nth-child(3),
.ct-panel:nth-child(4) { border-top: 1px solid #0a1628; }

/* Panel overlays */
.ct-panel__lbl {
  position: absolute; top: 8px; left: 10px;
  font-size: 11px; color: #fff;
  background: rgba(0,0,0,0.55);
  padding: 2px 7px; border-radius: 3px;
  font-weight: 500; pointer-events: none; z-index: 5;
}
.ct-panel__slice {
  position: absolute; top: 8px; right: 10px;
  font-size: 11px; color: #fff;
  background: #1d4ed8;
  padding: 2px 8px; border-radius: 12px;
  pointer-events: none; z-index: 5;
}
.ct-panel__img {
  width: 100%; height: 100%;
  object-fit: contain;
}

/* Crosshair lines */
.ct-line {
  position: absolute; background: rgba(0, 188, 212, 0.5);
  pointer-events: none; z-index: 4;
}
.ct-line--h { left: 0; right: 0; top: 50%; height: 1px; }
.ct-line--v { top: 0; bottom: 0; left: 50%; width: 1px; }

/* Orientation labels */
.ct-orient {
  position: absolute; font-size: 12px; font-weight: 700;
  color: rgba(255,255,255,0.7); pointer-events: none; z-index: 5;
}
.ct-orient--ml { left: 10px; top: 50%; transform: translateY(-50%); }
.ct-orient--mr { right: 10px; top: 50%; transform: translateY(-50%); }
.ct-orient--tl { top: 30px; left: 10px; }
.ct-orient--bl { bottom: 30px; left: 10px; }

/* Scale bar */
.ct-scale {
  position: absolute; bottom: 10px; right: 10px;
  font-size: 10px; color: rgba(255,255,255,0.65);
  display: flex; align-items: center; gap: 4px; pointer-events: none; z-index: 5;
}
.ct-scale::before {
  content: '';
  display: inline-block; width: 36px; height: 2px;
  background: rgba(255,255,255,0.5);
}

/* Upload drop zone */
.ct-drop {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 8px; cursor: pointer;
  width: 100%; height: 100%;
  text-align: center; padding: 20px;
  box-sizing: border-box;
}
.ct-drop:hover { background: rgba(255,255,255,0.02); }
.ct-drop__icon { font-size: 36px; color: #374151; margin-bottom: 4px; }
.ct-drop__text { font-size: 13px; color: #6b7280; }
.ct-drop__sub { font-size: 11px; color: #374151; }

/* DICOM hint (file selected, no preview) */
.ct-dicom-hint { text-align: center; color: #6b7280; padding: 20px; }
.ct-dicom-hint__icon { font-size: 40px; margin-bottom: 8px; }
.ct-dicom-hint__name { font-size: 13px; color: #9ca3af; word-break: break-all; }
.ct-dicom-hint__sub { font-size: 11px; margin-top: 4px; }

/* Placeholder for non-axial panels */
.ct-placeholder { text-align: center; pointer-events: none; }
.ct-placeholder__icon { font-size: 32px; color: #1f2937; margin-bottom: 8px; }
.ct-placeholder__text { font-size: 13px; color: #374151; }
.ct-placeholder__sub { font-size: 11px; color: #1f2937; margin-top: 4px; }

/* Canvas — use smooth (bilinear) interpolation for medical CT display */
.ct-panel__canvas {
  max-width: 100%;
  max-height: calc(100% - 22px);
  image-rendering: auto;    /* bilinear when scaled, avoids blocky pixel look */
  display: block;
}
/* WebGL 3D canvas fills panel completely */
.ct-panel--3d { cursor: grab; }
.ct-panel--3d:active { cursor: grabbing; }
.ct-panel__canvas--3d {
  width: 100% !important;
  height: 100% !important;
  max-height: 100%;
  image-rendering: auto;
}
.ct-3d-hint {
  position: absolute; bottom: 8px; left: 50%;
  transform: translateX(-50%);
  font-size: 10px; color: rgba(255,255,255,0.35);
  pointer-events: none; z-index: 5; white-space: nowrap;
}

/* Slice slider (at panel bottom) */
.ct-slider {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  width: 100%;
  height: 18px;
  margin: 0;
  padding: 0;
  appearance: none;
  background: rgba(0, 0, 0, 0.55);
  cursor: pointer;
  z-index: 8;
}
.ct-slider::-webkit-slider-thumb {
  appearance: none;
  width: 14px; height: 14px;
  border-radius: 50%;
  background: #38bdf8;
  cursor: pointer;
}
.ct-slider::-webkit-slider-runnable-track {
  height: 3px;
  background: rgba(56, 189, 248, 0.35);
}

/* Loading spinner */
.ct-loading {
  color: #9ca3af; font-size: 13px; animation: pulse 1.4s ease-in-out infinite;
}
@keyframes pulse { 0%, 100% { opacity: 0.5; } 50% { opacity: 1; } }

/* W/L inline inputs */
.ct-wl-lbl { font-size: 11px; color: #6b7280; white-space: nowrap; }
.ct-wl-inp {
  width: 64px; height: 28px;
  background: #1f2937; color: #e5e7eb;
  border: 1px solid #374151; border-radius: 4px;
  padding: 0 6px; font-size: 12px;
  text-align: center;
}
.ct-wl-inp:focus { outline: none; border-color: #38bdf8; }
.ct-sep--sm { width: 1px; height: 20px; background: #1f2937; margin: 0 4px; }

/* Clear button */
.ct-clear {
  position: absolute; top: 8px; right: 50px;
  background: rgba(0,0,0,0.6); color: #9ca3af;
  border: 1px solid #374151; border-radius: 4px;
  padding: 2px 7px; font-size: 11px; cursor: pointer; z-index: 10;
}
.ct-clear:hover { color: #fff; background: rgba(0,0,0,0.85); }

/* Bottom action toolbar */
.ct-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 46px;
  padding: 0 12px;
  background: #111827;
  border-top: 1px solid #1f2937;
  flex-shrink: 0;
}
.ct-act {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 14px;
  border: none; background: #1f2937;
  color: #9ca3af; font-size: 12px;
  border-radius: 5px; cursor: pointer;
  transition: background 0.12s, color 0.12s;
  white-space: nowrap;
}
.ct-act:hover:not(:disabled):not(.ct-act--disabled) { background: #273549; color: #e5e7eb; }
.ct-act:disabled,
.ct-act--disabled { opacity: 0.4; cursor: not-allowed; }
.ct-act-gap { flex: 1; }
.ct-act--primary {
  background: #0899a5; color: #fff;
}
.ct-act--primary:hover:not(:disabled) { background: #0cbdcc; }
.ct-act--report {
  background: #1e3a5f; color: #93c5fd;
}
.ct-act--report:hover { background: #1d4ed8; color: #fff; }

/* ── Lab section (LAB_DOCTOR) ── */
.lab-section { display: flex; flex-direction: column; gap: 10px; }
.lab-block-title { font-size: 14px; font-weight: 600; color: #374151; border-left: 3px solid #0cbdcc; padding-left: 8px; }
.lab-grid { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.lab-field { display: flex; flex-direction: column; gap: 4px; min-width: 140px; }
.lab-field label { font-size: 12px; color: #6b7280; }

/* ── Disposal section ── */
.disposal-section { display: flex; flex-direction: column; gap: 10px; }

/* ── Formal report document ── */
.med-report {
  font-family: "SimSun", "宋体", "Microsoft YaHei", sans-serif;
  font-size: 14px; color: #111;
  max-width: 720px; margin: 0 auto;
}

.med-report__hospital {
  text-align: center;
  font-size: 20px; font-weight: bold;
  letter-spacing: 3px;
  padding-bottom: 4px;
}
.med-report__title {
  text-align: center;
  font-size: 17px; font-weight: bold;
  letter-spacing: 6px;
  padding-bottom: 10px;
}
.med-report__rule-thick { border: none; border-top: 3px double #444; margin-bottom: 14px; }
.med-report__rule { border: none; border-top: 1px solid #bbb; margin: 14px 0; }

.med-report__info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px 16px;
  margin-bottom: 10px;
}
.rinfo-cell {
  display: flex; gap: 4px;
  border-bottom: 1px solid #ccc;
  padding: 3px 2px;
  font-size: 13.5px;
}
.rinfo-cell em { color: #555; font-style: normal; white-space: nowrap; margin-right: 2px; }
.rinfo-cell span { flex: 1; }

.med-report__clinical {
  display: flex; gap: 8px; align-items: baseline;
  margin-bottom: 4px; font-size: 13.5px;
}
.med-report__clinical em { color: #555; font-style: normal; white-space: nowrap; }
.med-report__clinical span { flex: 1; border-bottom: 1px solid #ccc; padding: 2px; }

.med-report__section { margin-bottom: 6px; }
.med-report__section-lbl {
  font-size: 14px; font-weight: 600;
  margin-bottom: 6px; color: #111;
}
.med-report__section-lbl--emphasis { color: #0899a5; }
.med-report__area {
  width: 100%; box-sizing: border-box;
  border: none; border-bottom: 1px solid #bbb;
  outline: none; background: transparent;
  font-family: inherit; font-size: 14px; color: #111;
  resize: vertical; padding: 4px 2px; line-height: 1.9; min-height: 60px;
}
.med-report__area:focus { border-bottom-color: #0899a5; }
.med-report__area--emphasis { font-weight: 600; font-size: 14.5px; color: #0899a5; }

/* Signature footer */
.med-report__sig-footer {
  display: flex; align-items: flex-end; justify-content: space-between;
  padding: 8px 0;
}
.sig-block { display: flex; flex-direction: column; gap: 8px; }
.sig-row { display: flex; align-items: baseline; gap: 6px; font-size: 13.5px; }
.sig-row--sign { margin: 4px 0; }
.sig-label { color: #555; white-space: nowrap; }
.sig-name-print { font-weight: 600; }
.sig-cursive {
  font-family: "STKaiti", "KaiTi", "楷体", cursive;
  font-size: 22px;
  color: #111;
  letter-spacing: 2px;
  line-height: 1;
  transform: rotate(-3deg);
  display: inline-block;
}
.sig-date { color: #333; }

/* Stamp */
.stamp-block { display: flex; align-items: center; justify-content: center; padding-right: 20px; }
.stamp-circle {
  width: 72px; height: 72px;
  border: 2.5px solid #aaa;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #aaa; font-size: 13px; font-weight: bold;
  transform: rotate(-20deg);
  opacity: 0.7;
  letter-spacing: 1px;
}
.stamp-circle--published {
  border-color: #c00;
  color: #c00;
  opacity: 0.75;
}

.med-report__notice {
  font-size: 11.5px; color: #777;
  border-top: 1px solid #e5e7eb;
  padding-top: 8px; margin-top: 10px;
}

.med-report__actions {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 16px; padding-top: 12px;
  border-top: 1px dashed #e5e7eb;
}

/* ── AI panel ── */
.wks-ai {
  width: 300px; flex-shrink: 0;
  overflow-y: auto; padding: 12px;
  border-left: 1px solid #e5e7eb; background: #f8fafc;
}
.ai-card { border-color: #a8e8ec; }
.ai-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.context-block { margin: 12px 0; padding: 10px 12px; border-radius: 8px; background: #f0f9fa; }
.context-block p { margin: 4px 0 0; font-size: 13px; }
.ai-messages { min-height: 120px; max-height: 300px; overflow-y: auto; margin-bottom: 10px; }
.ai-message {
  margin-bottom: 10px; padding: 10px;
  border-left: 3px solid #0cbdcc; background: #e6f9fa;
  border-radius: 0 4px 4px 0;
}
.ai-msg-label { font-weight: 700; font-size: 12px; color: #0899a5; }
.ai-message p { margin: 4px 0 0; font-size: 13px; color: #374151; }
.ai-action { margin-top: 8px; }
.full { width: 100%; }
.muted { color: #9ca3af; font-size: 12px; }

/* ── Print ── */
@media print {
  .wks-nav, .wks-sidebar, .wks-ai, .patient-hdr,
  .main-tabs, .no-print { display: none !important; }

  .wks { background: #fff; }
  .wks-body { height: auto; overflow: visible; display: block; }
  .wks-main { padding: 0; overflow: visible; }
  .main-content { box-shadow: none; border-radius: 0; padding: 0; }
  .med-report { max-width: 100%; }
  .med-report__area { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
}
</style>
