<template>
  <div class="wks">
    <!-- ── Navbar ── -->
    <header class="wks-nav">
      <div class="wks-nav__brand">
        <span class="wks-nav__logo">+</span>
        <span class="wks-nav__title">{{ roleLabel }}</span>
      </div>
      <div class="wks-nav__right">
        <span class="wks-nav__info">{{ auth.user?.name }}</span>
        <span class="wks-nav__date">{{ today }} {{ dayOfWeek }}</span>
        <button :class="['my-entry', showMySchedule && 'my-entry--active']" type="button" @click="showMySchedule = !showMySchedule">
          {{ showMySchedule ? '返回工作台' : '我的' }}
        </button>
        <el-button size="small" text @click="logout" style="color:rgba(255,255,255,0.85)">退出</el-button>
      </div>
    </header>

    <!-- ── Body ── -->
    <div class="wks-body">

      <!-- Left: queue sidebar -->
      <aside v-if="!showMySchedule" class="wks-sidebar">
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
              <el-tag :type="statusTagType(item.status)" size="small" effect="plain">{{ orderStatusLabel(item) }}</el-tag>
              <span class="qcard__type">{{ formatOrderType(item.orderType) }}</span>
            </div>
            <div class="qcard__ops" @click.stop>
              <el-button v-if="isPathologyItem(item) && ['WAITING','CALLED'].includes(item.status)" size="small" type="success" link @click="start(item)">接收送检</el-button>
              <el-button v-if="!isPathologyItem(item) && item.status === 'WAITING'" size="small" type="primary" link @click="call(item)">叫号</el-button>
              <el-button v-if="!isPathologyItem(item) && item.status === 'CALLED'" size="small" type="success" link @click="start(item)">开始执行</el-button>
              <el-button v-if="!isPathologyItem(item) && ['WAITING','CALLED'].includes(item.status)" size="small" link @click="miss(item)">过号</el-button>
            </div>
          </div>
          <div v-if="!filteredOrders.length" class="queue-empty">暂无医嘱</div>
        </div>
        <div class="sidebar-footer">共 {{ orders.length }} 条</div>
      </aside>

      <!-- Center: main content -->
      <main v-if="showMySchedule" class="wks-main wks-main--schedule">
        <DoctorPersonalSchedule />
      </main>

      <main v-else class="wks-main">
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
                <el-tag :type="statusTagType(current.status)" size="small" effect="plain">{{ orderStatusLabel(current) }}</el-tag>
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
                <div v-if="volume || aiStatus" class="ct-toolbar">
                  <div class="ct-view-tabs" v-if="volume">
                    <button type="button" :class="['ct-view-tab', ctViewerMode === 'mpr' && 'ct-view-tab--active']" @click.stop="setCtViewerMode('mpr')">MPR/3D</button>
                    <button type="button" :class="['ct-view-tab', ctViewerMode === 'film' && 'ct-view-tab--active']" @click.stop="setCtViewerMode('film')">多切片胶片</button>
                  </div>
                  <div v-if="volume" class="ct-window-tools">
                    <span class="ct-wl-lbl">WL</span>
                    <input class="ct-wl-inp" type="number" v-model.number="winC" @change="onManualWindowChange" />
                    <span class="ct-wl-lbl">WW</span>
                    <input class="ct-wl-inp" type="number" v-model.number="winW" @change="onManualWindowChange" />
                    <div class="ct-sep ct-sep--sm"></div>
                    <button :class="['ct-act ct-act--compact', windowPreset === 'brain' && 'ct-act--active']" @click="setWindow('brain')">脑窗</button>
                    <button :class="['ct-act ct-act--compact', windowPreset === 'standard' && 'ct-act--active']" @click="setWindow('standard')">标准</button>
                    <button :class="['ct-act ct-act--compact', windowPreset === 'subdural' && 'ct-act--active']" @click="setWindow('subdural')">硬膜下</button>
                    <button :class="['ct-act ct-act--compact', windowPreset === 'bone' && 'ct-act--active']" @click="setWindow('bone')">骨窗</button>
                    <button :class="['ct-act ct-act--compact', windowPreset === 'soft' && 'ct-act--active']" @click="setWindow('soft')">软组织</button>
                  </div>
                  <div class="ct-toolbar__spacer"></div>
                  <span v-if="aiStatus === 'PROCESSING'" class="ct-ai-badge ct-ai-badge--running">AI 分析中…</span>
                  <span v-else-if="aiStatus === 'COMPLETED'" class="ct-ai-badge ct-ai-badge--done">✓ AI 已完成</span>
                </div>

                <!-- 2×2 panels -->
                <div v-show="ctViewerMode === 'mpr'" class="ct-panels">

                  <!-- Axial -->
                  <div class="ct-panel" @dragover.prevent @drop.prevent="handleDrop" @wheel.prevent="onWheelPanel('axial', $event)">
                    <span class="ct-panel__lbl">【轴检 Axial】</span>
                    <span class="ct-panel__slice" v-if="volume">切片: {{ sliceZ + 1 }} / {{ volume.nz }}</span>

                    <div v-if="volLoading" class="ct-loading">解析体积影像中…</div>

                    <template v-else-if="volume">
                      <canvas ref="canvasAxial" class="ct-panel__canvas"></canvas>
                      <div v-if="showImageAiOverlay" class="ct-ai-overlay">
                        <div class="ct-ai-overlay__head">
                          <span>AI 结果叠加</span>
                          <strong>{{ overlaySliceLabel }}</strong>
                        </div>
                        <button
                          v-for="item in imageAiFindings"
                          :key="item.id"
                          :class="['ct-ai-marker', `ct-ai-marker--${item.tone}`]"
                          type="button"
                          :style="{ left: item.x, top: item.y }"
                          @click.stop="focusAiFinding(item)"
                        >
                          <span class="ct-ai-marker__dot"></span>
                          <span>{{ item.label }}</span>
                        </button>
                      </div>
                      <div class="ct-line ct-line--h"></div>
                      <div class="ct-line ct-line--v"></div>
                      <span class="ct-orient ct-orient--ml">R</span>
                      <span class="ct-orient ct-orient--mr">L</span>
                      <span class="ct-orient ct-orient--tl">S</span>
                      <span class="ct-orient ct-orient--bl">I</span>
                      <div class="ct-scale">{{ volume.dx.toFixed(2) }}mm/px</div>
                      <button class="ct-clear" @click="clearFile">✕</button>
                      <input class="ct-slider" type="range" :min="0" :max="volume.nz - 1" :value="sliceZ" @input="onSliceSlider('axial', $event)" />
                    </template>

                    <template v-else-if="imagePreviewUrl">
                      <img :src="imagePreviewUrl" class="ct-panel__img" alt="轴位" />
                      <div v-if="showImageAiOverlay" class="ct-ai-overlay">
                        <div class="ct-ai-overlay__head">
                          <span>AI 结果叠加</span>
                          <strong>{{ overlaySliceLabel }}</strong>
                        </div>
                        <button
                          v-for="item in imageAiFindings"
                          :key="item.id"
                          :class="['ct-ai-marker', `ct-ai-marker--${item.tone}`]"
                          type="button"
                          :style="{ left: item.x, top: item.y }"
                          @click.stop="focusAiFinding(item)"
                        >
                          <span class="ct-ai-marker__dot"></span>
                          <span>{{ item.label }}</span>
                        </button>
                      </div>
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
                    <div v-if="volume" class="ct-3d-controls" @mousedown.stop @click.stop>
                      <button :class="['ct-3d-preset', render3DMode === 'brain' && 'ct-3d-preset--active']" @click="set3DMode('brain')">脑实质</button>
                      <button :class="['ct-3d-preset', render3DMode === 'composite' && 'ct-3d-preset--active']" @click="set3DMode('composite')">综合</button>
                      <button :class="['ct-3d-preset', render3DMode === 'skull' && 'ct-3d-preset--active']" @click="set3DMode('skull')">颅骨参考</button>
                      <label class="ct-3d-roi">
                        ROI
                        <input type="range" min="62" max="100" :value="Math.round(render3DRoi * 100)" @input="set3DRoi" />
                      </label>
                    </div>
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
                      <input class="ct-slider" type="range" :min="0" :max="volume.ny - 1" :value="sliceY" @input="onSliceSlider('coronal', $event)" />
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
                      <input class="ct-slider" type="range" :min="0" :max="volume.nx - 1" :value="sliceX" @input="onSliceSlider('sagittal', $event)" />
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

                <div v-show="ctViewerMode === 'film'" ref="filmScrollRef" class="ct-film" @scroll="onFilmScroll">
                  <div v-if="!volume" class="ct-placeholder">
                    <div class="ct-placeholder__icon">⬡</div>
                    <div class="ct-placeholder__text">上传体积影像后显示多切片胶片</div>
                  </div>
                  <div v-else class="ct-film__spacer" :style="{ height: `${filmTotalRows * FILM_ROW_STRIDE}px` }">
                    <div class="ct-film__window" :style="{ transform: `translateY(${filmStartRow * FILM_ROW_STRIDE}px)` }">
                    <button
                      v-for="item in visibleFilmThumbs"
                      :key="item.z"
                      :class="['ct-film__cell', isFilmSliceSelected(item.z) && 'ct-film__cell--selected']"
                      @click="toggleFilmSlice(item.z)"
                      @dblclick="jumpToSlice(item.z)"
                    >
                      <img v-if="item.url" :src="item.url" :alt="`轴位切片 ${item.z + 1}`" />
                      <span v-else class="ct-film__loading">生成中</span>
                      <span class="ct-film__tag">R</span>
                      <span class="ct-film__tag ct-film__tag--posterior">P</span>
                      <span class="ct-film__idx">{{ item.z + 1 }}</span>
                    </button>
                    </div>
                  </div>
                </div>

                <!-- Bottom action toolbar -->
                <div class="ct-actions">
                  <button class="ct-act" :disabled="!volume" @click="selectCurrentFilmSlice">选当前层</button>
                  <button class="ct-act" :disabled="!volume" @click="selectRecommendedFilmSlices">推荐选片</button>
                  <button class="ct-act" :disabled="selectedFilmSlices.length === 0" @click="clearSelectedFilmSlices">清空选片</button>
                  <div class="ct-act-gap"></div>
                  <label class="ct-act ct-act--upload" :class="{ 'ct-act--disabled': !current }" title="自动识别 NIfTI、NRRD、MHA、DICOM 和常见图片">
                    上传影像
                    <input type="file" accept=".nii,.nii.gz,.nrrd,.nhdr,.mha,.dcm,.jpg,.jpeg,.png,.bmp,.tiff" multiple @change="chooseFile" style="display:none" :disabled="!current" />
                  </label>
                  <button class="ct-act ct-act--primary" :disabled="aiDiagnosisDisabled" @click="startAiDiagnosis">
                    {{ aiDiagnosisButtonText }}
                  </button>
                  <button class="ct-act" :disabled="!aiTaskId" @click="pollAi()">
                    刷新 AI
                  </button>
                  <button class="ct-act" :disabled="!current || current.status !== 'IN_PROGRESS'" @click="markPatientDone">
                    患者检查完成
                  </button>
                  <button class="ct-act ct-act--publish" :disabled="selectedFilmSlices.length === 0" @click="exportSelectedFilm">
                    导出所选胶片 {{ selectedFilmSlices.length || '' }}
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
                    <el-select v-model="lab.specimenType" size="small" clearable placeholder="请选择样本类型">
                      <el-option
                        v-for="item in specimenTypeOptions"
                        :key="item"
                        :label="item"
                        :value="item"
                      />
                    </el-select>
                  </div>
                  <div class="lab-field lab-field--barcode">
                    <label>条码号</label>
                    <el-input v-model="lab.barcode" size="small" readonly placeholder="缴费并分配诊室后自动生成" />
                  </div>
                  <div class="lab-qr-card lab-barcode-card">
                    <svg ref="labBarcodeSvg" class="lab-barcode" aria-label="样本条形码"></svg>
                    <span>{{ lab.barcode }}</span>
                  </div>
                </div>
                <el-button type="primary" size="small" :loading="samplingSubmitting" :disabled="!current || isCurrentSamplingDone" @click="prepareSpecimen">
                  {{ isCurrentSamplingDone ? '采样已完成' : '完成采样并送检' }}
                </el-button>

                <template v-if="isPathologyOrder">
                  <div class="lab-block-title" style="margin-top:20px">病理报告录入</div>
                  <div class="pathology-form">
                    <label>
                      <span>送检材料</span>
                      <el-input v-model="pathology.material" size="small" />
                    </label>
                    <label>
                      <span>肉眼所见</span>
                      <el-input v-model="pathology.gross" type="textarea" :rows="3" />
                    </label>
                    <label>
                      <span>病理诊断</span>
                      <el-input v-model="pathology.diagnosis" type="textarea" :rows="3" />
                    </label>
                  </div>
                  <div class="pathology-upload-row">
                    <label class="lab-import-btn" :class="{ 'lab-import-btn--busy': pathologyUploading }">
                      {{ pathologyUploading ? '上传中...' : '上传光镜照片' }}
                      <input type="file" accept="image/*" multiple @change="uploadPathologySlides" />
                    </label>
                    <el-button size="small" :loading="pathologySaving" :disabled="!pathology.diagnosis.trim()" @click="savePathology">
                      {{ pathologySaved ? '病理结果已保存' : '保存病理结果' }}
                    </el-button>
                  </div>
                  <div v-if="pathologySlides.length" class="pathology-slide-grid">
                    <div v-for="slide in pathologySlides" :key="slide.id" class="pathology-slide-card">
                      <img v-if="slide.previewUrl" :src="slide.previewUrl" alt="光镜照片" />
                      <div v-else class="pathology-slide-card__empty">光镜照片</div>
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div class="lab-block-title" style="margin-top:20px">检验结果导入</div>
                  <div class="lab-import-bar">
                    <label class="lab-import-btn">
                      导入 Excel
                      <input type="file" accept=".xlsx,.xls,.csv" @change="importLabExcel" />
                    </label>
                    <el-button size="small" :loading="labSaving" :disabled="!specimenId || !labResultRows.length" @click="saveLab">
                      {{ labResultsSaved ? '结果已保存' : '登记并保存检验结果' }}
                    </el-button>
                  </div>
                  <el-table v-if="labResultRows.length" :data="labResultRows" size="small" border :max-height="300">
                    <el-table-column prop="itemName" label="指标名称" min-width="160" />
                    <el-table-column prop="resultValue" label="结果值" width="120" />
                    <el-table-column prop="unit" label="单位" width="100" />
                    <el-table-column prop="referenceRange" label="参考范围" width="140" />
                    <el-table-column prop="abnormalFlag" label="提示" width="90">
                      <template #default="{ row }">
                        <el-tag :type="labFlagType(row.abnormalFlag)" size="small" effect="plain">{{ labFlagLabel(row.abnormalFlag) }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div v-else class="lab-import-empty">尚未导入检验结果 Excel</div>
                </template>
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
              <div v-if="role === 'LAB_DOCTOR' && isPathologyOrder" class="pathology-report-title">
                <strong>病 理 检 查 报 告</strong>
                <span>病理号：{{ pathologyNo }}</span>
              </div>
              <div v-else class="med-report__title">{{ reportTitle }}</div>
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

              <template v-if="role === 'LAB_DOCTOR' && isPathologyOrder">
                <div class="pathology-lines">
                  <p><b>临床诊断：</b>{{ current.purpose || '—' }}</p>
                  <p><b>送检材料：</b>{{ pathology.material || '—' }}</p>
                </div>
                <div class="pathology-report-section">
                  <b>肉眼所见：</b>
                  <p>{{ pathology.gross || '—' }}</p>
                </div>
                <div class="pathology-report-section">
                  <b>光镜所见：</b>
                  <div v-if="pathologySlides.length" class="pathology-report-images">
                    <figure v-for="slide in pathologySlides" :key="slide.id">
                      <img v-if="slide.previewUrl" :src="slide.previewUrl" alt="光镜照片" />
                    </figure>
                  </div>
                  <p v-else class="pathology-report-empty">未上传光镜照片</p>
                </div>
                <div class="pathology-diagnosis">
                  <b>病理诊断：</b>
                  <p>{{ pathology.diagnosis || report.conclusion || '—' }}</p>
                </div>
              </template>

              <template v-else-if="role === 'LAB_DOCTOR'">
                <table class="lab-report-table">
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
                    <tr v-for="row in labResultRows" :key="row.itemCode || row.itemName">
                      <td>{{ row.itemName }}</td>
                      <td class="lab-report-table__value">{{ row.resultValue }}</td>
                      <td>{{ row.unit || '—' }}</td>
                      <td>{{ row.referenceRange || '—' }}</td>
                      <td>{{ labFlagLabel(row.abnormalFlag) }}</td>
                    </tr>
                    <tr v-if="!labResultRows.length">
                      <td colspan="5" class="lab-report-table__empty">尚未导入检验结果</td>
                    </tr>
                  </tbody>
                </table>
              </template>

              <template v-else>
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
                    class="med-report__area med-report__area--single med-report__area--emphasis"
                    v-model="report.conclusion"
                    placeholder="填写检查结论或检验结果…"
                    rows="1"
                  />
                </div>
              </template>

              <!-- Advice -->
              <div class="med-report__section" style="margin-top:14px">
                <div class="med-report__section-lbl">后续建议</div>
                <textarea
                  class="med-report__area"
                  v-model="report.advice"
                  placeholder="后续复查建议、注意事项…"
                  rows="3"
                />
              </div>

              <div class="med-report__rule"></div>

              <!-- Signature footer -->
              <div class="med-report__sig-footer">
                <div class="sig-block">
                  <span><span class="sig-label">报告医师：</span><span class="sig-name-print">{{ auth.user?.name }}</span></span>
                  <span><span class="sig-label">医师签名：</span><span class="sig-cursive">{{ auth.user?.name }}</span></span>
                  <span><span class="sig-label">报告日期：</span><span class="sig-date">{{ confirmedAt || today }}</span></span>
                </div>
                <div class="stamp-block">
                  <div :class="['stamp-circle', published && 'stamp-circle--published']">
                    <span>{{ published ? '已审核' : '待审核' }}</span>
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
                  <el-button size="small" :disabled="published" @click="saveDraft">保存草稿</el-button>
                  <el-button type="success" size="small" :loading="publishing" :disabled="published" @click="confirmAndPublish">
                    {{ published ? '已发布' : (role === 'LAB_DOCTOR' ? '发布并结束检验' : '确认发布') }}
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </main>

      <!-- Right: AI panel -->
      <aside v-if="!showMySchedule" class="wks-ai">
        <el-card shadow="never" class="ai-card">
          <template #header>
            <div class="ai-header">
              <span>AI 检查辅助</span>
              <el-tag v-if="showAiModelTag" :type="aiFallback ? 'warning' : aiModel ? 'success' : 'info'" effect="plain" size="small">
                {{ aiModelLabel }}
              </el-tag>
            </div>
          </template>

          <template v-if="role === 'CHECK_DOCTOR'">
            <section class="ai-decision">
              <div class="ai-decision__top">
                <span :class="['ai-risk-dot', `ai-risk-dot--${aiRiskLevel}`]"></span>
                <div>
                  <strong>{{ aiDecisionTitle }}</strong>
                  <em v-if="aiDecisionSubtitle">{{ aiDecisionSubtitle }}</em>
                </div>
                <el-tag :type="aiStatusTagType(aiStatus)" size="small" effect="plain">{{ aiStatusLabel(aiStatus) }}</el-tag>
              </div>
              <div class="ai-decision__score">
                <span>模型置信度</span>
                <strong>{{ confidenceText(aiStructured.confidence) }}</strong>
              </div>
              <div class="ai-diagnosis-actions">
                <el-button
                  class="ai-theme-button"
                  :loading="aiSubmitting"
                  :disabled="aiDiagnosisDisabled"
                  @click="startAiDiagnosis"
                >
                  {{ aiDiagnosisButtonText }}
                </el-button>
                <el-button
                  class="ai-theme-button ai-theme-button--ghost"
                  :disabled="!aiTaskId || aiSubmitting"
                  @click="pollAi()"
                >
                  刷新判断
                </el-button>
              </div>
              <div class="ai-metric-grid">
                <div class="ai-metric">
                  <span>风险级别</span>
                  <strong>{{ aiRiskText }}</strong>
                </div>
                <div class="ai-metric">
                  <span>异常定位</span>
                  <strong>{{ aiStructured.abnormalRegions?.length || 0 }}</strong>
                </div>
                <div class="ai-metric">
                  <span>病灶分割</span>
                  <strong>{{ lesionSegText }}</strong>
                </div>
                <div class="ai-metric">
                  <span>金属伪影</span>
                  <strong>{{ metalArtifactText }}</strong>
                </div>
              </div>
            </section>

            <section class="ai-evidence-panel">
              <div class="ai-section-head">
                <span>影像证据</span>
                <strong>{{ imageAiFindings.length }}</strong>
              </div>
              <div v-if="imageAiFindings.length" class="ai-evidence-list">
                <button
                  v-for="item in imageAiFindings"
                  :key="item.id"
                  class="ai-evidence"
                  type="button"
                  @click="focusAiFinding(item)"
                >
                  <span :class="['ai-evidence__pin', `ai-evidence__pin--${item.tone}`]"></span>
                  <span>
                    <strong>{{ item.label }}</strong>
                    <em>{{ item.detail || '点击跳转至影像证据' }}</em>
                  </span>
                </button>
              </div>
              <div v-else-if="aiEvidenceEmptyText" class="ai-evidence-empty">
                {{ aiEvidenceEmptyText }}
              </div>
            </section>

            <section class="ai-judgement-panel">
              <div class="ai-section-head">
                <span>辅助判断</span>
                <strong>{{ aiMessages.length }}</strong>
              </div>
              <div v-if="aiMessages.length" class="ai-judgement-list">
                <button
                  v-for="msg in aiMessages"
                  :key="msg.id"
                  class="ai-judgement"
                  type="button"
                  @click="focusAiMessage(msg.kind)"
                >
                  <span>{{ msg.label }}</span>
                  <p>{{ msg.content }}</p>
                </button>
              </div>
              <div v-else-if="aiJudgementEmptyText" class="ai-evidence-empty">{{ aiJudgementEmptyText }}</div>
            </section>

            <section class="ai-report-build">
              <div>
                <strong>报告草稿</strong>
              </div>
              <el-button class="full ai-action ai-theme-button" :disabled="!current || !aiMessages.length" @click="generateAiDraft">
                根据判断生成报告草稿
              </el-button>
            </section>
          </template>

          <template v-else>
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
                  <el-button v-if="msg.kind === 'advice'" size="small" @click="applyToAdvice(msg.content)">
                    填入建议
                  </el-button>
                </div>
              </div>
              <el-empty v-if="!aiMessages.length" description="点击生成后在此显示" :image-size="60" />
            </div>

            <el-button type="primary" class="full ai-action" :disabled="!current" @click="generateAiDraft">
              生成 AI 后续建议
            </el-button>
          </template>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import JsBarcode from 'jsbarcode';
import * as XLSX from 'xlsx';
import { useRouter } from 'vue-router';
import DoctorPersonalSchedule from '../../components/DoctorPersonalSchedule.vue';
import { useAuthStore } from '../../store/auth';
import { useQueuePolling } from '../../composables/useQueuePolling';
import {
  callMedicalOrder, confirmReport, createReportDraft as saveReportDraft,
  createSpecimen, downloadAttachment, getLabResults, getWorkspace, missMedicalOrder,
  markMedicalOrderReportPending, refreshAiTask, saveLabResults, startMedicalOrder,
  submitCt, transitionSpecimen, uploadAttachment,
  type AiMedicalTask, type LaboratoryResultItem, type MedicalAttachment, type MedicalOrder, type MedicalReport, type Specimen
} from '../../api/medical-order';
import { createReportDraft as createAiReportDraft } from '../../api/ai';
import { readVolume, readDicomSeries, renderAxial, renderCoronal, renderSagittal, type VolumeData } from '../../utils/volumeReader';
import { VolumeRenderer3D, type VolumeRenderMode } from '../../utils/volumeRenderer3D';

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
const showMySchedule = ref(false);

// 用户正在写报告或录入结果时跳过队列轮询，避免覆盖正在输入的内容
const isEditing = computed(() => !!current.value && !published.value);

// 定时轮询队列：缴费后自动刷新待执行列表
useQueuePolling(isEditing, loadOrders);

// Report state
const report = reactive({ findings: '', conclusion: '', advice: '' });
const confirmedAt = ref('');
const published = ref(false);
const publishing = ref(false);

// AI state
const aiModel = ref('');
const aiFallback = ref(false);
const aiSubmitting = ref(false);
const aiMessages = ref<Array<{ id: string; label: string; content: string; kind: string }>>([]);
const aiModelLabel = computed(() => !aiModel.value ? '未生成' : aiFallback.value ? `${aiModel.value}/Mock` : aiModel.value);
const showAiModelTag = computed(() => {
  const model = aiModel.value.trim().toLowerCase();
  return Boolean(model) && !aiFallback.value && !model.includes('mock') && !model.includes('demo');
});
const aiStructured = ref<{
  label?: string;
  confidence?: number;
  abnormalRegions?: Array<Record<string, any>>;
  metalArtifact?: Record<string, any>;
  metalArtifactSegmentation?: Record<string, any>;
  lesionSegmentation?: Record<string, any>;
}>({});

type ImageAiFinding = {
  id: string;
  label: string;
  detail: string;
  kind: string;
  tone: 'finding' | 'risk' | 'support';
  x: string;
  y: string;
  sliceIndex?: number;
};

const imageAiFindings = computed<ImageAiFinding[]>(() => {
  if (role.value !== 'CHECK_DOCTOR') return [];
  const items: ImageAiFinding[] = [];
  const regions = Array.isArray(aiStructured.value.abnormalRegions) ? aiStructured.value.abnormalRegions : [];
  const sortedRegions = regions
    .map((region, index) => ({ region, index, distance: sliceDistance(region) }))
    .sort((a, b) => a.distance - b.distance);
  const closeRegions = sortedRegions.filter(item => item.distance <= 8);
  const visibleRegions = (closeRegions.length ? closeRegions : sortedRegions).slice(0, 3);
  visibleRegions.forEach(({ region, index }) => {
    const pos = imageRegionPosition(region, index);
    const label = regionLabel(region);
    const sliceIndex = numericValue(region.sliceIndex ?? region.slice ?? region.z);
    items.push({
      id: `region-${index}`,
      label: sliceIndex !== undefined ? `${label} 第 ${sliceIndex + 1} 层` : label,
      detail: regionDetail(region),
      kind: 'findings',
      tone: label.includes('金属') || label.includes('伪影') ? 'risk' : 'finding',
      x: pos.x,
      y: pos.y,
      sliceIndex,
    });
  });
  if (hasPositiveLesionSegmentation.value && items.length < 3) {
    const seg = aiStructured.value.lesionSegmentation || {};
    const sliceIndex = bestSegmentationSlice(seg);
    items.push({
      id: 'lesion-seg',
      label: `疑似病灶 ${seg.affectedSlices || 0}/${seg.totalSlices || 0} 层`,
      detail: seg.summary || seg.labelCn || seg.label || 'AI 已返回病灶分割摘要',
      kind: 'findings',
      tone: 'finding',
      x: '58%',
      y: '46%',
      sliceIndex,
    });
  }
  if (hasPositiveMetalSegmentation.value && items.length < 3) {
    const seg = aiStructured.value.metalArtifactSegmentation || {};
    const sliceIndex = bestSegmentationSlice(seg);
    items.push({
      id: 'metal-seg',
      label: `金属伪影 ${seg.affectedSlices || 0}/${seg.totalSlices || 0} 层`,
      detail: seg.summary || seg.labelCn || seg.label || 'AI 已返回金属伪影分割摘要',
      kind: 'advice',
      tone: 'risk',
      x: '42%',
      y: '56%',
      sliceIndex,
    });
  }
  if (hasPositiveMetalClassification() && items.length < 3) {
    const metal = aiStructured.value.metalArtifact || {};
    items.push({
      id: 'metal-classifier',
      label: metal.labelCn || '金属伪影提示',
      detail: `分类置信度 ${confidenceText(numericValue(metal.confidence))}，未返回精确分割区域`,
      kind: 'advice',
      tone: 'risk',
      x: '62%',
      y: '58%',
    });
  }
  if (isAbnormalCtLabel(aiStructured.value.label) && items.length < 3) {
    items.push({
      id: 'ct-classifier',
      label: ctLabelText(aiStructured.value.label),
      detail: `分类置信度 ${confidenceText(aiStructured.value.confidence)}，${regions.length ? '可结合检测框复核' : '未返回检测框定位'}`,
      kind: 'conclusion',
      tone: 'support',
      x: '48%',
      y: '34%',
    });
  }
  return items;
});
const showImageAiOverlay = computed(() => imageAiFindings.value.length > 0 && (Boolean(volume.value) || Boolean(imagePreviewUrl.value)));
const overlaySliceLabel = computed(() => volume.value ? `当前层 ${sliceZ.value + 1}/${volume.value.nz}` : '当前图像');
const hasPositiveMetalSegmentation = computed(() => Boolean(aiStructured.value.metalArtifactSegmentation?.enabled && aiStructured.value.metalArtifactSegmentation?.hasArtifactRegion));
const hasPositiveLesionSegmentation = computed(() => Boolean(aiStructured.value.lesionSegmentation?.enabled && aiStructured.value.lesionSegmentation?.hasLesionRegion));
const aiRiskLevel = computed<'none' | 'low' | 'medium' | 'high'>(() => {
  if (aiStatus.value === 'FAILED') return 'high';
  if (!aiStructured.value.label && !aiStatus.value) return 'none';
  if (aiStructured.value.label === 'hemorrhage') return 'high';
  if (aiStructured.value.label === 'ischemia' || hasPositiveLesionSegmentation.value) return 'medium';
  const metalLabel = String(aiStructured.value.metalArtifact?.label || '');
  if (['severe_metal', 'moderate_metal'].includes(metalLabel) || hasPositiveMetalSegmentation.value) return 'medium';
  if (aiStructured.value.label === 'normal') return 'low';
  return 'medium';
});
const aiRiskText = computed(() => ({
  none: '待分析',
  low: '低风险',
  medium: '需复核',
  high: '高风险',
}[aiRiskLevel.value]));
const aiDecisionTitle = computed(() => {
  if (!current.value) return '请选择检查';
  if (aiStatus.value === 'PROCESSING') return '正在分析影像';
  if (aiStatus.value === 'FAILED') return 'AI 分析失败';
  if (!aiStructured.value.label && !aiStatus.value) return '等待辅助判断';
  return ctLabelText(aiStructured.value.label);
});
const aiDecisionSubtitle = computed(() => {
  if (aiStatus.value === 'FAILED') return '请检查模型服务后重试';
  if (aiStatus.value === 'PROCESSING') return '';
  if (!aiStructured.value.label) return '';
  return '';
});
const lesionSegText = computed(() => {
  const seg = aiStructured.value.lesionSegmentation;
  if (!seg?.enabled) return '未启用';
  return `${seg.affectedSlices || 0}/${seg.totalSlices || 0} 层`;
});
const metalArtifactText = computed(() => {
  const metal = aiStructured.value.metalArtifact;
  if (!metal?.enabled) return '未启用';
  return metal.labelCn || metal.label || '已分析';
});
const aiEvidenceEmptyText = computed(() => {
  if (!aiStructured.value.label && !aiStatus.value) return '';
  return '';
});
const aiJudgementEmptyText = computed(() => '');
const aiDiagnosisDisabled = computed(() => !current.value || !file.value || aiSubmitting.value || aiStatus.value === 'PROCESSING');
const aiDiagnosisButtonText = computed(() => {
  if (!current.value) return '请选择检查';
  if (!file.value) return '开始诊断';
  if (aiStatus.value === 'PROCESSING') return '正在诊断';
  if (aiStatus.value === 'COMPLETED') return '重新诊断';
  if (aiStatus.value === 'FAILED') return '重新诊断';
  return '开始诊断';
});

function numericValue(value: unknown): number | undefined {
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function sliceDistance(region: Record<string, any>): number {
  const sliceIndex = numericValue(region.sliceIndex ?? region.slice ?? region.z);
  if (sliceIndex === undefined || !volume.value) return 0;
  return Math.abs(sliceIndex - sliceZ.value);
}

function imageRegionPosition(region: Record<string, any>, index: number): { x: string; y: string } {
  const bbox = Array.isArray(region.bbox) ? region.bbox.map(Number) : [];
  let rawX = numericValue(region.x ?? region.centerX ?? region.cx ?? region.left);
  let rawY = numericValue(region.y ?? region.centerY ?? region.cy ?? region.top);
  if (bbox.length >= 4 && bbox.every(Number.isFinite)) {
    rawX = (bbox[0] + bbox[2]) / 2;
    rawY = (bbox[1] + bbox[3]) / 2;
  }
  const x = rawX !== undefined ? normalizeImagePercent(rawX) : 34 + index * 18;
  const y = rawY !== undefined ? normalizeImagePercent(rawY) : 42 + index * 12;
  return {
    x: `${Math.max(14, Math.min(86, x))}%`,
    y: `${Math.max(18, Math.min(82, y))}%`,
  };
}

function normalizeImagePercent(value: number): number {
  if (value <= 1) return value * 100;
  return (value / 512) * 100;
}

function regionLabel(region: Record<string, any>): string {
  const raw = String(region.labelCn || region.label || region.type || region.source || '异常区');
  if (raw === 'hemorrhage') return '疑似出血';
  if (raw === 'ischemia') return '疑似缺血';
  if (raw === 'lesion' || raw === 'lesion_segmentation') return '疑似病灶';
  if (raw === 'metal' || raw === 'metal_artifact' || raw.includes('metal')) return '金属伪影';
  return raw;
}

function isAbnormalCtLabel(label?: string): boolean {
  return Boolean(label && !['normal', 'demo'].includes(label));
}

function ctLabelText(label?: string): string {
  if (label === 'hemorrhage') return '疑似颅内出血';
  if (label === 'ischemia') return '疑似脑缺血';
  return label ? `AI 分类：${label}` : 'AI 分类提示';
}

function hasPositiveMetalClassification(): boolean {
  const metal = aiStructured.value.metalArtifact;
  const label = String(metal?.label || '');
  return Boolean(metal?.enabled && label && !['normal', 'unknown'].includes(label));
}

function regionDetail(region: Record<string, any>): string {
  const confidence = numericValue(region.confidence ?? region.score ?? region.probability);
  const bbox = Array.isArray(region.bbox) ? `bbox ${region.bbox.join(', ')}` : '';
  const area = numericValue(region.areaRatio);
  return [
    confidence !== undefined ? `置信度 ${Math.round(confidence * 100)}%` : '',
    area !== undefined ? `面积占比 ${(area * 100).toFixed(2)}%` : '',
    bbox,
    String(region.description || region.summary || '').trim(),
  ].filter(Boolean).join('；');
}

function bestSegmentationSlice(seg?: Record<string, any>): number | undefined {
  const topSlices = Array.isArray(seg?.topSlices) ? seg?.topSlices : [];
  if (!topSlices.length) return undefined;
  const best = topSlices
    .map((item: any) => ({
      sliceIndex: numericValue(item.sliceIndex ?? item.slice ?? item.z),
      confidence: numericValue(item.maxProb ?? item.confidence ?? item.score) ?? 0,
    }))
    .filter((item: { sliceIndex?: number }) => item.sliceIndex !== undefined)
    .sort((a: { confidence: number }, b: { confidence: number }) => b.confidence - a.confidence)[0];
  return best?.sliceIndex;
}

// Imaging state (CHECK_DOCTOR)
const file = ref<File>();
const imagePreviewUrl = ref('');
const aiTaskId = ref('');
const aiStatus = ref('');
const AI_POLL_INTERVAL_MS = 2500;
const AI_CLIENT_TIMEOUT_MS = 6 * 60 * 1000;
const aiStartedAtMs = ref<number>();
let aiPollTimer: number | undefined;
let aiPolling = false;
let aiCompletionNotified = false;
let aiPollFailureCount = 0;

// Volume state
const volume = ref<VolumeData | null>(null);
const volLoading = ref(false);
const sliceZ = ref(0);
const sliceY = ref(0);
const sliceX = ref(0);
const winC = ref(40);
const winW = ref(80);
const windowPreset = ref<WindowPreset | null>('brain');
const ctViewerMode = ref<'mpr' | 'film'>('mpr');
const selectedFilmSlices = ref<number[]>([]);
const filmScrollRef = ref<HTMLElement>();
const filmScrollTop = ref(0);
const filmViewportHeight = ref(0);
const filmThumbVersion = ref(0);
const filmThumbCache = new Map<number, string>();
const filmThumbQueue = new Set<number>();
let filmThumbRendering = false;
const FILM_COLS = 5;
const FILM_ROW_HEIGHT = 118;
const FILM_GAP = 6;
const FILM_ROW_STRIDE = FILM_ROW_HEIGHT + FILM_GAP;
const FILM_OVERSCAN_ROWS = 3;

// Canvas refs
const canvasAxial    = ref<HTMLCanvasElement>();
const canvasCoronal  = ref<HTMLCanvasElement>();
const canvasSagittal = ref<HTMLCanvasElement>();
const canvas3D       = ref<HTMLCanvasElement>();

// 3D renderer
const renderer3D = ref<VolumeRenderer3D | null>(null);
const azim3D = ref(220);
const elev3D = ref(18);
const render3DMode = ref<VolumeRenderMode>('brain');
const render3DRoi = ref(0.76);
let syncingSlices = false;

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
function clampIndex(value: number, max: number): number {
  return Math.max(0, Math.min(max, Math.round(value)));
}

function setSynchronizedSlices(plane: 'axial' | 'coronal' | 'sagittal', value: number) {
  const vol = volume.value;
  if (!vol) return;
  syncingSlices = true;
  if (plane === 'axial') {
    sliceZ.value = clampIndex(value, vol.nz - 1);
    const ratio = vol.nz > 1 ? sliceZ.value / (vol.nz - 1) : 0;
    sliceY.value = clampIndex(ratio * (vol.ny - 1), vol.ny - 1);
    sliceX.value = clampIndex(ratio * (vol.nx - 1), vol.nx - 1);
  } else if (plane === 'coronal') {
    sliceY.value = clampIndex(value, vol.ny - 1);
    const ratio = vol.ny > 1 ? sliceY.value / (vol.ny - 1) : 0;
    sliceZ.value = clampIndex(ratio * (vol.nz - 1), vol.nz - 1);
    sliceX.value = clampIndex(ratio * (vol.nx - 1), vol.nx - 1);
  } else {
    sliceX.value = clampIndex(value, vol.nx - 1);
    const ratio = vol.nx > 1 ? sliceX.value / (vol.nx - 1) : 0;
    sliceZ.value = clampIndex(ratio * (vol.nz - 1), vol.nz - 1);
    sliceY.value = clampIndex(ratio * (vol.ny - 1), vol.ny - 1);
  }
  syncingSlices = false;
  rerenderMpr();
}

function onSliceSlider(plane: 'axial' | 'coronal' | 'sagittal', event: Event) {
  setSynchronizedSlices(plane, Number((event.target as HTMLInputElement).value));
}

function onWheelPanel(plane: 'axial' | 'coronal' | 'sagittal', e: WheelEvent) {
  if (!volume.value) return
  const delta = e.deltaY > 0 ? 1 : -1
  if (plane === 'axial') setSynchronizedSlices('axial', sliceZ.value + delta)
  else if (plane === 'coronal') setSynchronizedSlices('coronal', sliceY.value + delta)
  else setSynchronizedSlices('sagittal', sliceX.value + delta)
}

function rerenderMpr() {
  const vol = volume.value;
  if (!vol) return;
  if (canvasAxial.value)    renderAxial(canvasAxial.value,       vol, sliceZ.value, winC.value, winW.value);
  if (canvasCoronal.value)  renderCoronal(canvasCoronal.value,   vol, sliceY.value, winC.value, winW.value);
  if (canvasSagittal.value) renderSagittal(canvasSagittal.value, vol, sliceX.value, winC.value, winW.value);
}
function rerender3D() {
  renderer3D.value?.render(azim3D.value, elev3D.value, winC.value, winW.value, render3DMode.value, render3DRoi.value);
}

function set3DMode(mode: VolumeRenderMode) {
  render3DMode.value = mode;
  if (mode === 'brain') render3DRoi.value = 0.76;
  if (mode === 'composite') render3DRoi.value = 0.86;
  if (mode === 'skull') render3DRoi.value = 0.84;
  rerender3D();
}

function set3DRoi(event: Event) {
  render3DRoi.value = Number((event.target as HTMLInputElement).value) / 100;
  rerender3D();
}

function axialDataUrl(vol: VolumeData, z: number, scale = 0.62): string {
  const raw = document.createElement('canvas');
  renderAxial(raw, vol, z, winC.value, winW.value);
  const w = Math.max(1, Math.round(raw.width * scale));
  const h = Math.max(1, Math.round(raw.height * scale));
  const out = document.createElement('canvas');
  out.width = w;
  out.height = h;
  const ctx = out.getContext('2d')!;
  ctx.imageSmoothingEnabled = true;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);
  ctx.drawImage(raw, 0, 0, w, h);
  return out.toDataURL('image/png');
}

const filmTotalSlices = computed(() => volume.value?.nz ?? 0);
const filmTotalRows = computed(() => Math.ceil(filmTotalSlices.value / FILM_COLS));
const filmStartRow = computed(() => Math.max(0, Math.floor(filmScrollTop.value / FILM_ROW_STRIDE) - FILM_OVERSCAN_ROWS));
const filmEndRow = computed(() => {
  const visibleRows = Math.ceil((filmViewportHeight.value || FILM_ROW_STRIDE * 4) / FILM_ROW_STRIDE);
  return Math.min(filmTotalRows.value, filmStartRow.value + visibleRows + FILM_OVERSCAN_ROWS * 2);
});
const visibleFilmSliceIndexes = computed(() => {
  const vol = volume.value;
  if (!vol) return [];
  const start = filmStartRow.value * FILM_COLS;
  const end = Math.min(vol.nz, filmEndRow.value * FILM_COLS);
  return Array.from({ length: Math.max(0, end - start) }, (_, index) => start + index);
});
const visibleFilmThumbs = computed(() => {
  filmThumbVersion.value;
  return visibleFilmSliceIndexes.value.map(z => ({ z, url: filmThumbCache.get(z) || '' }));
});

function queueFilmThumbs(sliceIndexes: number[]) {
  if (!volume.value) return;
  for (const z of sliceIndexes) {
    if (!filmThumbCache.has(z)) filmThumbQueue.add(z);
  }
  processFilmThumbQueue();
}

function processFilmThumbQueue() {
  if (filmThumbRendering) return;
  const vol = volume.value;
  if (!vol || filmThumbQueue.size === 0) return;
  filmThumbRendering = true;
  requestAnimationFrame(() => {
    const z = filmThumbQueue.values().next().value as number | undefined;
    if (typeof z === 'number') {
      filmThumbQueue.delete(z);
      if (!filmThumbCache.has(z)) {
        filmThumbCache.set(z, axialDataUrl(vol, z));
        filmThumbVersion.value++;
      }
    }
    filmThumbRendering = false;
    if (filmThumbQueue.size > 0) processFilmThumbQueue();
  });
}

function clearFilmThumbCache() {
  filmThumbCache.clear();
  filmThumbQueue.clear();
  filmThumbVersion.value++;
}

function updateFilmViewport() {
  const el = filmScrollRef.value;
  if (!el) return;
  filmScrollTop.value = el.scrollTop;
  filmViewportHeight.value = el.clientHeight;
}

function onFilmScroll() {
  updateFilmViewport();
}

function recommendedAxialSlices(vol: VolumeData, count = 20): number[] {
  if (vol.nz <= count) return Array.from({ length: vol.nz }, (_, z) => z);
  const start = Math.max(0, Math.floor(vol.nz * 0.08));
  const end = Math.min(vol.nz - 1, Math.ceil(vol.nz * 0.92));
  const span = Math.max(1, end - start);
  return Array.from({ length: count }, (_, i) => Math.round(start + (span * i) / (count - 1)));
}

function refreshFilmThumbs() {
  const vol = volume.value;
  if (!vol) {
    clearFilmThumbCache();
    return;
  }
  clearFilmThumbCache();
  nextTick(updateFilmViewport);
}

async function setCtViewerMode(mode: 'mpr' | 'film') {
  ctViewerMode.value = mode;
  await nextTick();
  if (mode === 'mpr') {
    rerenderMpr();
    rerender3D();
  } else {
    filmScrollRef.value?.scrollTo({ top: 0 });
    filmScrollTop.value = 0;
    refreshFilmThumbs();
    await nextTick();
    updateFilmViewport();
  }
}

function isFilmSliceSelected(z: number): boolean {
  return selectedFilmSlices.value.includes(z);
}

function toggleFilmSlice(z: number) {
  selectedFilmSlices.value = isFilmSliceSelected(z)
    ? selectedFilmSlices.value.filter(item => item !== z)
    : [...selectedFilmSlices.value, z].sort((a, b) => a - b);
}

function jumpToSlice(z: number) {
  sliceZ.value = z;
  setCtViewerMode('mpr');
}

function selectCurrentFilmSlice() {
  if (!volume.value) return;
  if (!isFilmSliceSelected(sliceZ.value)) toggleFilmSlice(sliceZ.value);
}

function selectRecommendedFilmSlices() {
  const vol = volume.value;
  if (!vol) return;
  selectedFilmSlices.value = recommendedAxialSlices(vol, Math.min(12, vol.nz));
  ctViewerMode.value = 'film';
  nextTick(updateFilmViewport);
}

function clearSelectedFilmSlices() {
  selectedFilmSlices.value = [];
}

function drawFilmCell(
  ctx: CanvasRenderingContext2D,
  vol: VolumeData,
  z: number,
  x: number,
  y: number,
  w: number,
  h: number
) {
  const raw = document.createElement('canvas');
  renderAxial(raw, vol, z, winC.value, winW.value);
  const margin = 18;
  const imgW = w - margin * 2;
  const imgH = h - margin * 2;
  const scale = Math.min(imgW / raw.width, imgH / raw.height);
  const dw = raw.width * scale;
  const dh = raw.height * scale;
  const dx = x + (w - dw) / 2;
  const dy = y + (h - dh) / 2;

  ctx.fillStyle = '#000';
  ctx.fillRect(x, y, w, h);
  ctx.imageSmoothingEnabled = true;
  ctx.drawImage(raw, dx, dy, dw, dh);
  ctx.strokeStyle = 'rgba(255,255,255,0.18)';
  ctx.lineWidth = 1;
  ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
  ctx.font = 'bold 15px Arial';
  ctx.fillStyle = '#18c447';
  ctx.fillText('R', x + 9, y + h / 2);
  ctx.fillText('P', x + w / 2 - 5, y + h - 10);
  ctx.font = '12px Arial';
  ctx.fillStyle = 'rgba(255,255,255,0.8)';
  ctx.fillText(String(z + 1), x + w - 34, y + 18);
}

function exportSelectedFilm() {
  const vol = volume.value;
  const slices = [...selectedFilmSlices.value].sort((a, b) => a - b);
  if (!vol || slices.length === 0) return;

  const cols = 4;
  const rows = Math.ceil(slices.length / cols);
  const cellW = 260;
  const cellH = 196;
  const canvas = document.createElement('canvas');
  canvas.width = cols * cellW;
  canvas.height = rows * cellH;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  slices.forEach((z, idx) => {
    drawFilmCell(ctx, vol, z, (idx % cols) * cellW, Math.floor(idx / cols) * cellH, cellW, cellH);
  });

  const a = document.createElement('a');
  const patient = current.value?.patientName || 'patient';
  a.href = canvas.toDataURL('image/png');
  a.download = `${patient}-CT-selected-slices.png`;
  a.click();
  ElMessage.success('已导出所选切片胶片 PNG');
}

watch(sliceZ, () => { if (syncingSlices) return; const v = volume.value; if (v && canvasAxial.value)    renderAxial(canvasAxial.value,       v, sliceZ.value, winC.value, winW.value); });
watch(sliceY, () => { if (syncingSlices) return; const v = volume.value; if (v && canvasCoronal.value)  renderCoronal(canvasCoronal.value,   v, sliceY.value, winC.value, winW.value); });
watch(sliceX, () => { if (syncingSlices) return; const v = volume.value; if (v && canvasSagittal.value) renderSagittal(canvasSagittal.value, v, sliceX.value, winC.value, winW.value); });
watch(visibleFilmSliceIndexes, indexes => queueFilmThumbs(indexes), { immediate: true });
watch([azim3D, elev3D], rerender3D);
watch([render3DMode, render3DRoi], rerender3D);
watch([winC, winW], () => { rerenderMpr(); rerender3D(); refreshFilmThumbs(); });

watch(volume, async (vol) => {
  // destroy old renderer
  renderer3D.value?.destroy();
  renderer3D.value = null;
  selectedFilmSlices.value = [];
  clearFilmThumbCache();
  filmScrollTop.value = 0;
  ctViewerMode.value = 'mpr';
  render3DMode.value = 'brain';
  render3DRoi.value = 0.76;
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
  refreshFilmThumbs();
});

function setWindow(preset: WindowPreset) {
  const map = {
    brain: [40, 80],
    standard: [400, 1400],
    subdural: [80, 200],
    bone: [500, 2500],
    soft: [60, 400],
  } as const;
  windowPreset.value = preset;
  [winC.value, winW.value] = map[preset];
}

function onManualWindowChange() {
  windowPreset.value = null;
  rerenderMpr();
}

// Lab state (LAB_DOCTOR)
type LabResultRow = Pick<LaboratoryResultItem, 'itemCode' | 'itemName' | 'resultValue' | 'unit' | 'referenceRange' | 'abnormalFlag'>;

const lab = reactive({ specimenType: '', barcode: '' });
const specimenId = ref('');
const labBarcodeSvg = ref<SVGSVGElement>();
const specimensByOrder = ref<Record<string, Specimen[]>>({});
const labResultRows = ref<LabResultRow[]>([]);
const labResultsSaved = ref(false);
const labSaving = ref(false);
const samplingSubmitting = ref(false);
const specimenTypeOptions = [
  '全血',
  '血清',
  '血浆',
  '尿液',
  '粪便',
  '脑脊液',
  '咽拭子',
  '分泌物',
  '穿刺液',
  '组织',
  '其他',
];
const pathology = reactive({ material: '', gross: '', diagnosis: '' });
const pathologySlides = ref<Array<{ id: string; label: string; previewUrl: string; attachment?: MedicalAttachment }>>([]);
const pathologyUploading = ref(false);
const pathologySaving = ref(false);
const pathologySaved = ref(false);
let syncingPathologyForm = false;
const isPathologyOrder = computed(() => current.value ? isPathologyItem(current.value) : false);
const pathologyNo = computed(() => current.value ? `P${current.value.id.replace(/-/g, '').slice(0, 8).toUpperCase()}` : '');
const currentSpecimens = computed(() => current.value ? (specimensByOrder.value[current.value.id] ?? []) : []);
const isCurrentSamplingDone = computed(() => currentSpecimens.value.some(item => ['COLLECTED', 'RECEIVED', 'ANALYZING', 'COMPLETED'].includes(item.status)));

function buildSpecimenBarcode(order: MedicalOrder) {
  if (order.paymentStatus !== 'PAID' || !order.roomId || order.queueNumber == null) return '';
  const roomCode = (order.roomId || order.roomName || 'LAB').replace(/[^a-zA-Z0-9]/g, '').toUpperCase() || 'LAB';
  const dateCode = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const queueCode = String(Math.abs(order.queueNumber)).padStart(3, '0');
  const orderCode = order.id.replace(/-/g, '').slice(0, 6).toUpperCase();
  return `${roomCode}-${dateCode}-${queueCode}-${orderCode}`;
}

async function renderLabBarcode() {
  await nextTick();
  if (!labBarcodeSvg.value) return;
  if (!lab.barcode.trim()) {
    labBarcodeSvg.value.innerHTML = '';
    return;
  }
  JsBarcode(labBarcodeSvg.value, lab.barcode, {
    format: 'CODE128',
    width: 1.6,
    height: 46,
    margin: 0,
    displayValue: false,
  });
}

function labFlagLabel(flag?: string) {
  const value = (flag ?? '').toUpperCase();
  if (['HIGH', 'H', '↑', '偏高'].includes(value)) return '↑';
  if (['LOW', 'L', '↓', '偏低'].includes(value)) return '↓';
  if (['ABNORMAL', '异常'].includes(value)) return '异常';
  return '正常';
}

function labFlagType(flag?: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const value = (flag ?? '').toUpperCase();
  if (!value || value === 'NORMAL' || value === '正常') return 'success';
  if (['HIGH', 'H', 'LOW', 'L', '↑', '↓', '偏高', '偏低'].includes(value)) return 'warning';
  if (['CRITICAL', 'C', '危急', '危急值'].includes(value)) return 'danger';
  return 'danger';
}

function normalizeLabFlag(flag?: string) {
  const value = (flag ?? '').trim().toUpperCase();
  if (['HIGH', 'H', '↑', '偏高'].includes(value)) return 'HIGH';
  if (['LOW', 'L', '↓', '偏低'].includes(value)) return 'LOW';
  if (['CRITICAL', 'C', '危急', '危急值'].includes(value)) return 'CRITICAL';
  if (['ABNORMAL', '异常'].includes(value)) return 'ABNORMAL';
  return 'NORMAL';
}

function valueOf(row: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && String(value).trim() !== '') return String(value).trim();
  }
  return '';
}

function normalizeLabRow(row: Record<string, unknown>, index: number): LabResultRow | null {
  const itemName = valueOf(row, ['指标名称', '项目名称', '检验项目', '项目', 'itemName', 'name']);
  const resultValue = valueOf(row, ['结果值', '结果', '检测结果', 'resultValue', 'value']);
  if (!itemName || !resultValue) return null;
  const code = valueOf(row, ['指标编码', '项目编码', '编码', 'itemCode', 'code']) || `LAB-${index + 1}`;
  const flag = normalizeLabFlag(valueOf(row, ['异常标志', '提示', 'abnormalFlag', 'flag']) || 'NORMAL');
  return {
    itemCode: code,
    itemName,
    resultValue,
    unit: valueOf(row, ['单位', 'unit']),
    referenceRange: valueOf(row, ['参考范围', '参考值', 'referenceRange', 'range']),
    abnormalFlag: flag,
  };
}

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
  return { WAITING: '待执行', CALLED: '已叫号', IN_PROGRESS: '执行中', REPORT_PENDING: '待报告', COMPLETED: '已完成', MISSED: '过号' }[s] ?? s;
}

function orderStatusLabel(order: MedicalOrder) {
  if (order.status === 'COMPLETED') return `${formatOrderType(order.orderType)}已完成`;
  if (order.status === 'REPORT_PENDING') return `${formatOrderType(order.orderType)}待报告`;
  if (isPathologyItem(order)) {
    if (order.status === 'WAITING') return '待接收送检';
    if (order.status === 'IN_PROGRESS') return '已接收送检';
    if (order.status === 'REPORT_PENDING') return '病理待报告';
  }
  return statusLabel(order.status);
}

function statusTagType(s: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (s === 'WAITING') return 'warning';
  if (s === 'CALLED') return 'primary';
  if (s === 'IN_PROGRESS') return 'primary';
  if (s === 'REPORT_PENDING') return 'info';
  if (s === 'COMPLETED') return 'success';
  return 'info';
}

function formatOrderType(t: string) {
  return { CHECK: '检查', LAB: '检验', DISPOSAL: '处置' }[t] ?? t;
}

function isPathologyItem(order: MedicalOrder) {
  if (order.orderType !== 'LAB') return false;
  const text = `${order.itemCode ?? ''}${order.itemName ?? ''}${order.bodyPart ?? ''}`.toUpperCase();
  return ['PATH', 'PATHOLOGY', '病理', '切片', '活检'].some(keyword => text.includes(keyword));
}

function aiStatusTagType(s: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (s === 'COMPLETED') return 'success';
  if (s === 'PROCESSING') return 'warning';
  if (s === 'FAILED') return 'danger';
  return 'info';
}

function aiStatusLabel(s: string) {
  return { COMPLETED: '分析完成', PROCESSING: '分析中', FAILED: '分析失败' }[s] ?? (s || '未提交');
}

async function loadOrders() {
  const workspace = await getWorkspace();
  orders.value = workspace.orders;
  // 保持 current 与 orders 中同一对象引用，消除字段不一致导致的跳变
  if (current.value) {
    const match = orders.value.find(o => o.id === current.value!.id);
    if (match) current.value = match;
  }
}

async function refreshOrders() {
  refreshing.value = true;
  await loadOrders();
  // 刷新后保持当前选中状态和 tab，不重置
  if (current.value) {
    await select(current.value, true);
  }
  refreshing.value = false;
}

function formatReportDate(value?: string) {
  if (!value) return today;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return today;
  return date.toLocaleDateString('zh-CN');
}

async function loadExistingReport(orderId: string): Promise<MedicalReport | undefined> {
  const workspace = await getWorkspace(orderId);
  const reportDto = workspace.detail?.report ?? null;
  if (!reportDto) return undefined;
  report.findings = reportDto.findings || '';
  report.conclusion = cleanReportConclusion(reportDto.conclusion);
  report.advice = reportDto.advice || '';
  published.value = reportDto.status === 'CONFIRMED';
  confirmedAt.value = reportDto.confirmedAt ? formatReportDate(reportDto.confirmedAt) : '';
  return reportDto;
}

function cleanPathologyReportValue(value: string) {
  const text = value.trim();
  return text === '—' ? '' : text;
}

function cleanReportConclusion(value?: string) {
  if (!value) return '';
  return value
    .split(/\r?\n/)
    .map(line => line
      .replace(/^\s*(?:AI|人工智能|模型|辅助判断|辅助诊断|智能辅助)\s*(?:初步)?(?:结论|判断|提示|诊断)?[：:]\s*/i, '')
      .replace(/\s*[（(](?:AI|人工智能|模型|智能辅助)[^）)]*[）)]\s*/gi, '')
      .trim()
    )
    .filter(line => line && !/(?:AI|人工智能|模型|智能辅助).{0,12}(?:仅供参考|提示|建议)/i.test(line))
    .join(' ')
    .trim();
}

function fieldFromReportFindings(findings: string, label: string) {
  const line = findings.split('\n').find(item => item.trim().startsWith(`${label}：`));
  if (!line) return '';
  return cleanPathologyReportValue(line.slice(label.length + 1));
}

function syncPathologyFormFromReport() {
  syncingPathologyForm = true;
  pathology.material = fieldFromReportFindings(report.findings, '送检材料');
  pathology.gross = fieldFromReportFindings(report.findings, '肉眼所见');
  pathology.diagnosis = cleanPathologyReportValue(report.conclusion || pathology.diagnosis);
  nextTick(() => {
    syncingPathologyForm = false;
  });
}

function isLegacyPathologySpecimen(order: MedicalOrder, specimen?: Specimen) {
  return isPathologyItem(order)
    && specimen?.specimenType === '全血'
    && /^LAB-[0-9A-F]{8}$/i.test(specimen.barcode);
}

async function select(row: MedicalOrder, isReselect = false) {
  stopAiPolling();

  // ── Phase 1: Synchronous reset of ALL fields (Vue batches these into one render) ──
  current.value = row;
  if (!isReselect) {
    Object.assign(report, { findings: '', conclusion: '', advice: '' });
    confirmedAt.value = '';
    published.value = false;
    file.value = undefined;
    imagePreviewUrl.value = '';
    volume.value = null;
    aiTaskId.value = '';
    aiStatus.value = '';
    aiStartedAtMs.value = undefined;
    aiCompletionNotified = false;
    aiMessages.value = [];
    aiModel.value = '';
    aiStructured.value = {};
    specimenId.value = '';
    labResultRows.value = [];
    labResultsSaved.value = false;
    pathology.material = '';
    pathology.gross = '';
    pathology.diagnosis = '';
    pathologySlides.value.forEach(slide => {
      if (slide.previewUrl) URL.revokeObjectURL(slide.previewUrl);
    });
    pathologySlides.value = [];
    pathologySaved.value = false;
    lab.specimenType = '';
    lab.barcode = '';
  }

  // ── Phase 2: Use workspace API instead of 3+ separate requests ──
  if (row.orderType === 'LAB') {
    const workspace = await getWorkspace(row.id);
    const detail = workspace.detail;
    const specimens = detail?.specimens ?? [];
    const labResults = detail?.labResults ?? [];
    const attachments = detail?.attachments ?? [];

    // ── Phase 3: Synchronous batch of ALL async results (Vue batches into one render) ──
    specimensByOrder.value = { ...specimensByOrder.value, [row.id]: specimens };
    const existingSpecimen = specimens[0];
    const displaySpecimen = isLegacyPathologySpecimen(row, existingSpecimen) ? undefined : existingSpecimen;
    specimenId.value = displaySpecimen?.id ?? '';
    lab.specimenType = displaySpecimen?.specimenType ?? '';
    lab.barcode = displaySpecimen?.barcode ?? buildSpecimenBarcode(row);
    labResultRows.value = labResults.map(item => ({
      itemCode: item.itemCode,
      itemName: item.itemName,
      resultValue: item.resultValue,
      unit: item.unit,
      referenceRange: item.referenceRange,
      abnormalFlag: item.abnormalFlag,
    }));
    labResultsSaved.value = labResultRows.value.length > 0;
    if (isPathologyItem(row)) {
      const imageAttachments = attachments.filter(item => item.contentType?.startsWith('image/'));
      const downloadResults = await Promise.allSettled(
        imageAttachments.map(item => downloadAttachment(row.id, item.id))
      );
      pathologySlides.value = imageAttachments.map((item, i) => {
        const result = downloadResults[i];
        const previewUrl = result.status === 'fulfilled'
          ? URL.createObjectURL(result.value)
          : '';
        return {
          id: item.id,
          label: '',
          previewUrl,
          attachment: item,
        };
      });
    }
  }

  // ── Phase 4: Render barcode and load report in parallel ──
  const [, existingReport] = await Promise.all([
    renderLabBarcode(),
    loadExistingReport(row.id),
  ]);
  if (isPathologyItem(row) && existingReport) {
    syncPathologyFormFromReport();
    pathologySaved.value = true;
  }
  // 不再在数据加载完成后强制切 tab，避免打断用户在其他 tab 的操作
}

async function call(row: MedicalOrder) {
  await callMedicalOrder(row.id);
  ElMessage.success('已叫号');
  await loadOrders();
}

async function start(row: MedicalOrder) {
  await startMedicalOrder(row.id);
  ElMessage.success(isPathologyItem(row) ? '已接收送检' : '已开始执行');
  await loadOrders(); // loadOrders 自动同步 current.value
}

async function miss(row: MedicalOrder) {
  await missMedicalOrder(row.id);
  await loadOrders();
}

async function markPatientDone() {
  if (!current.value || current.value.status !== 'IN_PROGRESS') return;
  const summary = role.value === 'CHECK_DOCTOR'
    ? '患者检查已完成，待发布正式报告'
    : role.value === 'LAB_DOCTOR'
      ? '患者采样/检验执行已完成，待发布正式报告'
      : '处置执行已完成，待发布记录';
  await markMedicalOrderReportPending(current.value.id, { summary });
  ElMessage.success('患者执行已完成，当前状态为待报告');
  await loadOrders(); // loadOrders 自动同步 current.value
}

const VOLUME_EXTS = ['.nii', '.nii.gz', '.nrrd', '.nhdr', '.mha'];
type WindowPreset = 'brain' | 'standard' | 'subdural' | 'bone' | 'soft';

function isVolumeFile(f: File): boolean {
  const name = f.name.toLowerCase();
  return VOLUME_EXTS.some(ext => name.endsWith(ext));
}

function isDicomFile(f: File): boolean {
  return f.name.toLowerCase().endsWith('.dcm');
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
      setWindow('brain');
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
  const input = event.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  if (files.length) loadFiles(files);
  input.value = '';
}

/** DICOM conversion endpoint hosted by ai-service. */
const DICOM_SERVICE = '/api/ai';

async function loadDicomFiles(files: File[]) {
  if (files.length === 0) return;
  file.value = files[0];
  imagePreviewUrl.value = '';
  volume.value = null;
  volLoading.value = true;

  try {
    // ── 优先尝试 Python 微服务（处理所有压缩格式）────────────────
    let vol: import('../../utils/volumeReader').VolumeData | null = null;
    let serviceOk = false;
    try {
      const hc = await fetch(`${DICOM_SERVICE}/dicom/health`, { signal: AbortSignal.timeout(1500) });
      serviceOk = hc.ok;
    } catch { /* 服务未启动 */ }

    if (serviceOk) {
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
    setWindow('brain');
    ElMessage.success(`影像加载成功：${vol.nx}×${vol.ny}×${vol.nz} 体素`);
  } catch (e: unknown) {
    const msg = String((e as Error).message ?? e);
    ElMessage.error(msg);
    file.value = undefined;
  } finally {
    volLoading.value = false;
  }
}

function loadFiles(files: File[]) {
  if (files.length > 1 || isDicomFile(files[0])) {
    loadDicomFiles(files);
    return;
  }
  loadFile(files[0]);
}

async function loadDicomFolder(event: Event) {
  const input = event.target as HTMLInputElement;
  const fileList = input.files;
  if (!fileList || fileList.length === 0) return;
  const files = Array.from(fileList);
  input.value = '';
  await loadDicomFiles(files);
}

function handleDrop(event: DragEvent) {
  const files = event.dataTransfer?.files ? Array.from(event.dataTransfer.files) : [];
  if (files.length) loadFiles(files);
}

function clearFile() {
  file.value = undefined;
  imagePreviewUrl.value = '';
  volume.value = null;
}

function parseAiOutput(raw: AiMedicalTask['rawOutput']): Record<string, any> {
  if (!raw) return {};
  let parsed: unknown = raw;
  if (typeof raw === 'string') {
    try {
      parsed = JSON.parse(raw);
    } catch {
      return {};
    }
  }
  if (!parsed || typeof parsed !== 'object') return {};
  const record = parsed as Record<string, any>;
  if (record.result && typeof record.result === 'object') return record.result as Record<string, any>;
  return record;
}

function confidenceText(value?: number) {
  return typeof value === 'number' ? `${Math.round(value * 100)}%` : '未返回';
}

function aiTaskStartedAt(task: AiMedicalTask): number {
  const createdAt = task.createdAt ? Date.parse(task.createdAt) : NaN;
  if (Number.isFinite(createdAt)) return createdAt;
  if (aiStartedAtMs.value !== undefined) return aiStartedAtMs.value;
  return Date.now() - AI_CLIENT_TIMEOUT_MS;
}

function markAiTimedOut() {
  aiStatus.value = 'FAILED';
  aiMessages.value = [{
    id: 'ct-timeout',
    label: 'AI 分析超时',
    content: 'AI 分析超过 6 分钟仍未返回结果，请检查 AI 服务日志或重新提交诊断。',
    kind: 'advice',
  }];
  stopAiPolling();
}

function syncCtAiResult(task: AiMedicalTask) {
  aiStatus.value = task.status === 'RUNNING' || task.status === 'PENDING' || task.status === 'QUEUED' ? 'PROCESSING' : task.status;
  aiModel.value = task.modelVersion || aiModel.value;
  aiFallback.value = task.modelVersion?.includes('demo') || task.modelVersion?.includes('mock') || false;

  if (aiStatus.value === 'PROCESSING') {
    aiStartedAtMs.value = aiTaskStartedAt(task);
    if (Date.now() - aiStartedAtMs.value > AI_CLIENT_TIMEOUT_MS) {
      markAiTimedOut();
      return;
    }
  }

  if (task.status === 'FAILED') {
    aiMessages.value = [{ id: 'ct-error', label: 'AI 分析失败', content: task.errorMessage || '推理失败，请检查模型文件和 AI 服务日志', kind: 'advice' }];
    return;
  }

  const result = parseAiOutput(task.rawOutput);
  if (!Object.keys(result).length) return;
  aiStructured.value = {
    label: result.label,
    confidence: result.confidence,
    abnormalRegions: Array.isArray(result.abnormalRegions) ? result.abnormalRegions : [],
    metalArtifact: result.metalArtifact,
    metalArtifactSegmentation: result.metalArtifactSegmentation,
    lesionSegmentation: result.lesionSegmentation,
  };
  report.findings = result.findings || report.findings;
  report.conclusion = cleanReportConclusion(result.conclusion) || report.conclusion;
  report.advice = result.riskAdvice || report.advice;
  const timingSummary = formatInferenceTimings(result.inferenceTimingsMs);
  aiMessages.value = [
    { id: 'ct-f', label: 'AI 影像所见', content: result.findings, kind: 'findings' },
    { id: 'ct-c', label: 'AI 初步结论', content: result.conclusion, kind: 'conclusion' },
    { id: 'ct-a', label: '风险提示', content: result.riskAdvice, kind: 'advice' },
    timingSummary ? { id: 'ct-timing', label: '推理耗时诊断', content: timingSummary, kind: 'advice' } : null,
  ].filter((item): item is { id: string; label: string; content: string; kind: string } => Boolean(item?.content));
}

function formatInferenceTimings(value: unknown) {
  if (!value || typeof value !== 'object') return '';
  const timings = value as Record<string, unknown>;
  const labelMap: Record<string, string> = {
    total: '总耗时',
    download_load: '下载/读取',
    preprocess_slices: '预处理',
    classifier: '出血分类',
    detector: '异常定位',
    metal_classifier: '金属伪影分类',
    metal_segmentation: '金属伪影分割',
    lesion_segmentation: '病灶分割',
    parallel_total: '小模型并行段',
    originalSlices: '原始有效切片',
    modelSlices: '模型实际切片',
  };
  return Object.entries(labelMap)
    .filter(([key]) => typeof timings[key] === 'number')
    .map(([key, label]) => {
      const numberValue = Number(timings[key]);
      if (key.endsWith('Slices')) return `${label}: ${numberValue}`;
      return `${label}: ${(numberValue / 1000).toFixed(1)}s`;
    })
    .join('；');
}

function isAiTaskTerminal(status?: string) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';
}

function stopAiPolling() {
  if (aiPollTimer !== undefined) {
    window.clearInterval(aiPollTimer);
    aiPollTimer = undefined;
  }
}

function startAiPolling() {
  if (!aiTaskId.value || isAiTaskTerminal(aiStatus.value)) return;
  stopAiPolling();
  aiPollTimer = window.setInterval(() => {
    if (!aiTaskId.value || isAiTaskTerminal(aiStatus.value)) {
      stopAiPolling();
      return;
    }
    if (aiStartedAtMs.value !== undefined && Date.now() - aiStartedAtMs.value > AI_CLIENT_TIMEOUT_MS) {
      markAiTimedOut();
      return;
    }
    pollAi({ silent: true }).catch((error) => {
      aiPollFailureCount++;
      if (aiPollFailureCount >= 3) {
        aiStatus.value = 'FAILED';
        aiMessages.value = [{
          id: 'ct-refresh-error',
          label: 'AI 状态刷新失败',
          content: errorMessage(error, '连续刷新 AI 状态失败，请检查后端服务和任务表结构。'),
          kind: 'advice',
        }];
        stopAiPolling();
      }
    });
  }, AI_POLL_INTERVAL_MS);
}

function notifyAiCompleted() {
  if (aiCompletionNotified) return;
  aiCompletionNotified = true;
  ElMessage.success('AI 分析完成，已同步至报告草稿');
}

async function uploadCt() {
  if (!current.value || !file.value) return;
  aiSubmitting.value = true;
  let submitStage = '上传影像附件';
  try {
    stopAiPolling();
    aiCompletionNotified = false;
    aiPollFailureCount = 0;
    aiStartedAtMs.value = Date.now();
    aiStatus.value = 'PROCESSING';
    aiMessages.value = [];
    aiStructured.value = {};
    submitStage = '上传影像附件';
    const attachment = await uploadAttachment(current.value.id, file.value);
    submitStage = '创建 AI 分析任务';
    const task = await submitCt(current.value.id, attachment.id);
    aiTaskId.value = task.externalTaskId;
    syncCtAiResult(task);
    if (task.status === 'COMPLETED' && current.value) {
      await loadExistingReport(current.value.id);
      notifyAiCompleted();
    } else if (!isAiTaskTerminal(task.status)) {
      startAiPolling();
    }
    ElMessage.success('AI 辅助诊断已启动');
  } catch (error) {
    stopAiPolling();
    const rawMessage = errorMessage(error, '请检查影像附件、医嘱信息和 AI 服务配置。');
    const message = `${submitStage}失败：${rawMessage}`;
    aiStatus.value = 'FAILED';
    aiTaskId.value = '';
    aiMessages.value = [{
      id: 'ct-submit-error',
      label: 'AI 分析提交失败',
      content: message,
      kind: 'advice',
    }];
    ElMessage.error({ message, duration: 6000 });
  } finally {
    aiSubmitting.value = false;
  }
}

async function pollAi(options: { silent?: boolean } = {}) {
  if (!aiTaskId.value || aiPolling) return;
  aiPolling = true;
  try {
    const task = await refreshAiTask(aiTaskId.value);
    aiPollFailureCount = 0;
  syncCtAiResult(task);
  if (isAiTaskTerminal(task.status)) stopAiPolling();
  if (task.status === 'COMPLETED' && current.value) await loadExistingReport(current.value.id);
  if (task.status === 'COMPLETED' && options.silent) return;
  if (task.status === 'COMPLETED') ElMessage.success('AI 分析完成，已同步至报告草稿');
  } finally {
    aiPolling = false;
  }
}

async function startAiDiagnosis() {
  if (!current.value) {
    ElMessage.warning('请先选择医嘱');
    return;
  }
  if (!file.value) {
    ElMessage.warning('请先上传影像文件');
    return;
  }
  await uploadCt();
}

async function prepareSpecimen() {
  if (!current.value) return;
  if (isCurrentSamplingDone.value) return;
  if (!lab.specimenType.trim()) {
    ElMessage.warning('请填写样本类型');
    return;
  }
  if (!lab.barcode.trim()) lab.barcode = buildSpecimenBarcode(current.value);
  if (!lab.barcode.trim()) {
    ElMessage.warning('医嘱缴费并分配诊室后才会生成条码号');
    return;
  }
  samplingSubmitting.value = true;
  try {
    const specimen = await createSpecimen(current.value.id, lab.specimenType.trim(), lab.barcode.trim());
    specimenId.value = specimen.id;
    await transitionSpecimen(specimen.id, 'COLLECTED');
    specimensByOrder.value = { ...specimensByOrder.value, [current.value.id]: [{ ...specimen, status: 'COLLECTED' }] };
    if (current.value.status === 'IN_PROGRESS') {
      await markMedicalOrderReportPending(current.value.id, { summary: '采样已完成，样本已送检，待发布正式报告' });
    }
    ElMessage.success('采样已完成，样本已送检，当前状态为待报告');
    await loadOrders(); // loadOrders 自动同步 current.value
  } finally {
    samplingSubmitting.value = false;
  }
}

async function importLabExcel(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, { type: 'array' });
  const sheet = workbook.Sheets[workbook.SheetNames[0]];
  const rows = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' });
  const normalized = rows
    .map((row, index) => normalizeLabRow(row, index))
    .filter((row): row is LabResultRow => row !== null);
  if (!normalized.length) {
    ElMessage.warning('未识别到有效检验结果，请确认 Excel 表头包含“指标名称”和“结果值”');
    return;
  }
  labResultRows.value = normalized;
  labResultsSaved.value = false;
  updateLabReportDraft();
  ElMessage.success(`已导入 ${normalized.length} 项检验结果`);
}

function updateLabReportDraft() {
  if (!labResultRows.value.length) return;
  const specimenText = lab.specimenType.trim() || '未填写类型';
  const barcodeText = lab.barcode.trim() || '未填写';
  report.findings = `${specimenText}样本，条码号：${barcodeText}，共导入 ${labResultRows.value.length} 项检验指标。`;
  const abnormal = labResultRows.value.filter(row => labFlagLabel(row.abnormalFlag) !== '正常');
  report.conclusion = abnormal.length
    ? `异常指标：${abnormal.map(row => `${row.itemName}${labFlagLabel(row.abnormalFlag)}`).join('、')}。`
    : '本次导入检验指标未见明显异常。';
}

function labResultContext() {
  if (!labResultRows.value.length) return '尚未导入检验结果。';
  const rows = labResultRows.value.map(row => {
    const flag = labFlagLabel(row.abnormalFlag);
    return `${row.itemName}=${row.resultValue}${row.unit || ''}，参考范围=${row.referenceRange || '未提供'}，提示=${flag}`;
  });
  return rows.join('\n');
}

function mapSavedLabResult(item: LaboratoryResultItem): LabResultRow {
  return {
    itemCode: item.itemCode,
    itemName: item.itemName,
    resultValue: item.resultValue,
    unit: item.unit,
    referenceRange: item.referenceRange,
    abnormalFlag: item.abnormalFlag,
  };
}

function reportAiContext() {
  const base = [
    `患者姓名：${current.value?.patientName || ''}`,
    `医嘱项目：${current.value?.itemName || ''}`,
    `临床目的：${current.value?.purpose || '未提供'}`,
    `当前报告所见：${report.findings || '未填写'}`,
    `当前报告结论：${report.conclusion || '未填写'}`,
  ];
  if (role.value === 'LAB_DOCTOR' && isPathologyOrder.value) {
    base.push(
      `送检材料：${pathology.material || '未填写'}`,
      `肉眼所见：${pathology.gross || '未填写'}`,
      `病理诊断：${pathology.diagnosis || '未填写'}`,
      `光镜照片：${pathologySlides.value.length ? `已上传 ${pathologySlides.value.length} 张` : '未上传'}`
    );
  } else if (role.value === 'LAB_DOCTOR') {
    base.push(`检验明细：\n${labResultContext()}`);
  } else if (role.value === 'CHECK_DOCTOR') {
    base.push(`影像AI结构化结果：\n${ctAiContext()}`);
  }
  base.push('生成要求：只能依据上述真实报告数据和检验明细生成后续建议；不得添加未提供的症状、疾病、检查结果或诊断。信息不足时请明确提示需医生结合临床判断。');
  return base.join('\n');
}

function ctAiContext() {
  if (!Object.keys(aiStructured.value).length && !report.findings && !report.conclusion) {
    return '尚未完成影像AI分析。';
  }
  return JSON.stringify({
    status: aiStatus.value,
    label: aiStructured.value.label,
    confidence: aiStructured.value.confidence,
    findings: report.findings,
    conclusion: report.conclusion,
    riskAdvice: report.advice,
    abnormalRegions: aiStructured.value.abnormalRegions || [],
    metalArtifact: aiStructured.value.metalArtifact,
    metalArtifactSegmentation: aiStructured.value.metalArtifactSegmentation,
    lesionSegmentation: aiStructured.value.lesionSegmentation,
  }, null, 2);
}

async function saveLab() {
  if (!current.value) return;
  if (!specimenId.value) {
    ElMessage.warning('请先完成样本登记并流转至分析');
    return;
  }
  if (!labResultRows.value.length) {
    ElMessage.warning('请先导入检验结果 Excel');
    return;
  }
  labSaving.value = true;
  try {
    const saved = await saveLabResults(current.value.id, specimenId.value, labResultRows.value.map(row => ({
      ...row,
      abnormalFlag: normalizeLabFlag(row.abnormalFlag),
      createdByType: 'HUMAN',
    })));
    labResultRows.value = saved.map(item => ({
      itemCode: item.itemCode,
      itemName: item.itemName,
      resultValue: item.resultValue,
      unit: item.unit,
      referenceRange: item.referenceRange,
      abnormalFlag: item.abnormalFlag,
    }));
    updateLabReportDraft();
    await saveReportDraft(current.value.id, report);
    labResultsSaved.value = true;
    ElMessage.success('检验结果已保存并同步至报告');
  } finally {
    labSaving.value = false;
  }
}

async function uploadPathologySlides(event: Event) {
  if (!current.value) {
    ElMessage.warning('请先选择病理送检医嘱');
    return;
  }
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  if (!files.length) return;
  pathologyUploading.value = true;
  let successCount = 0;
  try {
    for (const file of files) {
      if (!isPathologyImageFile(file)) {
        ElMessage.warning(`${file.name} 不是图片文件`);
        continue;
      }
      const previewUrl = URL.createObjectURL(file);
      try {
        const attachment = await uploadAttachment(current.value.id, file);
        pathologySlides.value.push({
          id: attachment.id,
          label: '',
          previewUrl,
          attachment,
        });
        successCount += 1;
      } catch (error: any) {
        URL.revokeObjectURL(previewUrl);
        ElMessage.error({ message: `上传失败：${error?.response?.data?.message ?? error?.message ?? file.name}`, duration: 5000 });
      }
    }
  } finally {
    pathologyUploading.value = false;
  }
  if (successCount > 0) {
    pathologySaved.value = false;
    ElMessage.success(`已上传 ${successCount} 张光镜照片`);
  }
}

function isPathologyImageFile(file: File) {
  return file.type.startsWith('image/') || /\.(png|jpe?g|bmp|gif|webp|tiff?)$/i.test(file.name);
}

function updatePathologyReportDraft() {
  report.findings = [
    `送检材料：${pathology.material || '—'}`,
    `肉眼所见：${pathology.gross || '—'}`,
    pathologySlides.value.length
      ? `光镜照片：已上传 ${pathologySlides.value.length} 张`
      : '光镜照片：未上传',
  ].join('\n');
  report.conclusion = pathology.diagnosis;
}

async function savePathology() {
  if (!current.value) return;
  if (!pathology.diagnosis.trim()) {
    ElMessage.warning('请先填写病理诊断');
    return;
  }
  pathologySaving.value = true;
  try {
    if (!specimenId.value && !isCurrentSamplingDone.value) await prepareSpecimen();
    updatePathologyReportDraft();
    await saveReportDraft(current.value.id, report);
    pathologySaved.value = true;
    ElMessage.success('病理结果已保存并同步至报告');
  } finally {
    pathologySaving.value = false;
  }
}

async function generateAiDraft() {
  if (!current.value) return;
  if (role.value === 'LAB_DOCTOR' && isPathologyOrder.value) {
    if (!pathologySaved.value) {
      ElMessage.warning('请先保存病理结果，再生成 AI 后续建议');
      return;
    }
    await loadExistingReport(current.value.id);
    syncPathologyFormFromReport();
  } else if (role.value === 'LAB_DOCTOR') {
    if (!labResultsSaved.value) {
      ElMessage.warning('请先登记并保存真实检验结果后再生成 AI 建议');
      return;
    }
    const savedResults = await getLabResults(current.value.id);
    if (!savedResults.length) {
      labResultsSaved.value = false;
      labResultRows.value = [];
      ElMessage.warning('后端尚未保存检验结果，请先保存后再生成 AI 建议');
      return;
    }
    labResultRows.value = savedResults.map(mapSavedLabResult);
    updateLabReportDraft();
  }
  const draft = await createAiReportDraft({
    orderId: current.value.id,
    reportType: current.value.orderType,
    itemName: current.value.itemName,
    findings: report.findings,
    conclusion: report.conclusion,
    context: reportAiContext()
  });
  aiModel.value = draft.model;
  aiFallback.value = draft.fallbackUsed;
  report.advice = draft.advice;
  if (role.value === 'LAB_DOCTOR') {
    aiMessages.value = [
      { id: 'a', label: '后续建议', content: draft.advice, kind: 'advice' },
    ].filter(m => m.content);
  } else {
    report.findings = draft.findings;
    report.conclusion = cleanReportConclusion(draft.conclusion);
    aiMessages.value = [
      { id: 'f', label: '检查所见建议', content: draft.findings, kind: 'findings' },
      { id: 'c', label: '结论建议', content: draft.conclusion, kind: 'conclusion' },
      { id: 'a', label: '后续建议', content: draft.advice, kind: 'advice' },
    ].filter(m => m.content);
  }
  await saveReportDraft(current.value.id, report);
  ElMessage.success(role.value === 'LAB_DOCTOR' ? 'AI 后续建议已基于已保存的真实数据生成' : 'AI 报告草稿已生成并保存');
  mainTab.value = 'report';
}

function applyToFindings(content: string) { report.findings = content; mainTab.value = 'report'; }
function applyToConclusion(content: string) { report.conclusion = cleanReportConclusion(content); mainTab.value = 'report'; }
function applyToAdvice(content: string) { report.advice = content; mainTab.value = 'report'; }
function focusAiFinding(item: ImageAiFinding) {
  if (volume.value && item.sliceIndex !== undefined) {
    setSynchronizedSlices('axial', item.sliceIndex);
  }
  focusAiMessage(item.kind);
}
function focusAiMessage(kind: string) {
  const message = aiMessages.value.find(item => item.kind === kind);
  if (!message) return;
  if (kind === 'findings') applyToFindings(message.content);
  else if (kind === 'conclusion') applyToConclusion(message.content);
  else if (kind === 'advice') applyToAdvice(message.content);
}

function errorMessage(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: unknown } }).response;
  const data = response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>;
    for (const key of ['message', 'error', 'detail']) {
      if (typeof record[key] === 'string' && String(record[key]).trim()) return String(record[key]);
    }
    if (Array.isArray(record.detail)) {
      const details = record.detail
        .map((item) => {
          if (!item || typeof item !== 'object') return '';
          const detail = item as Record<string, unknown>;
          const loc = Array.isArray(detail.loc) ? detail.loc.join('.') : '';
          const msg = typeof detail.msg === 'string' ? detail.msg : '';
          return [loc, msg].filter(Boolean).join(': ');
        })
        .filter(Boolean);
      if (details.length) return details.join('；');
    }
  }
  const message = (error as Error)?.message;
  return message || fallback;
}

async function saveDraft() {
  if (!current.value) return;
  if (role.value === 'LAB_DOCTOR' && isPathologyOrder.value) updatePathologyReportDraft();
  else if (role.value === 'LAB_DOCTOR' && labResultRows.value.length) updateLabReportDraft();
  await saveReportDraft(current.value.id, report);
  ElMessage.success('报告草稿已保存');
}

async function confirmAndPublish() {
  if (!current.value) return;
  if (role.value === 'LAB_DOCTOR' && isPathologyOrder.value && !pathologySaved.value) {
    ElMessage.warning('请先保存病理结果');
    return;
  }
  if (role.value === 'LAB_DOCTOR' && !isPathologyOrder.value && !labResultsSaved.value) {
    ElMessage.warning('请先登记并保存检验结果');
    return;
  }
  publishing.value = true;
  try {
    await confirmReport(current.value.id, report);
    confirmedAt.value = today;
    published.value = true;
    ElMessage.success(role.value === 'LAB_DOCTOR' ? '正式报告已审核，检验已结束' : '正式报告已发布');
    await loadOrders();
  } catch (error) {
    ElMessage.error(errorMessage(error, '发布失败，请检查报告内容或服务状态'));
  } finally {
    publishing.value = false;
  }
}

function printReport() {
  mainTab.value = 'report';
  nextTick(() => window.print());
}

function logout() { auth.signOut(); router.push('/login'); }

watch(() => lab.barcode, () => {
  renderLabBarcode().catch(() => {});
});

watch(() => [pathology.material, pathology.gross, pathology.diagnosis], () => {
  if (syncingPathologyForm) return;
  if (isPathologyOrder.value) pathologySaved.value = false;
});

onMounted(async () => {
  await renderLabBarcode();
  await loadOrders();
});

onUnmounted(() => {
  stopAiPolling();
});
</script>

<style scoped>
/* ── Root ── */
.wks {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  font-family: Inter, "Microsoft YaHei", system-ui, sans-serif;
  overflow: hidden;
}

/* ── Navbar ── */
.wks-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
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
.wks-nav__right { display: flex; align-items: center; gap: 20px; font-size: 13px; font-family: inherit; line-height: 1; }
.wks-nav__right > span,
.wks-nav__right :deep(.el-button),
.my-entry { height: 32px; display: inline-flex; align-items: center; font: inherit; line-height: 1; }
.wks-nav__info { opacity: 0.9; }
.wks-nav__date { opacity: 0.8; }

/* ── Body layout ── */
.wks-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  height: calc(100vh - 56px);
  min-height: 0;
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
  flex: 1; min-width: 0; min-height: 0; overflow: hidden;
  padding: 12px 14px;
  display: flex; flex-direction: column;
}
.wks-main--schedule {
  overflow: hidden;
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
  min-height: 0;
}

/* ── main-content viewer override ── */
.main-content--viewer {
  padding: 0;
  background: transparent;
  border-radius: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* ── CT Viewer (CHECK_DOCTOR) ── */
.ct-viewer {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border: 1px solid #dbe3ef;
  border-top: none;
  overflow: hidden;
  min-height: 0;
  min-width: 0;
}

/* Toolbar */
.ct-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 12px;
  background: #ffffff;
  border-bottom: 1px solid #dbe3ef;
  flex-shrink: 0;
  min-width: 0;
  overflow: hidden;
}
.ct-tool {
  display: flex; align-items: center; justify-content: center;
  width: 30px; height: 28px;
  border: none; background: transparent;
  color: #64748b; font-size: 14px;
  border-radius: 4px; cursor: pointer;
  transition: background 0.12s, color 0.12s;
}
.ct-tool:hover { background: #e6f9fa; color: #0899a5; }
.ct-tool--active { background: #0899a5; color: #fff; }
.ct-sep { width: 1px; height: 20px; background: #dbe3ef; margin: 0 4px; flex-shrink: 0; }
.ct-sep--flex { flex: 1; width: auto; background: transparent; }
.ct-toolbar__spacer {
  flex: 1 1 auto;
  min-width: 8px;
}
.ct-ai-badge {
  font-size: 11px; padding: 3px 8px;
  border-radius: 20px; white-space: nowrap;
}
.ct-ai-badge--running { background: #dcfce7; color: #15803d; }
.ct-ai-badge--done { background: #e6f9fa; color: #0899a5; }

.ct-view-tabs {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  background: #f1f5f9;
  border: 1px solid #dbe3ef;
  border-radius: 5px;
  flex-shrink: 0;
  pointer-events: auto;
}
.ct-window-tools {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1 1 auto;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 2px 0;
}
.ct-view-tab {
  height: 24px;
  padding: 0 10px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.ct-view-tab:hover { color: #0899a5; }
.ct-view-tab--active {
  background: #0899a5;
  color: #fff;
}

/* 2×2 panels grid */
.ct-panels {
  flex: 1 1 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: #dbe3ef;
  min-height: 0;
  min-width: 0;
}

.ct-panel {
  position: relative;
  background: #000;
  overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  cursor: crosshair;
  min-width: 0;
  min-height: 0;
}
.ct-panel + .ct-panel { border-left: 1px solid #dbe3ef; }
.ct-panel:nth-child(3),
.ct-panel:nth-child(4) { border-top: 1px solid #dbe3ef; }
.ct-panel:nth-child(3) { border-top: 1px solid #dbe3ef; }
.ct-panel:first-child .ct-panel__canvas {
  max-height: calc(86% - 18px);
}

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
  width: 88%; height: 88%;
  object-fit: contain;
}
.ct-ai-overlay {
  position: absolute;
  inset: 0;
  z-index: 7;
  pointer-events: none;
}
.ct-ai-overlay__head {
  position: absolute;
  left: 12px;
  bottom: 28px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border: 1px solid rgba(34, 211, 238, 0.45);
  border-radius: 4px;
  background: rgba(2, 6, 23, 0.72);
  color: #cffafe;
  font-size: 11px;
  backdrop-filter: blur(4px);
}
.ct-ai-overlay__head strong {
  color: #fff;
  font-weight: 700;
}
.ct-ai-marker {
  position: absolute;
  transform: translate(-50%, -50%);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 188px;
  min-height: 28px;
  padding: 5px 9px;
  border: 1px solid rgba(255, 255, 255, 0.48);
  border-radius: 4px;
  background: rgba(8, 13, 23, 0.82);
  color: #fff;
  font-size: 12px;
  line-height: 1.25;
  text-align: left;
  cursor: pointer;
  pointer-events: auto;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.45), 0 0 24px rgba(34, 211, 238, 0.18);
}
.ct-ai-marker::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 100%;
  width: 1px;
  height: 34px;
  background: currentColor;
  opacity: 0.7;
}
.ct-ai-marker__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 5px rgba(255,255,255,0.08), 0 0 14px currentColor;
  flex-shrink: 0;
}
.ct-ai-marker--finding { color: #67e8f9; }
.ct-ai-marker--risk { color: #fda4af; }
.ct-ai-marker--support { color: #bef264; }

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

.ct-film {
  flex: 1 1 0;
  min-height: 0;
  position: relative;
  overflow: auto;
  overscroll-behavior: contain;
  padding: 8px;
  background: #f8fafc;
}
.ct-film__spacer {
  position: relative;
  min-width: 0;
}
.ct-film__window {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  grid-auto-rows: 118px;
  gap: 6px;
  will-change: transform;
}
.ct-film__cell {
  position: relative;
  min-width: 0;
  min-height: 118px;
  border: 2px solid #dbe3ef;
  background: #000;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.12s, box-shadow 0.12s, transform 0.12s;
}
.ct-film__cell:hover {
  border-color: #0899a5;
  box-shadow: 0 0 0 2px rgba(8, 153, 165, 0.16);
}
.ct-film__cell--selected {
  border-color: #0899a5;
  box-shadow:
    inset 0 0 0 3px #facc15,
    inset 0 0 0 6px rgba(8, 153, 165, 0.92),
    0 0 0 2px rgba(8, 153, 165, 0.22),
    0 10px 24px rgba(8, 153, 165, 0.18);
}
.ct-film__cell--selected::after {
  content: '';
  position: absolute;
  top: 8px;
  left: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #0899a5;
  box-shadow: 0 0 0 3px rgba(250, 204, 21, 0.9);
  z-index: 4;
}
.ct-film__cell img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  image-rendering: auto;
}
.ct-film__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.52);
  font-size: 12px;
  letter-spacing: 0;
  pointer-events: none;
}
.ct-film__tag {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  color: #18c447;
  font-size: 12px;
  font-weight: 700;
  text-shadow: 0 0 2px #000;
  pointer-events: none;
}
.ct-film__tag--posterior {
  left: 50%;
  top: auto;
  bottom: 5px;
  transform: translateX(-50%);
}
.ct-film__idx {
  position: absolute;
  top: 5px;
  right: 7px;
  color: rgba(255,255,255,0.65);
  font-size: 10px;
  pointer-events: none;
}

/* Canvas — use smooth (bilinear) interpolation for medical CT display */
.ct-panel__canvas {
  max-width: 88%;
  max-height: calc(86% - 22px);
  image-rendering: auto;    /* bilinear when scaled, avoids blocky pixel look */
  display: block;
}
/* WebGL 3D canvas fills panel completely */
.ct-panel--3d { cursor: grab; }
.ct-panel--3d:active { cursor: grabbing; }
.ct-panel__canvas--3d {
  width: 92% !important;
  height: 92% !important;
  max-height: 100%;
  image-rendering: auto;
}
.ct-3d-controls {
  position: absolute;
  top: 8px;
  right: 10px;
  z-index: 8;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid rgba(219, 227, 239, 0.9);
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(4px);
}
.ct-3d-preset {
  height: 24px;
  padding: 0 8px;
  border: none;
  border-radius: 3px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
}
.ct-3d-preset:hover { color: #0899a5; background: #e6f9fa; }
.ct-3d-preset--active {
  background: #0899a5;
  color: #fff;
}
.ct-3d-roi {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-left: 4px;
  color: #0899a5;
  font-size: 10px;
  white-space: nowrap;
}
.ct-3d-roi input {
  width: 72px;
  height: 16px;
  margin: 0;
  accent-color: #0e7490;
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
  background: rgba(255, 255, 255, 0.78);
  cursor: pointer;
  z-index: 8;
}
.ct-slider::-webkit-slider-thumb {
  appearance: none;
  width: 14px; height: 14px;
  border-radius: 50%;
  background: #0899a5;
  cursor: pointer;
}
.ct-slider::-webkit-slider-runnable-track {
  height: 3px;
  background: rgba(8, 153, 165, 0.32);
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
  background: #fff; color: #0f172a;
  border: 1px solid #dbe3ef; border-radius: 4px;
  padding: 0 6px; font-size: 12px;
  text-align: center;
}
.ct-wl-inp:focus { outline: none; border-color: #0899a5; }
.ct-sep--sm { width: 1px; height: 20px; background: #dbe3ef; margin: 0 4px; }

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
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 2px;
  height: 46px;
  flex: 0 0 46px;
  padding: 0 12px;
  background: #ffffff;
  border-top: 1px solid #dbe3ef;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior: contain;
}
.ct-act {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 14px;
  border: 1px solid #dbe3ef; background: #f8fafc;
  color: #475569; font-size: 12px;
  border-radius: 5px; cursor: pointer;
  transition: background 0.12s, color 0.12s;
  white-space: nowrap;
  flex-shrink: 0;
}
.ct-act--compact {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}
.ct-act:hover:not(:disabled):not(.ct-act--disabled) { background: #e6f9fa; color: #0899a5; border-color: #a8e8ec; }
.ct-act:disabled,
.ct-act--disabled { opacity: 0.4; cursor: not-allowed; }
.ct-act-gap { flex: 1; }
.ct-act--active {
  background: #0899a5;
  color: #fff;
  border-color: #0899a5;
  box-shadow: 0 0 0 2px rgba(8, 153, 165, 0.14);
}
.ct-act--active:hover:not(:disabled):not(.ct-act--disabled) {
  background: #0cbdcc;
  color: #fff;
  border-color: #0cbdcc;
}
.ct-act--upload {
  background: #e6f9fa;
  color: #0899a5;
  border-color: #a8e8ec;
  font-weight: 700;
}
.ct-act--upload:hover:not(.ct-act--disabled) {
  background: #0899a5;
  color: #fff;
  border-color: #0899a5;
}
.ct-act--primary {
  background: #0899a5; color: #fff; border-color: #0899a5;
}
.ct-act--primary:hover:not(:disabled) { background: #0cbdcc; }
.ct-act--report {
  background: #e6f9fa; color: #0899a5; border-color: #a8e8ec;
}
.ct-act--report:hover { background: #0899a5; color: #fff; }
.ct-act--publish {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}
.ct-act--publish:hover:not(:disabled) {
  background: #16a34a;
  color: #fff;
}

/* ── Lab section (LAB_DOCTOR) ── */
.lab-section { display: flex; flex-direction: column; gap: 10px; }
.lab-block-title { font-size: 14px; font-weight: 600; color: #374151; border-left: 3px solid #0cbdcc; padding-left: 8px; }
.lab-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: flex-start;
  margin-bottom: 8px;
}
.lab-field { display: flex; flex-direction: column; gap: 4px; min-width: 140px; }
.lab-field :deep(.el-select) { width: 100%; }
.lab-field--barcode { min-width: 260px; }
.lab-field label { font-size: 12px; color: #6b7280; }
.lab-qr-card {
  width: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 8px 10px;
  border: 1px solid #dbe3ef;
  border-radius: 6px;
  background: #fff;
}
.lab-barcode-card {
  align-items: stretch;
  justify-content: center;
  margin-left: auto;
  align-self: flex-start;
}
.lab-barcode {
  width: 100%;
  height: 46px;
  display: block;
}
.lab-qr-card img { width: 112px; height: 112px; display: block; }
.lab-qr-card span { font-size: 11px; color: #64748b; overflow-wrap: anywhere; text-align: center; line-height: 1.25; }
.lab-qr-card__empty {
  width: 112px;
  height: 112px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  color: #94a3b8;
  font-weight: 700;
}
.lab-import-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.lab-import-btn {
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  border-radius: 4px;
  background: #0899a5;
  color: #fff;
  font-size: 12px;
  line-height: 28px;
  cursor: pointer;
}
.lab-import-btn:hover { background: #0cbdcc; }
.lab-import-btn--busy {
  opacity: 0.65;
  pointer-events: none;
}
.lab-import-btn input { display: none; }
:deep(.lab-import-bar .el-button--small),
:deep(.pathology-upload-row .el-button--small) {
  height: 28px;
  min-height: 28px;
  padding: 0 12px;
}
.lab-import-empty {
  min-height: 76px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #dbe3ef;
  border-radius: 6px;
  color: #9ca3af;
  font-size: 13px;
  background: #f8fafc;
}

.pathology-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.pathology-form label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 12px;
  color: #4b5563;
}
.pathology-form label:nth-child(n + 2) {
  grid-column: 1 / -1;
}
.pathology-upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.pathology-slide-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.pathology-slide-card {
  border: 1px solid #dbe3ef;
  border-radius: 6px;
  padding: 8px;
  background: #fff;
}
.pathology-slide-card img,
.pathology-slide-card__empty {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border: 1px solid #e5e7eb;
  margin-bottom: 8px;
}
.pathology-slide-card__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  background: #f8fafc;
  font-size: 13px;
}

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
.pathology-report-title {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding-bottom: 10px;
}
.pathology-report-title strong {
  font-size: 18px;
  letter-spacing: 8px;
}
.pathology-report-title span {
  align-self: flex-end;
  font-size: 13px;
  color: #333;
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
.med-report__area--single {
  min-height: 32px;
  height: 32px;
  line-height: 24px;
  resize: none;
  white-space: nowrap;
  overflow: hidden;
}
.med-report__area--emphasis { font-weight: 600; font-size: 14.5px; color: #0899a5; }

.pathology-lines {
  display: grid;
  gap: 6px;
  font-size: 13.5px;
}
.pathology-lines p,
.pathology-report-section p,
.pathology-diagnosis p {
  margin: 4px 0;
  line-height: 1.8;
}
.pathology-report-section {
  min-height: 80px;
  border-bottom: 1px solid #bbb;
  padding: 10px 0;
}
.pathology-report-images {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  padding: 18px 0 8px;
}
.pathology-report-images figure {
  margin: 0;
  text-align: center;
}
.pathology-report-images img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
}
.pathology-report-images figcaption {
  border-top: 1px solid #111;
  font-size: 12px;
  padding-top: 3px;
}
.pathology-report-empty {
  color: #94a3b8;
  min-height: 48px;
  display: flex;
  align-items: center;
}
.pathology-diagnosis {
  border-top: 1px solid #111;
  padding-top: 8px;
  margin-top: 8px;
}

/* Signature footer */
.med-report__sig-footer {
  display: flex; align-items: flex-end; justify-content: space-between;
  padding: 8px 0;
}
.sig-block {
  display: flex;
  flex-direction: row;
  gap: 28px;
  align-items: baseline;
  flex-wrap: wrap;
}
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

.lab-report-table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0 18px;
  font-size: 13.5px;
}
.lab-report-table th {
  border-top: 1px solid #333;
  border-bottom: 1px solid #333;
  padding: 6px 8px;
  text-align: left;
  font-weight: 700;
}
.lab-report-table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 7px 8px;
  line-height: 1.5;
}
.lab-report-table__value { font-weight: 700; }
.lab-report-table__empty {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0 !important;
}

.med-report__actions {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 16px; padding-top: 12px;
  border-top: 1px dashed #e5e7eb;
}

/* ── AI panel ── */
.wks-ai {
  width: clamp(300px, 18vw, 336px); flex-shrink: 0;
  height: 100%;
  min-height: 0;
  display: flex;
  overflow: hidden; padding: 14px;
  border-left: 1px solid #dbe3ef; background: linear-gradient(180deg, #f8fafc 0%, #eef7f8 100%);
  box-sizing: border-box;
}
.ai-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-color: #b9edf0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}
.ai-card :deep(.el-card__header) {
  flex-shrink: 0;
}
.ai-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}
.context-block { margin: 12px 0; padding: 10px 12px; border-radius: 8px; background: #f0f9fa; }
.context-block p { margin: 4px 0 0; font-size: 13px; }
.ai-decision,
.ai-evidence-panel,
.ai-judgement-panel,
.ai-report-build {
  margin-bottom: 12px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
}
.ai-decision {
  padding: 14px;
  border-color: #9de3e7;
  background:
    linear-gradient(135deg, rgba(8, 153, 165, 0.12) 0%, rgba(255,255,255,0) 46%),
    #ffffff;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.85), 0 6px 18px rgba(8, 153, 165, 0.08);
}
.ai-decision__top {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: start;
  gap: 9px;
}
.ai-decision__top strong {
  display: block;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.3;
}
.ai-decision__top em {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.4;
}
.ai-risk-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  background: #94a3b8;
  box-shadow: 0 0 0 4px rgba(148, 163, 184, 0.14);
}
.ai-risk-dot--low {
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.14);
}
.ai-risk-dot--medium {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.16);
}
.ai-risk-dot--high {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.16);
}
.ai-decision__score {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  padding: 9px 10px;
  border-radius: 6px;
  border: 1px solid #a8e8ec;
  background: linear-gradient(135deg, #f0fdfa 0%, #ffffff 100%);
  color: #0f766e;
  min-width: 0;
}
.ai-decision__score strong {
  color: #0899a5;
  font-size: 18px;
  line-height: 1;
  overflow-wrap: anywhere;
  text-align: right;
}
.ai-diagnosis-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}
.ai-theme-button,
:deep(.ai-theme-button.el-button) {
  width: 100%;
  height: 36px;
  min-width: 0;
  padding: 0 10px;
  border-color: #0899a5;
  background: #0899a5;
  color: #fff;
  font-weight: 700;
  white-space: normal;
  line-height: 1.2;
}
:deep(.ai-theme-button.el-button > span) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  text-align: center;
}
.ai-theme-button:hover,
.ai-theme-button:focus,
:deep(.ai-theme-button.el-button:hover),
:deep(.ai-theme-button.el-button:focus) {
  border-color: #0cbdcc;
  background: #0cbdcc;
  color: #fff;
}
.ai-theme-button:disabled,
:deep(.ai-theme-button.el-button.is-disabled) {
  border-color: #9bd7dc;
  background: #9bd7dc;
  color: rgba(255,255,255,0.9);
}
.ai-theme-button--ghost,
:deep(.ai-theme-button--ghost.el-button) {
  min-width: 0;
  border-color: #a8e8ec;
  background: #f0f9fa;
  color: #0899a5;
}
.ai-theme-button--ghost:hover,
.ai-theme-button--ghost:focus,
:deep(.ai-theme-button--ghost.el-button:hover),
:deep(.ai-theme-button--ghost.el-button:focus) {
  border-color: #0899a5;
  background: #e6f9fa;
  color: #067985;
}
.ai-metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}
.ai-metric {
  min-width: 0;
  padding: 9px;
  border: 1px solid #dbe3ef;
  border-radius: 6px;
  background: rgba(248, 250, 252, 0.85);
}
.ai-metric span {
  display: block;
  color: #64748b;
  font-size: 11px;
}
.ai-metric strong {
  display: block;
  margin-top: 3px;
  color: #0f172a;
  font-size: 12px;
  overflow-wrap: anywhere;
}
@media (max-width: 1360px) {
  .ai-diagnosis-actions {
    grid-template-columns: 1fr;
  }
}
.ai-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 11px 12px 8px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}
.ai-section-head strong {
  min-width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: #0899a5;
  color: #fff;
  font-size: 12px;
}
.ai-evidence-empty {
  min-height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 12px 12px;
  padding: 10px;
  border: 1px dashed #b9dfe4;
  border-radius: 6px;
  color: #7890a4;
  background: #fbfeff;
  font-size: 12px;
  text-align: center;
  line-height: 1.45;
}
.ai-judgement-list {
  display: grid;
  gap: 8px;
  padding: 0 12px 12px;
}
.ai-judgement {
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #dbe3ef;
  border-left: 3px solid #0cbdcc;
  border-radius: 5px;
  background: #fbfeff;
  text-align: left;
  cursor: pointer;
}
.ai-judgement:hover {
  border-color: #0cbdcc;
  background: #ecfeff;
}
.ai-judgement span {
  display: block;
  color: #0899a5;
  font-size: 12px;
  font-weight: 700;
}
.ai-judgement p {
  display: -webkit-box;
  margin: 5px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}
.ai-report-build {
  padding: 12px;
  border-color: #a8e8ec;
  background: #f7fcfd;
}
.ai-report-build strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}
.ai-report-build span {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}
.ai-structured {
  margin-bottom: 10px;
  padding: 9px 10px;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #f8fbff;
}
.ai-structured__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 24px;
  font-size: 12px;
  color: #64748b;
}
.ai-structured__row + .ai-structured__row {
  border-top: 1px solid #edf2f7;
}
.ai-structured__row strong {
  max-width: 170px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  text-align: right;
  overflow-wrap: anywhere;
}
.ai-image-bridge {
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
}
.ai-image-bridge__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}
.ai-image-bridge__top strong {
  min-width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: #0f172a;
  color: #fff;
  font-size: 12px;
}
.ai-evidence-list {
  display: grid;
  gap: 7px;
}
.ai-evidence {
  width: 100%;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
}
.ai-evidence:hover {
  border-color: #0899a5;
  background: #f0fdfa;
}
.ai-evidence__pin {
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border-radius: 50%;
  background: #06b6d4;
  box-shadow: 0 0 0 4px rgba(6, 182, 212, 0.12);
}
.ai-evidence__pin--risk {
  background: #f43f5e;
  box-shadow: 0 0 0 4px rgba(244, 63, 94, 0.12);
}
.ai-evidence__pin--support {
  background: #65a30d;
  box-shadow: 0 0 0 4px rgba(101, 163, 13, 0.12);
}
.ai-evidence strong {
  display: block;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.35;
}
.ai-evidence em {
  display: -webkit-box;
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.45;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.ai-image-bridge__empty {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #cbd5e1;
  border-radius: 5px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
  padding: 8px;
}
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
.my-entry {
  height: 32px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.38);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}
.my-entry:hover,
.my-entry--active {
  border-color: #fff;
  background: #fff;
  color: #0899a5;
}
</style>
